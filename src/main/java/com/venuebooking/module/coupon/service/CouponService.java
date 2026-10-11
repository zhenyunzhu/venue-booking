package com.venuebooking.module.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.venuebooking.common.constant.CacheKeys;
import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.redis.StockRedisHelper;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.module.coupon.entity.Coupon;
import com.venuebooking.module.coupon.entity.UserCoupon;
import com.venuebooking.module.coupon.mapper.CouponMapper;
import com.venuebooking.module.coupon.mapper.UserCouponMapper;
import com.venuebooking.module.coupon.model.CouponVO;
import com.venuebooking.module.coupon.model.UserCouponVO;
import com.venuebooking.common.constant.SportType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 营销抢券服务 —— 第二个秒杀场景（券库存）。
 *
 * 与场次抢订的取舍差异：
 * - 相同：Lua 原子预扣 + DB 条件更新兜底 + 失败回补；
 * - 不同：抢券【不加分布式锁】——券没有"同用户同场次限一单"的强一致诉求，
 *   限领通过 Redis SETNX 标记（per_limit=1）+ 事务内幂等写入实现，
 *   牺牲极端并发下 per_limit>1 的精确性换取吞吐（超领风险由业务容忍度评估）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponMapper couponMapper;
    private final UserCouponMapper userCouponMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final StockRedisHelper stockRedisHelper;
    private final TransactionTemplate transactionTemplate;

    /** 可领券列表 */
    public List<CouponVO> listAvailable() {
        List<Coupon> coupons = couponMapper.selectList(new LambdaQueryWrapper<Coupon>()
                .eq(Coupon::getStatus, 1)
                .orderByDesc(Coupon::getId));
        return coupons.stream().map(this::toVO).collect(Collectors.toList());
    }

    /** 我的券 */
    public List<UserCouponVO> myCoupons(long userId) {
        List<UserCoupon> list = userCouponMapper.selectList(new LambdaQueryWrapper<UserCoupon>()
                .eq(UserCoupon::getUserId, userId)
                .orderByDesc(UserCoupon::getId)
                .last("LIMIT 50"));
        return list.stream().map(this::toUcVO).collect(Collectors.toList());
    }

    /**
     * 限时抢券：SETNX 限领标记（per_limit=1）→ Lua 预扣券库存 → 事务（DB 扣减 + 落持券记录）。
     * 任一步失败：回补 Redis 库存 + 删除限领标记，用户可重试。
     */
    public void grab(long userId, long couponId) {
        Coupon coupon = couponMapper.selectById(couponId);
        if (coupon == null || coupon.getStatus() == 0) {
            throw new BizException(ResultCode.COUPON_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(coupon.getValidStart()) || now.isAfter(coupon.getValidEnd())) {
            throw BizException.of(ResultCode.COUPON_NOT_USABLE, "不在该券的领取时间范围内");
        }

        // 限领标记：SETNX 原子防重领（同用户并发抢只有一次 set 成功）
        String flagKey = CacheKeys.couponGotFlagKey(couponId, userId);
        if (coupon.getPerLimit() != null && coupon.getPerLimit() == 1) {
            Boolean first = stringRedisTemplate.opsForValue().setIfAbsent(flagKey, "1",
                    Duration.between(now, coupon.getValidEnd()));
            if (!Boolean.TRUE.equals(first)) {
                throw BizException.of(ResultCode.COUPON_NOT_USABLE, "该券每人限领1张，您已领取");
            }
        }

        // Lua 预扣券库存（同场次库存模式：挡掉抢完后的全部无效流量）
        String stockKey = CacheKeys.couponStockKey(couponId);
        Long deduct = stockRedisHelper.deduct(stockKey, coupon.getRemainCount());
        if (deduct == null || deduct != 1L) {
            stringRedisTemplate.delete(flagKey);
            throw new BizException(ResultCode.COUPON_NOT_ENOUGH);
        }

        try {
            transactionTemplate.execute(status -> {
                int rows = couponMapper.deduct(couponId);
                if (rows == 0) {
                    throw new BizException(ResultCode.COUPON_NOT_ENOUGH);
                }
                UserCoupon uc = new UserCoupon();
                uc.setUserId(userId);
                uc.setCouponId(coupon.getId());
                uc.setCouponTitle(coupon.getTitle());
                uc.setType(coupon.getType());
                uc.setThresholdAmount(coupon.getThresholdAmount());
                uc.setDiscountValue(coupon.getDiscountValue());
                uc.setStatus(0);
                uc.setReceiveTime(now);
                uc.setExpireTime(coupon.getValidEnd());
                uc.setUsedOrderNo("");
                userCouponMapper.insert(uc);
                return true;
            });
        } catch (RuntimeException e) {
            // 失败回补：券库存 + 限领标记都要还回去
            stockRedisHelper.increment(stockKey);
            stringRedisTemplate.delete(flagKey);
            throw e;
        }
        log.info("[抢券] 用户 {} 抢到券 {} 剩余 {}", userId, couponId,
                coupon.getRemainCount() - 1);
    }

    private CouponVO toVO(Coupon coupon) {
        String desc = coupon.getType() != null && coupon.getType() == 2 ? "折扣券" : "满减券";
        return new CouponVO(coupon.getId(), coupon.getTitle(), coupon.getType(), desc,
                coupon.getThresholdAmount(), coupon.getDiscountValue(), coupon.getRemainCount(),
                coupon.getPerLimit(), coupon.getValidStart(), coupon.getValidEnd());
    }

    private UserCouponVO toUcVO(UserCoupon uc) {
        return new UserCouponVO(uc.getId(), uc.getCouponTitle(), uc.getType(),
                uc.getThresholdAmount(), uc.getDiscountValue(), uc.getStatus(),
                uc.getExpireTime(), uc.getUsedOrderNo());
    }
}
