-- =====================================================================
-- 趣订场 venue-booking · MySQL 8.0 建库建表脚本（第一阶段交付物）
-- 执行方式：DataGrip 连接本地 MySQL(3306) 后，打开本文件全量执行
--
-- 设计约定（务必理解）：
--   1) 不建物理外键：一致性由应用层保证，避免外键锁与级联带来的性能
--      和运维问题，这是互联网公司的主流做法；
--   2) 金额一律 DECIMAL(10,2)：float/double 有精度丢失，绝不能存钱；
--   3) 乐观锁字段 version：并发更新靠 "WHERE version=?"+影响行数判断；
--   4) 逻辑删除 deleted：0-未删 1-已删，配合唯一索引时注意语义；
--   5) create_time/update_time 由数据库自动维护，代码不手动 set。
-- =====================================================================

CREATE DATABASE IF NOT EXISTS venue_booking
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE venue_booking;

-- ---------------------------------------------------------------------
-- 1. 用户表 t_user
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    username    VARCHAR(32)     NOT NULL                COMMENT '登录用户名',
    password    VARCHAR(100)    NOT NULL                COMMENT '密码（BCrypt 散列，禁止明文）',
    nickname    VARCHAR(32)     NOT NULL DEFAULT ''     COMMENT '昵称',
    phone       VARCHAR(16)     NULL     DEFAULT NULL   COMMENT '手机号（可空，唯一索引允许多个 NULL）',
    role        TINYINT         NOT NULL DEFAULT 1      COMMENT '角色: 1-普通用户 2-场馆商家 3-核销员 4-平台管理员',
    status      TINYINT         NOT NULL DEFAULT 1      COMMENT '状态: 1-正常 0-禁用',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0-未删 1-已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone)
) ENGINE = InnoDB COMMENT = '用户表';

-- ---------------------------------------------------------------------
-- 2. 场馆表 t_venue
--    court_count 就是"同一时段可预订份数"，即场次库存的初始值
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_venue;
CREATE TABLE t_venue
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    merchant_user_id BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '所属商家用户ID（0-平台代管）',
    name             VARCHAR(64)     NOT NULL                COMMENT '场馆名称',
    sport_type       TINYINT         NOT NULL                COMMENT '运动类型: 1-羽毛球 2-篮球 3-游泳 4-健身 5-网球',
    city             VARCHAR(32)     NOT NULL DEFAULT ''     COMMENT '城市',
    district         VARCHAR(32)     NOT NULL DEFAULT ''     COMMENT '区县',
    address          VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '详细地址',
    cover_url        VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '封面图URL',
    description      VARCHAR(1024)   NOT NULL DEFAULT ''     COMMENT '场馆介绍',
    open_time        TIME            NOT NULL DEFAULT '08:00:00' COMMENT '每日营业开始时间',
    close_time       TIME            NOT NULL DEFAULT '22:00:00' COMMENT '每日营业结束时间',
    court_count      INT             NOT NULL DEFAULT 1      COMMENT '场地数量（同一时段可订份数=场次库存总数）',
    rating           DECIMAL(2, 1)   NOT NULL DEFAULT 5.0    COMMENT '平均评分（冗余，由评价模块异步维护）',
    rating_count     INT             NOT NULL DEFAULT 0      COMMENT '评价数（冗余）',
    status           TINYINT         NOT NULL DEFAULT 1      COMMENT '状态: 1-上架 0-下架',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0-未删 1-已删',
    PRIMARY KEY (id),
    KEY idx_city_type_status (city, sport_type, status) COMMENT '首页按城市+类型筛选',
    KEY idx_merchant (merchant_user_id)
) ENGINE = InnoDB COMMENT = '场馆表';

-- ---------------------------------------------------------------------
-- 3. 场次模板表 t_slot_template
--    商家配置"每天几点到几点、多少钱"，系统按模板批量生成未来场次
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_slot_template;
CREATE TABLE t_slot_template
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    venue_id    BIGINT UNSIGNED NOT NULL                COMMENT '场馆ID',
    start_time  TIME            NOT NULL                COMMENT '场次开始时间',
    end_time    TIME            NOT NULL                COMMENT '场次结束时间',
    price       DECIMAL(10, 2)  NOT NULL                COMMENT '单价（元/场次），黄金时段可定高价',
    status      TINYINT         NOT NULL DEFAULT 1      COMMENT '状态: 1-启用 0-停用',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0-未删 1-已删',
    PRIMARY KEY (id),
    KEY idx_venue (venue_id)
) ENGINE = InnoDB COMMENT = '场次模板表';

