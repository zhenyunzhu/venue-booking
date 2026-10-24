# 趣订场 · venue-booking

![CI](https://github.com/zhenyunzhu/venue-booking/actions/workflows/ci.yml/badge.svg) ![Java](https://img.shields.io/badge/Java-17-orange) ![Spring%20Boot](https://img.shields.io/badge/Spring_Boot-3.5.16-brightgreen) ![License](https://img.shields.io/badge/License-MIT-lightgrey)

面向线下运动场馆的**在线预订与到店核销平台**：按场次抢订 → 支付获取核销码 → 到店核销；
支持分时段定价、取消退款、限时抢券、候补排队。

> 个人独立设计并开发的高并发交易项目，按生产标准实施（单元与集成测试 / 接口文档 / 指标监控 / 容器化部署）。

## 量化亮点

- **两级库存扣减：Redis Lua 预扣 + MySQL 条件更新兜底**，并发下单下不超卖，两级库存同时归零
- 抢券用 SETNX 限领 + Lua 预扣防超发；支付回调三保险幂等；滑动窗口限流拦截突发流量
- 缓存体系：穿透/击穿/雪崩治理 + **L0 布隆过滤器 + L1 Caffeine + L2 Redis 多级缓存**（广播失效）
- 延迟队列 + 兜底扫描双保险超时关单；对账任务自动修复库存漂移
- **Outbox 本地消息表**：支付事件与业务同事务落库，中继任务可靠投递（指数退避 + 死信）
- traceId 全链路日志 + Prometheus 业务指标（/actuator/prometheus）
- 16 个单元/集成测试（CI 中自动起 MySQL/Redis 容器执行）

## 技术栈

Java 17 ｜ Spring Boot 3.5.16 ｜ MyBatis-Plus 3.5.17 ｜ MySQL 8.0 ｜ Redis 7 (Memurai + Redisson 4.7) ｜ jjwt 0.13 ｜ SpringDoc 2.9 ｜ Maven

## 快速开始

**方式一：本地运行**

1. 初始化数据库：DataGrip 连接本地 MySQL(3306)，依次执行 `sql/01_ddl.sql` → `sql/02_init_data.sql` → `sql/03_m9_upgrade.sql`
2. 准备 Redis：确保 127.0.0.1:6379 可连
3. 启动：IDEA 运行 `VenueBookingApplication`，或 `mvn spring-boot:run`
4. 验收：`GET http://localhost:8080/api/ping` 返回 db:ok + redis:ok

**方式二：Docker 一键起（含建表与演示数据）**

```bash
docker compose up -d      # MySQL + Redis + 应用
# 应用在 http://localhost:8080，首次构建需要几分钟
```

### 测试账号（首次启动自动创建）

| 账号 | 密码 | 角色 |
|---|---|---|
| admin | admin123 | 平台管理员 |
| merchant | merchant123 | 场馆商家 |
| staff | staff123 | 核销员 |
| user | user123 | 普通用户 |

## 接口文档与调试

- **Swagger UI**：启动后访问 `http://localhost:8080/swagger-ui.html`
- 登录 `/api/auth/login` 拿到 token → Swagger 右上角 Authorize 输入 `Bearer {token}` → 即可带登录态调试
- 指标端点：`/actuator/prometheus`（业务指标以 `venue_` 前缀区分）

## 测试

```bash
mvn test    # 16 个用例：雪花并发唯一性 / 退款规则边界 / 优惠封顶 / 下单链路集成测试
```

集成测试（@SpringBootTest）依赖本地 MySQL 与 Redis；CI 中自动以容器形式拉起两者并执行。

## 目录结构

```
venue-booking/
├── sql/                           # 建库脚本（12+1 张表）、演示数据、重置脚本
├── .github/workflows/ci.yml       # CI：自动起 MySQL/Redis 容器跑全量测试
├── Dockerfile / docker-compose.yml
└── src/
    ├── main/java/com/venuebooking/
    │   ├── common/                # result/exception/redis/ratelimit/metrics/web/outbox/mybatis
    │   └── module/                # auth/user/system/venue/slot/booking/payment/refund/coupon/waitlist/notify/outbox
    └── test/java/                 # 单元测试 + @SpringBootTest 集成测试
```

## 模拟实现与扩展点（诚实声明）

| 模拟点 | 当前实现 | 生产扩展 |
|---|---|---|
| 支付 | MockPayService 模拟回调 | 接入微信/支付宝 SDK + 平台验签 + 证书 |
| 回调验签 | md5(orderNo\|secret) | 平台签名/证书 + IP 白名单 |
| 退款 | 原路退回即时成功（状态 1→6） | 1→5 退款中→第三方确认→6 |
| 通知推送 | 站内信落库 + 日志模拟短信 | 短信网关 / WebSocket |
| 限领张数 | per_limit=1 用 Redis SETNX 严格限领；>1 无强校验 | 唯一索引 (user_id, coupon_id, seq) |
| 定时任务 | 单实例 @Scheduled | 分布式调度（XXL-JOB）或分片 |

## 里程碑进度（全部完成 ✅）

- [x] **Phase 1** 选型与架构设计（架构文档 + 12 表 DDL + 演示数据）
- [x] **M1** 脚手架与通用组件（统一响应/全局异常/错误码/MP 三插件）
- [x] **M2** 认证授权（JWT+Redis 双校验、单设备互踢、@RequireRole）
- [x] **M3** 场馆与场次查询（缓存三件套、Cache Aside、幂等排期）
- [x] **M4** 预订下单核心链路（六道闸门，防超订）
- [x] **M5** 支付与异步（回调幂等三保险、延迟队列关单、候补通知）
- [x] **M6** 核销与退款（状态机防重、退款规则、退款单）
- [x] **M7** 营销抢券（SETNX 限领 + Lua 预扣防超发；用券抵扣）
- [x] **M8** 亮点加固（滑动窗口限流、库存对账自愈、EXPLAIN 调优）
- [x] **M9** 深度强化（多级缓存/布隆过滤器/Outbox 可靠消息/可观测性/游标分页）
- [x] **工程化** 单元/集成测试、配置脱敏、SpringDoc、CI、Docker、LICENSE

## 更新日志

- 2026-09-15 工程化发布准备：敏感配置改环境变量占位（脱敏）；新增 16 个单元/集成
  测试（退款/优惠规则抽成纯函数便于穷举边界）；SpringDoc 接口文档；GitHub Actions
  CI（容器化 MySQL/Redis 跑全量测试）；Dockerfile + docker-compose 一键部署；
  LICENSE 与模拟实现声明。
- 2026-09-15 M9 深度强化完成：场馆详情升级为 L0 布隆 + L1 Caffeine + L2 Redis 四级
  读链路（Pub/Sub 广播失效 + 60s TTL 兜底）；本地消息表 Outbox（同事务落库、指数
  退避重试、死信、多实例抢占消费），支付成功通知零丢失；traceId 全链路日志；
  Micrometer 业务指标 + /actuator/prometheus；订单游标分页。
- 2026-09-15 M5~M8 完成：支付回调幂等三保险、延迟队列+扫描双保险关单、
  候补端到端、扫码核销防重、退款规则引擎、抢券防超发、滑动窗口限流
  （修复窗口清理边界 bug）、库存对账自愈、5 组 EXPLAIN 分析。
- 2026-09-15 M1~M4 完成：脚手架/认证/缓存/抢订链路。
- 2026-09-14 Phase 1 完成：选题与架构设计、数据库脚本。
