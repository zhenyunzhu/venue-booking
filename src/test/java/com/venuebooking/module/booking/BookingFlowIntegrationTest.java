package com.venuebooking.module.booking;

import com.venuebooking.common.exception.BizException;
import com.venuebooking.common.result.ResultCode;
import com.venuebooking.module.booking.model.BookingCreateRequest;
import com.venuebooking.module.booking.model.BookingOrderVO;
import com.venuebooking.module.booking.service.BookingOrderService;
import com.venuebooking.module.slot.entity.VenueSlot;
import com.venuebooking.module.slot.mapper.VenueSlotMapper;
import com.venuebooking.module.user.entity.User;
import com.venuebooking.module.user.mapper.UserMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 预订下单链路集成测试（@SpringBootTest 启动完整上下文，依赖本地 MySQL/Redis）。
 *
 * 测试数据自治理：每个用例独立场次（起始时间秒级随机避免唯一键冲突），
 * 结束后物理删除测试数据，不污染演示库。
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("预订下单链路集成测试")
class BookingFlowIntegrationTest {

    @Autowired
    private BookingOrderService bookingOrderService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private VenueSlotMapper slotMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private final AtomicInteger slotSequence = new AtomicInteger(0);
    private final List<Long> createdSlotIds = new ArrayList<>();

    private Long testUserId;

    @BeforeAll
    void setUp() {
        User user = new User();
        user.setUsername("ut" + System.nanoTime() % 100000000L);
        user.setPassword("it");
        user.setNickname("集成测试用户");
        user.setRole(1);
        user.setStatus(1);
        userMapper.insert(user);
        testUserId = user.getId();
    }

    /** 创建独立测试场次：明天的日期 + 递增分钟/随机秒，规避 (venue,date,start) 唯一键冲突 */
    private Long insertSlot(int stock) {
        long seed = System.nanoTime();
        LocalTime start = LocalTime.of(5, slotSequence.getAndIncrement() % 60, (int) (seed % 60));
        VenueSlot slot = new VenueSlot();
        slot.setVenueId(1L);
        slot.setSlotDate(LocalDate.now().plusDays(1));
        slot.setStartTime(start);
        slot.setEndTime(start.plusHours(2));
        slot.setPrice(new BigDecimal("60.00"));
        slot.setTotalStock(stock);
        slot.setRemainStock(stock);
        slot.setStatus(1);
        slotMapper.insert(slot);
        createdSlotIds.add(slot.getId());
        return slot.getId();
    }

    private BookingOrderVO book(Long slotId, String token) {
        BookingCreateRequest request = new BookingCreateRequest();
        request.setSlotId(slotId);
        return bookingOrderService.create(testUserId, request, token);
    }

    @Test
    @DisplayName("正常下单成功，生成雪花单号")
    void shouldCreateOrderSuccessfully() {
        BookingOrderVO vo = book(insertSlot(2), newToken());
        assertNotNull(vo.getOrderNo());
        assertEquals(0, vo.getStatus());
        assertEquals(new BigDecimal("60.00"), vo.getPayAmount());
    }

    @Test
    @DisplayName("下单凭证重放被拦截（幂等）")
    void shouldRejectReplayedToken() {
        Long slotId = insertSlot(2);
        String token = newToken();
        book(slotId, token);
        BizException e = assertThrows(BizException.class, () -> book(slotId, token));
        assertEquals(ResultCode.INVALID_PARAM, e.getResultCode());
    }

    @Test
    @DisplayName("同场次重复下单被拦截")
    void shouldRejectDuplicateBooking() {
        Long slotId = insertSlot(2);
        book(slotId, newToken());
        BizException e = assertThrows(BizException.class, () -> book(slotId, newToken()));
        assertEquals(ResultCode.ORDER_DUPLICATE, e.getResultCode());
    }

    @Test
    @DisplayName("零库存场次被拒绝（Redis 预扣层即拦截）")
    void shouldRejectZeroStockSlot() {
        Long zeroStockSlotId = insertSlot(0);
        BizException e = assertThrows(BizException.class, () -> book(zeroStockSlotId, newToken()));
        assertEquals(ResultCode.SLOT_STOCK_NOT_ENOUGH, e.getResultCode());
    }

    @Test
    @DisplayName("两次下单的雪花单号互不相同")
    void shouldGenerateDifferentOrderNos() {
        BookingOrderVO first = book(insertSlot(2), newToken());
        BookingOrderVO second = book(insertSlot(2), newToken());
        assertNotEquals(first.getOrderNo(), second.getOrderNo());
    }

    private String newToken() {
        return bookingOrderService.createToken(testUserId);
    }

    @AfterAll
    void cleanUp() {
        // 物理删除测试数据（逻辑删除仍占唯一键），并清理 Redis 库存计数器残留
        if (testUserId != null) {
            jdbcTemplate.update("DELETE FROM t_booking_order WHERE user_id = ?", testUserId);
            jdbcTemplate.update("DELETE FROM t_user WHERE id = ?", testUserId);
        }
        for (Long slotId : createdSlotIds) {
            jdbcTemplate.update("DELETE FROM t_venue_slot WHERE id = ?", slotId);
            stringRedisTemplate.delete("stock:slot:" + slotId);
        }
    }
}