-- ---------------------------------------------------------------------
-- 4. 场次表 t_venue_slot  ★全项目最核心的"库存表"
--    一行 = 某场馆某天某时段的一份可售资源，remain_stock 即剩余库存
--    唯一索引 (venue_id, slot_date, start_time) 从库表层面防止重复生成
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_venue_slot;
CREATE TABLE t_venue_slot
(
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    venue_id     BIGINT UNSIGNED NOT NULL                COMMENT '场馆ID',
    slot_date    DATE            NOT NULL                COMMENT '场次日期',
    start_time   TIME            NOT NULL                COMMENT '开始时间',
    end_time     TIME            NOT NULL                COMMENT '结束时间',
    price        DECIMAL(10, 2)  NOT NULL                COMMENT '单价（元）',
    total_stock  INT             NOT NULL DEFAULT 0      COMMENT '总库存（=场馆场地数）',
    remain_stock INT             NOT NULL DEFAULT 0      COMMENT '剩余库存（扣减 WHERE remain_stock>0 防超订）',
    version      INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    status       TINYINT         NOT NULL DEFAULT 1      COMMENT '状态: 1-可售 0-停售',
    create_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted      TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0-未删 1-已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_venue_date_time (venue_id, slot_date, start_time),
    KEY idx_date_status (slot_date, status)
) ENGINE = InnoDB COMMENT = '场次表（时段库存）';

-- ---------------------------------------------------------------------
-- 5. 预订订单表 t_booking_order  ★核心交易表
--    状态机: 0-待支付 1-已支付待核销 2-已核销(完成) 3-已取消 4-超时关单 5-退款中 6-已退款
--    冗余 venue_name/slot_date/start_time：订单详情免联表，历史数据不受场馆改名影响
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_booking_order;
CREATE TABLE t_booking_order
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    order_no         VARCHAR(32)     NOT NULL                COMMENT '业务订单号（唯一，对外展示）',
    user_id          BIGINT UNSIGNED NOT NULL                COMMENT '下单用户ID',
    venue_id         BIGINT UNSIGNED NOT NULL                COMMENT '场馆ID',
    venue_name       VARCHAR(64)     NOT NULL DEFAULT ''     COMMENT '场馆名称（下单时快照冗余）',
    slot_id          BIGINT UNSIGNED NOT NULL                COMMENT '场次ID',
    slot_date        DATE            NOT NULL                COMMENT '场次日期（冗余）',
    start_time       TIME            NOT NULL                COMMENT '开始时间（冗余）',
    end_time         TIME            NOT NULL                COMMENT '结束时间（冗余）',
    unit_price       DECIMAL(10, 2)  NOT NULL                COMMENT '下单时单价快照',
    total_amount     DECIMAL(10, 2)  NOT NULL DEFAULT 0      COMMENT '订单总额',
    discount_amount  DECIMAL(10, 2)  NOT NULL DEFAULT 0      COMMENT '优惠金额',
    pay_amount       DECIMAL(10, 2)  NOT NULL DEFAULT 0      COMMENT '实付金额',
    coupon_id        BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '使用的优惠券模板ID（0-未用券）',
    user_coupon_id   BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '用户持券ID（0-未用券）',
    status           TINYINT         NOT NULL DEFAULT 0      COMMENT '状态: 0-待支付 1-已支付待核销 2-已核销完成 3-已取消 4-超时关单 5-退款中 6-已退款',
    expire_time      DATETIME        NOT NULL                COMMENT '支付截止时间（超时自动关单）',
    pay_time         DATETIME        NULL     DEFAULT NULL   COMMENT '支付时间',
    checkin_code     VARCHAR(12)     NULL     DEFAULT NULL   COMMENT '核销码（支付成功后生成，唯一）',
    checkin_time     DATETIME        NULL     DEFAULT NULL   COMMENT '核销时间',
    checkin_staff_id BIGINT UNSIGNED NULL     DEFAULT NULL   COMMENT '核销操作员ID',
    cancel_time      DATETIME        NULL     DEFAULT NULL   COMMENT '取消时间',
    cancel_reason    VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '取消/关单原因',
    remark           VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '用户备注',
    version          INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0-未删 1-已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_checkin_code (checkin_code),
    KEY idx_user_status (user_id, status),
    KEY idx_slot (slot_id),
    KEY idx_expire (status, expire_time) COMMENT '超时关单扫描兜底索引'
) ENGINE = InnoDB COMMENT = '预订订单表';

