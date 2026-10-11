package com.venuebooking.module.booking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.venuebooking.common.constant.CacheKeys;
import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.metrics.BusinessMetrics;
import com.venuebooking.common.redis.StockRedisHelper;
import com.venuebooking.common.result.CursorPageResult;
import com.venuebooking.common.result.PageResult;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.common.util.SnowflakeIdGenerator;
import com.venuebooking.module.booking.component.OrderDelayQueue;
import com.venuebooking.module.booking.entity.BookingOrder;
import com.venuebooking.module.booking.mapper.BookingOrderMapper;
import com.venuebooking.module.booking.model.BookingCreateRequest;
import com.venuebooking.module.booking.model.BookingOrderVO;
import com.venuebooking.module.booking.property.BookingProperties;
import com.venuebooking.module.coupon.entity.Coupon;
import com.venuebooking.module.coupon.entity.UserCoupon;
import com.venuebooking.module.coupon.mapper.CouponMapper;
import com.venuebooking.module.coupon.mapper.UserCouponMapper;
import com.venuebooking.module.coupon.service.CouponDiscountCalculator;
import com.venuebooking.module.slot.entity.VenueSlot;
import com.venuebooking.module.slot.mapper.VenueSlotMapper;
import com.venuebooking.module.venue.entity.Venue;
import com.venuebooking.module.venue.mapper.VenueMapper;
import com.venuebooking.module.waitlist.service.WaitlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 预订下单服务 —— 全项目并发设计的核心。
 *
 * 下单六道闸门（由外到内，漏一道就有对应事故剧本）：
 *  1. 幂等凭证      同一次点击的重放只生效一次（HTTP 超时重试/双击）
 *  2. 用户级分布式锁 同一用户并发请求串行化；不同用户互不影响（锁粒度设计）
 *  3. 限订校验      同一用户同一场次只允许一笔有效订单
 *  4. 场次校验      存在性/上架/未开场，价格与时间快照进订单
 *  5. Redis Lua 预扣 原子"查+扣"，把无效流量挡在 DB 之外
 *  6. MySQL 条件更新 兜底防线：WHERE remain_stock>0，影响行数=0 即失败，失败回补 Redis
 *
 * M5 增量：下单成功后注册延迟关单消息；关单/扫描共用幂等的 closeOrderIfUnpaid；
 * 回补两级库存并通知候补队列。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookingOrderService {

    /** Lua：校验并消费幂等凭证（值匹配才删除，一个原子操作）。1=有效 0=无效 */
    private static final DefaultRedisScript<Long> TOKEN_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
            + "return redis.call('DEL', KEYS[1]) else return 0 end", Long.class);

    private final VenueSlotMapper slotMapper;
    private final VenueMapper venueMapper;
    private final BookingOrderMapper orderMapper;
    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final SnowflakeIdGenerator idGenerator;
    private final BookingProperties bookingProperties;
    private final TransactionTemplate transactionTemplate;
    private final StockRedisHelper stockRedisHelper;
    private final OrderDelayQueue orderDelayQueue;
    private final WaitlistService waitlistService;
    private final BusinessMetrics businessMetrics;

    /** 下单凭证：一次性使用，值绑定 userId（被偷了也用不了） */
    public String createToken(long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        stringRedisTemplate.opsForValue().set(CacheKeys.bookingTokenKey(token),
                String.valueOf(userId),
                Duration.ofMinutes(bookingProperties.getTokenTtlMinutes()));
        return token;
    }

    /**
     * 抢订下单。
     * 刻意不加 @Transactional：声明式事务的提交点在方法出口之后（AOP 代理层），
     * 若锁在 finally 先释放、事务后提交，下一个拿到锁的请求读不到上一笔
     * "未提交"订单，限订校验会被击穿 —— 用编程式事务精确控制边界（见 TransactionConfig）。
     */
    public BookingOrderVO create(long userId, BookingCreateRequest request, String token) {
        // ===== 闸门 1：幂等凭证（原子校验+删除，同一凭证的重放只有一次能通过） =====
        if (token == null || token.isBlank()) {
            throw BizException.of(ResultCode.INVALID_PARAM, "缺少下单凭证，请先获取 token");
        }
        Long tokenResult = stringRedisTemplate.execute(TOKEN_SCRIPT,
                List.of(CacheKeys.bookingTokenKey(token)), String.valueOf(userId));
        if (tokenResult == null || tokenResult != 1L) {
            throw BizException.of(ResultCode.INVALID_PARAM, "下单凭证无效或已使用，请刷新后重试");
        }

        // ===== 闸门 2：用户级分布式锁（可重入 + leaseTime 到期自动释放防死锁） =====
        RLock lock = redissonClient.getLock(CacheKeys.bookingUserLockKey(userId));
        boolean locked;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.of(ResultCode.SYSTEM_ERROR, "请求被中断，请重试");
        }
        if (!locked) {
            throw BizException.of(ResultCode.RATE_LIMITED, "操作过于频繁，请稍后重试");
        }
        try {
            BookingOrderVO created = transactionTemplate.execute(status -> doBook(userId, request));
            // 事务已提交才注册延迟关单消息（到点投递给 OrderCloseConsumer）
            orderDelayQueue.offer(created.getOrderNo(),
                    Duration.ofMinutes(bookingProperties.getPayExpireMinutes()));
            businessMetrics.incBookingSuccess();
            return created;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 在事务模板内执行的真正下单逻辑 */
    private BookingOrderVO doBook(long userId, BookingCreateRequest request) {
        Long slotId = request.getSlotId();
        // ===== 闸门 3：同用户同场次限一单（0 待支付 / 1 已支付都算占用） =====
        Long active = orderMapper.selectCount(new LambdaQueryWrapper<BookingOrder>()
                .eq(BookingOrder::getUserId, userId)
                .eq(BookingOrder::getSlotId, slotId)
                .in(BookingOrder::getStatus, List.of(0, 1)));
        if (active != null && active > 0) {
            throw new BizException(ResultCode.ORDER_DUPLICATE);
        }

        // ===== 闸门 4：场次/场馆校验，快照进订单 =====
        VenueSlot slot = slotMapper.selectById(slotId);
        if (slot == null || slot.getStatus() == 0) {
            throw new BizException(ResultCode.SLOT_NOT_FOUND);
        }
        if (LocalDateTime.of(slot.getSlotDate(), slot.getStartTime()).isBefore(LocalDateTime.now())) {
            throw BizException.of(ResultCode.SLOT_NOT_FOUND, "不能预订已开场的场次");
        }
        Venue venue = venueMapper.selectById(slot.getVenueId());
        if (venue == null || venue.getStatus() == 0) {
            throw new BizException(ResultCode.VENUE_NOT_FOUND);
        }

        // ===== 用券校验（放在扣库存前：券不合法就不要消耗库存） =====
        CouponPlan plan = resolveCoupon(request.getUserCouponId(), userId, slot.getPrice());

        // ===== 闸门 5：Redis Lua 原子预扣 =====
        String stockKey = CacheKeys.stockSlotKey(slotId);
        Long deduct = stockRedisHelper.deduct(stockKey, slot.getRemainStock());
        if (deduct == null || deduct != 1L) {
            businessMetrics.incBookingStockReject();
            throw new BizException(ResultCode.SLOT_STOCK_NOT_ENOUGH);
        }

        // ===== 闸门 6：MySQL 条件更新兜底；失败必须回补 Redis =====
        try {
            int rows = slotMapper.deductStock(slotId);
            if (rows == 0) {
                businessMetrics.incBookingStockReject();
                throw new BizException(ResultCode.SLOT_STOCK_NOT_ENOUGH);
            }
            BookingOrder order = buildOrder(userId, slot, venue, plan);
            orderMapper.insert(order);
            if (plan.userCouponId() > 0) {
                // 券占用：条件更新 0→1 与订单同事务；并发用同一张券只有一个成功，失败整体回滚
                int used = userCouponMapper.useCoupon(plan.userCouponId(), userId, order.getOrderNo());
                if (used == 0) {
                    throw BizException.of(ResultCode.COUPON_NOT_USABLE, "优惠券状态已变化，请重试");
                }
            }
            return toVO(order);
        } catch (RuntimeException e) {
            // 事务将回滚，Redis 预扣必须还回去 —— 否则库存凭空少一份（漂移的经典来源）
            stockRedisHelper.increment(stockKey);
            throw e;
        }
    }

    /** 用券计划：折扣金额与券 ID（无券时全 0） */
    private record CouponPlan(BigDecimal discount, long couponId, long userCouponId) {
    }

    /**
     * 用券校验与优惠计算。校验链：持券存在 → 归属本人 → 未使用 → 未过期 → 达到门槛。
     * 校验（读）在前，占用（条件更新 0→1）在订单同一事务内——并发重复用券只有一个成功。
     */
    private CouponPlan resolveCoupon(Long userCouponId, long userId, BigDecimal orderAmount) {
        if (userCouponId == null || userCouponId <= 0) {
            return new CouponPlan(BigDecimal.ZERO, 0L, 0L);
        }
        UserCoupon uc = userCouponMapper.selectById(userCouponId);
        if (uc == null || !Objects.equals(uc.getUserId(), userId)
                || uc.getStatus() == null || uc.getStatus() != 0
                || uc.getExpireTime().isBefore(LocalDateTime.now())) {
            throw BizException.of(ResultCode.COUPON_NOT_USABLE, "优惠券不可用或已过期");
        }
        Coupon coupon = couponMapper.selectById(uc.getCouponId());
        if (coupon == null) {
            throw new BizException(ResultCode.COUPON_NOT_FOUND);
        }
        if (orderAmount.compareTo(coupon.getThresholdAmount()) < 0) {
            throw BizException.of(ResultCode.COUPON_NOT_USABLE, "订单金额未达到优惠券使用门槛");
        }
        BigDecimal discount = CouponDiscountCalculator.calculate(coupon.getType(), coupon.getDiscountValue(), orderAmount);
        return new CouponPlan(discount, coupon.getId(), uc.getId());
    }

    /**
     * 超时关单（延迟队列消费与兜底扫描共用，幂等）：
     * 状态机条件更新 0→4，影响行数=0 说明已被处理（已支付/已关）直接返回；
     * 成功则回补两级库存并通知候补队列第一名。
     */
    public void closeOrderIfUnpaid(String orderNo) {
        BookingOrder order = orderMapper.selectOne(new LambdaQueryWrapper<BookingOrder>()
                .eq(BookingOrder::getOrderNo, orderNo)
                .select(BookingOrder::getId, BookingOrder::getSlotId));
        if (order == null) {
            return;
        }
        int rows = orderMapper.closeIfUnpaid(orderNo);
        if (rows == 0) {
            log.info("[关单] 订单 {} 已被处理（已支付/已关），跳过", orderNo);
            return;
        }
        restoreStockForSlot(order.getSlotId());
        waitlistService.notifyEarliest(order.getSlotId());
        log.info("[关单] 订单 {} 超时关闭，库存已回补", orderNo);
    }

    /** 回补两级库存：DB 条件回补（LEAST 防越界）+ Redis 计数器回加 */
    public void restoreStockForSlot(long slotId) {
        slotMapper.restoreStock(slotId);
        stockRedisHelper.increment(CacheKeys.stockSlotKey(slotId));
    }

    /**
     * 兜底扫描：每分钟清理"延迟消息丢失/应用重启期间漏关"的过期待支付订单。
     * 与延迟队列构成双保险：队列保证时效性（准点关），扫描保证最终一致性（绝不漏关）。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void closeExpiredOrders() {
        List<BookingOrder> expired = orderMapper.selectList(new LambdaQueryWrapper<BookingOrder>()
                .select(BookingOrder::getOrderNo)
                .eq(BookingOrder::getStatus, 0)
                .lt(BookingOrder::getExpireTime, LocalDateTime.now())
                .last("LIMIT 200"));
        for (BookingOrder order : expired) {
            closeOrderIfUnpaid(order.getOrderNo());
        }
    }

    private BookingOrder buildOrder(long userId, VenueSlot slot, Venue venue, CouponPlan plan) {
        BookingOrder order = new BookingOrder();
        order.setOrderNo(idGenerator.nextOrderNo());
        order.setUserId(userId);
        order.setVenueId(venue.getId());
        order.setVenueName(venue.getName());
        order.setSlotId(slot.getId());
        order.setSlotDate(slot.getSlotDate());
        order.setStartTime(slot.getStartTime());
        order.setEndTime(slot.getEndTime());
        order.setUnitPrice(slot.getPrice());
        order.setTotalAmount(slot.getPrice());
        order.setDiscountAmount(plan.discount());
        order.setPayAmount(slot.getPrice().subtract(plan.discount()));
        order.setCouponId(plan.couponId());
        order.setUserCouponId(plan.userCouponId());
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(bookingProperties.getPayExpireMinutes()));
        order.setRemark("");
        return order;
    }

    /** 我的订单（分页） */
    public PageResult<BookingOrderVO> myOrders(long userId, long pageNum, long pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 50);
        Page<BookingOrder> page = orderMapper.selectPage(new Page<>(Math.max(pageNum, 1), pageSize),
                new LambdaQueryWrapper<BookingOrder>()
                        .eq(BookingOrder::getUserId, userId)
                        .orderByDesc(BookingOrder::getId));
        return PageResult.of(page, this::toVO);
    }

    /**
     * 我的订单-游标分页（深分页优化）：
     * WHERE id < lastId ORDER BY id DESC LIMIT n，B+ 树直接定位，性能与页深无关，
     * 且新订单插入不会导致页码漂移。lastId 为空查第一页。
     */
    public CursorPageResult<BookingOrderVO> myOrdersByCursor(long userId, Long lastId, long pageSize) {
        pageSize = Math.min(Math.max(pageSize, 1), 50);
        List<BookingOrder> list = orderMapper.selectList(new LambdaQueryWrapper<BookingOrder>()
                .eq(BookingOrder::getUserId, userId)
                .lt(lastId != null, BookingOrder::getId, lastId)
                .orderByDesc(BookingOrder::getId)
                .last("LIMIT " + pageSize));
        Long nextCursor = list.isEmpty() ? null : list.get(list.size() - 1).getId();
        return new CursorPageResult<>(list.stream().map(this::toVO).toList(), nextCursor, list.size() == pageSize);
    }

    private BookingOrderVO toVO(BookingOrder order) {
        BookingOrderVO vo = new BookingOrderVO();
        vo.setOrderNo(order.getOrderNo());
        vo.setVenueId(order.getVenueId());
        vo.setVenueName(order.getVenueName());
        vo.setSlotDate(order.getSlotDate());
        vo.setStartTime(order.getStartTime());
        vo.setEndTime(order.getEndTime());
        vo.setUnitPrice(order.getUnitPrice());
        vo.setPayAmount(order.getPayAmount());
        vo.setDiscountAmount(order.getDiscountAmount());
        vo.setStatus(order.getStatus());
        vo.setCheckinCode(order.getCheckinCode());
        vo.setExpireTime(order.getExpireTime());
        vo.setCreateTime(order.getCreateTime());
        return vo;
    }
}
