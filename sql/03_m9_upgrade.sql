-- =====================================================================
-- 趣订场 M9 深度强化 · 数据库升级脚本
-- 执行方式：DataGrip 连接本地 MySQL 后全量执行（幂等，可重复执行）
-- =====================================================================

USE venue_booking;

-- ---------------------------------------------------------------------
-- 本地消息表（Outbox 模式）
-- 解决的问题：支付成功后如果直接调用"发通知"，进程在【事务提交后、调用完成前】
-- 崩溃，这条通知就永久丢了。改为与业务同一事务落库一条消息，由中继任务
-- （Relay）保证最终投递——这就是没有 MQ 时实现"可靠消息最终一致"的标准方案。
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS t_outbox_message
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    biz_type        VARCHAR(32)     NOT NULL                COMMENT '业务类型: PAY_SUCCESS 等',
    biz_key         VARCHAR(64)     NOT NULL                COMMENT '业务键（订单号），配合唯一索引实现发布幂等',
    payload         VARCHAR(1024)   NOT NULL                COMMENT '消息体 JSON',
    status          TINYINT         NOT NULL DEFAULT 0      COMMENT '状态: 0-待投递 1-已成功 2-死信',
    retry_count     INT             NOT NULL DEFAULT 0      COMMENT '已重试次数',
    next_retry_time DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次可投递时间（指数退避）',
    finish_time     DATETIME        NULL     DEFAULT NULL   COMMENT '完成时间',
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_type_key (biz_type, biz_key),
    KEY idx_status_retry (status, next_retry_time)
) ENGINE = InnoDB COMMENT = '本地消息表（Outbox）';