-- ---------------------------------------------------------------------
-- 6. 支付流水表 t_payment_record
--    trade_no 唯一索引 = 支付回调幂等的数据库层兜底
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_payment_record;
CREATE TABLE t_payment_record
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    payment_no    VARCHAR(32)     NOT NULL                COMMENT '支付单号',
    order_no      VARCHAR(32)     NOT NULL                COMMENT '关联业务订单号',
    user_id       BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
    amount        DECIMAL(10, 2)  NOT NULL                COMMENT '支付金额',
    pay_type      TINYINT         NOT NULL DEFAULT 1      COMMENT '支付方式: 1-微信(模拟) 2-支付宝(模拟)',
    status        TINYINT         NOT NULL DEFAULT 0      COMMENT '状态: 0-支付中 1-成功 2-失败',
    trade_no      VARCHAR(64)     NULL     DEFAULT NULL   COMMENT '第三方交易号（模拟；回调幂等键）',
    callback_time DATETIME        NULL     DEFAULT NULL   COMMENT '回调时间',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    UNIQUE KEY uk_trade_no (trade_no),
    KEY idx_order_no (order_no)
) ENGINE = InnoDB COMMENT = '支付流水表';

-- ---------------------------------------------------------------------
-- 7. 退款单表 t_refund_record
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_refund_record;
CREATE TABLE t_refund_record
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    refund_no     VARCHAR(32)     NOT NULL                COMMENT '退款单号（唯一）',
    order_no      VARCHAR(32)     NOT NULL                COMMENT '关联业务订单号',
    user_id       BIGINT UNSIGNED NOT NULL                COMMENT '申请用户ID',
    refund_amount DECIMAL(10, 2)  NOT NULL                COMMENT '退款金额',
    reason        VARCHAR(255)    NOT NULL DEFAULT ''     COMMENT '退款原因',
    status        TINYINT         NOT NULL DEFAULT 0      COMMENT '状态: 0-处理中 1-退款成功 2-已驳回/失败',
    audit_user_id BIGINT UNSIGNED NOT NULL DEFAULT 0      COMMENT '审核人ID（0-系统自动）',
    finish_time   DATETIME        NULL     DEFAULT NULL   COMMENT '退款完成时间',
    create_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_no (refund_no),
    KEY idx_order_no (order_no)
) ENGINE = InnoDB COMMENT = '退款单表';

-- ---------------------------------------------------------------------
-- 8. 优惠券模板表 t_coupon（营销模块：限时抢券=第二个"秒杀"场景）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_coupon;
CREATE TABLE t_coupon
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    title            VARCHAR(64)     NOT NULL                COMMENT '券标题',
    type             TINYINT         NOT NULL DEFAULT 1      COMMENT '类型: 1-满减券 2-折扣券',
    threshold_amount DECIMAL(10, 2)  NOT NULL DEFAULT 0      COMMENT '使用门槛（满 X 元可用，0-无门槛）',
    discount_value   DECIMAL(10, 2)  NOT NULL                COMMENT '满减券=减免金额；折扣券=折扣率(如0.85)',
    total_count      INT             NOT NULL DEFAULT 0      COMMENT '发放总量',
    remain_count     INT             NOT NULL DEFAULT 0      COMMENT '剩余发放量（抢券即扣减它，防超发）',
    per_limit        INT             NOT NULL DEFAULT 1      COMMENT '每人限领张数',
    valid_start      DATETIME        NOT NULL                COMMENT '生效时间',
    valid_end        DATETIME        NOT NULL                COMMENT '失效时间',
    status           TINYINT         NOT NULL DEFAULT 1      COMMENT '状态: 1-上架 0-下架',
    version          INT             NOT NULL DEFAULT 0      COMMENT '乐观锁版本号',
    create_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted          TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0-未删 1-已删',
    PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT = '优惠券模板表';

-- ---------------------------------------------------------------------
-- 9. 用户持券表 t_user_coupon
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_user_coupon;
CREATE TABLE t_user_coupon
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id         BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
    coupon_id       BIGINT UNSIGNED NOT NULL                COMMENT '券模板ID',
    coupon_title    VARCHAR(64)     NOT NULL DEFAULT ''     COMMENT '券标题（冗余快照）',
    type            TINYINT         NOT NULL DEFAULT 1      COMMENT '类型: 1-满减 2-折扣（冗余快照）',
    threshold_amount DECIMAL(10, 2) NOT NULL DEFAULT 0      COMMENT '门槛金额（冗余快照）',
    discount_value  DECIMAL(10, 2)  NOT NULL DEFAULT 0      COMMENT '面额/折扣率（冗余快照）',
    status          TINYINT         NOT NULL DEFAULT 0      COMMENT '状态: 0-未使用 1-已使用 2-已过期',
    receive_time    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
    expire_time     DATETIME        NOT NULL                COMMENT '过期时间',
    used_order_no   VARCHAR(32)     NOT NULL DEFAULT ''     COMMENT '核销本券的订单号',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_status (user_id, status),
    KEY idx_coupon (coupon_id)
) ENGINE = InnoDB COMMENT = '用户持券表';

-- ---------------------------------------------------------------------
-- 10. 评价表 t_review（订单完成后才能评价，一单一评）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_review;
CREATE TABLE t_review
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    order_no    VARCHAR(32)     NOT NULL                COMMENT '关联订单号（一单一评）',
    user_id     BIGINT UNSIGNED NOT NULL                COMMENT '用户ID',
    venue_id    BIGINT UNSIGNED NOT NULL                COMMENT '场馆ID',
    score       TINYINT         NOT NULL DEFAULT 5      COMMENT '评分 1~5',
    content     VARCHAR(500)    NOT NULL DEFAULT ''     COMMENT '评价内容',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted     TINYINT         NOT NULL DEFAULT 0      COMMENT '逻辑删除: 0-未删 1-已删',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_venue (venue_id)
) ENGINE = InnoDB COMMENT = '评价表';

-- ---------------------------------------------------------------------
-- 11. 候补队列表 t_waitlist（满员场次排队，退单后自动通知——延迟队列亮点）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_waitlist;
CREATE TABLE t_waitlist
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    slot_id     BIGINT UNSIGNED NOT NULL                COMMENT '场次ID',
    user_id     BIGINT UNSIGNED NOT NULL                COMMENT '候补用户ID',
    status      TINYINT         NOT NULL DEFAULT 0      COMMENT '状态: 0-排队中 1-已通知 2-已成交 3-已失效',
    notify_time DATETIME        NULL     DEFAULT NULL   COMMENT '通知时间',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_slot_user (slot_id, user_id),
    KEY idx_status (status, slot_id)
) ENGINE = InnoDB COMMENT = '候补队列表';

-- ---------------------------------------------------------------------
-- 12. 通知表 t_notify_message（站内信；异步通知的落库凭证）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS t_notify_message;
CREATE TABLE t_notify_message
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    user_id     BIGINT UNSIGNED NOT NULL                COMMENT '接收用户ID',
    title       VARCHAR(128)    NOT NULL                COMMENT '通知标题',
    content     VARCHAR(512)    NOT NULL DEFAULT ''     COMMENT '通知内容',
    type        TINYINT         NOT NULL DEFAULT 1      COMMENT '类型: 1-订单 2-营销 3-系统',
    is_read     TINYINT         NOT NULL DEFAULT 0      COMMENT '是否已读: 0-未读 1-已读',
    create_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_read (user_id, is_read)
) ENGINE = InnoDB COMMENT = '站内通知表';

-- =====================================================================
-- 建表完成。请继续执行 02_init_data.sql 初始化演示数据
-- =====================================================================
