# Webhook Platform Learning Roadmap

## 0. Roadmap 的目标

这个项目不再按“公开课大作业”方式推进，也不按“Codex 直接把功能写完”方式推进。

新的主线是：

- 理解一个生产级 Webhook 平台为什么需要这些组件。
- 通过试错理解可靠性、并发、重试、观测、部署和分布式事务。
- 少写重复 CRUD，多练系统设计、架构判断、测试设计和生产问题定位。
- Codex 负责低价值框架、样板代码、测试骨架、文档整理和 code review。
- 你负责关键业务逻辑、关键测试、设计取舍、debug 过程和 lab note。

每个 lab 都应该回答三个问题：

1. 这个 lab 解决什么真实问题？
2. 如果不这么做，系统会出现什么 bug？
3. 我如何用测试、日志、数据库记录或监控证明它真的工作？

设计 lab 和 roadmap 时先参考现成系统，再决定本项目的取舍。参考源记录在
`docs/reference/webhook-delivery-reference-architecture.md`。如果某个设计不是来自参考系统，
spec 必须明确写出这是教学简化或本项目暂时取舍。

长期路线遵守 `docs/reference/evolutionary-webhook-platform-roadmap.md`：不要先设计最终架构，
而是让项目从 CRUD 一步步演化成可靠投递系统。每一步只解决当前版本暴露出的一个真实问题。

和 Codex 协作时遵守 `docs/codex-collaboration-workflow.md`。这份文档比 lab 顺序更重要：
AI 的角色不是替你设计完整系统，而是暴露下一个问题、缩小下一步、review 你的判断，并在
样板代码上节省时间。

## 1. 学习方式

每个 lab 尽量按这个节奏推进：

1. **Why first**：先讲为什么需要这个组件或设计。
2. **Bad design exercise**：先看一个会坏的设计，预测它怎么坏。
3. **Small test skeleton**：我给测试骨架，你尝试补关键断言。
4. **Implementation slice**：你实现核心逻辑；我只补低价值样板。
5. **Run and fail**：先跑失败测试，根据失败信息修。
6. **Debug drill**：故意制造一个常见生产问题，练定位。
7. **Lab note**：记录你一开始怎么想、后来怎么改、为什么改。

Spec 不应该只是规则清单。它应该引导你自己设计、犯错、验证和修正。

## 2. 当前状态

### 已完成

- **Lab 01：Endpoint Registry**
  - 建立 endpoint 的基本概念。
  - 能保存接收 Webhook 的目标 URL。

- **Lab 02：Reliable Event Ingestion**
  - 明确 Event 和 Delivery 的区别。
  - 理解为什么可靠 Webhook 平台必须先持久化工作，再发送。
  - 实现 Event 入库和 Delivery 计划生成。
  - 理解 `UNIQUE(event_id, endpoint_id)`、外键和索引的作用。
  - 通过隐藏测试和本地测试验证核心行为。

- **Lab 03：Delivery Worker v0**
  - 建立 `DeliveryWorker.processOne()`。
  - 引入 `WebhookSender` 抽象。
  - 跑通单个 Delivery 的发送结果处理。

- **Lab 04：Reliable Delivery v1**
  - 引入 `DeliveryAttempt`。
  - 保存每一次发送证据。
  - 建立基础 retry 和终止失败状态。

- **Lab 05：Backoff and Dead Letter**
  - Retry policy 已经能参考 sender result，而不是只看 attempt number。
  - 404 等永久失败可以第一次失败就进入 `FAILED`。
  - 429、5xx、timeout-like failure 可以走不同 retry/backoff。
  - `FAILED` Delivery 现在是项目里的最小 dead-letter 视图。

### 当前下一步

- **Lab 06：Concurrent Workers and Locking**
  - 复现两个 worker 同时领取同一条 Delivery 的 bug。
  - 用数据库 claim/lease 先实现安全领取。
  - 理解生产系统为什么常用 `SELECT ... FOR UPDATE SKIP LOCKED`。
  - Kafka、Prometheus、React 仍然放到后续 lab。

## 3. Phase 1：核心 Webhook 平台

这一阶段目标是先跑通“保存工作 -> 后台发送 -> 记录结果”的主链路。

### Lab 01：Endpoint Registry

主题：Webhook 接收地址管理。

你需要理解：

- Endpoint 是客户注册的接收地址。
- 为什么平台不能只在内存里保存 endpoint。
- URL、状态、创建时间这些字段为什么存在。

产出：

- Endpoint 表。
- Endpoint 创建 API。
- 基本输入校验。

### Lab 02：Reliable Event Ingestion

主题：可靠接收事件。

你需要理解：

- Event 是业务事件。
- Delivery 是“某个 Event 发给某个 Endpoint”的工作单元。
- 一个 Event 可以产生多个 Deliveries。
- 保存 Event 和 Delivery 必须在同一个事务里完成。
- 先入库再发送，避免进程崩溃后任务丢失。

产出：

- Event 表。
- Delivery 表。
- Event ingestion API。
- Delivery plan。
- Schema 约束和索引。

### Lab 03：Delivery Worker v0

主题：为什么需要后台 worker。

你需要理解：

- API 请求线程不适合等待远程 endpoint。
- 远程 HTTP 调用是不可回滚副作用。
- Worker 是把“保存工作”和“执行工作”分开的执行者。
- v0 worker 只处理一次发送，不处理重试。

产出：

- Worker/service 层最小实现。
- Sender 抽象，测试里可以 fake。
- 一个 `PENDING -> SUCCEEDED/FAILED` 的状态流。
- 单元测试、数据库测试、集成测试骨架。

### Lab 04：Reliable Delivery v1

主题：Attempt、Retry v1。

你需要理解：

- Delivery 表示最终任务，Attempt 表示一次真实 HTTP 请求。
- timeout 不代表对方没有处理。
- 为了避免丢失，系统通常会重试。
- 重试会带来重复投递，因此接收方需要幂等。
- 为什么必须记录每一次 attempt。
- 为什么 retry budget 用完后必须进入终止状态。

产出：

- Attempt 表。
- 每次发送都记录 attempt。
- 简单 retry 规则。
- 失败原因、HTTP status、耗时记录。
- `FAILED` 终止状态。

### Lab 05：Backoff and Dead Letter

主题：什么时候该重试，什么时候该停止。

你需要理解：

- timeout、HTTP 5xx、HTTP 429、HTTP 4xx 的区别。
- 为什么 retry policy 不能只看 attempt number。
- 为什么 404 通常应该直接 dead-letter。
- dead-letter 在当前项目里先是 `FAILED` Delivery 查询，不是单独队列。
- 如何用 Delivery 和 Attempt rows 解释一次失败。

产出：

- 分类后的 retry policy。
- 更明确的 backoff 规则。
- Dead-letter 查询或最小服务。
- 一份基于数据库证据的故障说明。

### Lab 06：Concurrent Workers and Locking

主题：多个 worker 同时运行时如何避免重复领取同一条 Delivery。

你需要理解：

- 单 worker 的吞吐瓶颈。
- 多 worker 会如何重复发送同一条 Delivery。
- claim、锁、事务边界分别解决什么问题。
- `SELECT FOR UPDATE SKIP LOCKED` 为什么常用于任务队列式查询。

产出：

- 重复领取 bug 复现。
- 安全领取 Delivery 的查询。
- 并发数据库测试。
- worker claim 状态设计。

### Lab 06B：Spring Worker Wiring and Minimal Worker Loop

主题：把测试里的 worker 状态机接到真实 Spring 应用运行形态。

你需要理解：

- `@Transactional` 为什么需要 Spring-managed bean 才真正生效。
- `DeliveryClaimer`、`DeliveryWorker`、`DeliveryWorkerFactory`、`DeliveryWorkerRunner` 的边界。
- 为什么先做一次性 runner，而不是直接上无限循环 worker pool。
- worker id 如何进入 `claimed_by`，帮助排查真实运行问题。

产出：

- Spring 管理的 `DeliveryClaimer` 运行路径。
- 能创建带 `workerId` 的真实 worker 的 factory。
- 最小 runner：一次处理最多一条 Delivery。
- 一条小的 wiring/integration 测试。

## 4. Phase 2：真实运行和最小展示

这一阶段目标是把可靠投递链路从“测试里能跑”变成“本地真实可运行、可观察、可展示”。

### Lab 07：Real HTTP Sender and Scheduler

主题：真正发出 Webhook。

你需要理解：

- `WebhookSender` 为什么一开始只是接口。
- HTTP timeout、连接失败、非 2xx response 如何进入现有 retry policy。
- API 接收 Event 后为什么应该快速返回，而不是等待 receiver。
- 定时 worker 和手动 `processOne()` 的区别。

产出：

- 真实 HTTP sender。
- worker 定时执行。
- 本地 receiver demo。
- 一次从 `POST /events` 到 receiver 收到请求的演示。

### Lab 08：Delivery Read APIs

主题：让系统状态可以被查询。

你需要理解：

- 前端和运维页面需要读模型，不应该直接暴露 JPA entity。
- Delivery list/detail、Attempt list、DLQ 查询分别回答什么问题。
- 查询 API 要有稳定排序、状态过滤和错误契约。

产出：

- Delivery list/detail API。
- Delivery attempts API。
- Event -> deliveries API。
- DLQ 查询 API。

### Lab 09：Operator Console MVP

主题：把 delivery 状态展示出来。

你需要理解：

- 前端不是装饰，而是操作系统状态的窗口。
- 页面应该围绕“发生了什么、哪里失败、能否重试”设计。
- 最小可展示版本比完整 UI 框架更重要。

产出：

- Delivery queue 页面。
- Delivery detail 页面。
- Attempt timeline。
- Dead-letter 页面。

## 5. Phase 3：幂等、可观测、限流和安全

这一阶段目标是让系统开始具备生产运维和安全讨论价值。

### Lab 10：Idempotency

主题：重复请求必须可控。

你需要理解：

- 对外 API 的幂等和对 endpoint 的重复投递是两个问题。
- idempotency key 如何避免重复创建 Event。
- 接收方如何用 event id 去重。

产出：

- Event 创建幂等键。
- 幂等测试。
- 重复请求 debug 练习。

### Lab 11：Structured Logging and Metrics

主题：日志和指标是生产证据。

你需要理解：

- 一个事件从 API 到 worker 的日志如何串起来。
- 为什么需要 `eventId`、`deliveryId`、`attemptId`、`endpointId`。
- Counter、Gauge、Histogram 分别适合什么。
- 投递成功率、失败率、延迟、积压量如何定义。

产出：

- 结构化日志。
- request id / correlation id。
- delivery success/failure counters。
- pending delivery gauge。
- delivery duration histogram。

### Lab 12：Incident Drill and Runbook

主题：模拟一次生产事故。

你需要理解：

- 如何从告警进入定位。
- 如何区分 endpoint 故障、平台 bug、数据库慢、worker 停止。
- Runbook 是把排查步骤写成可执行流程。

产出：

- 故障注入脚本或测试场景。
- 一份简单 runbook。
- 一份 postmortem note。

### Lab 13：Timeout Ambiguity

主题：timeout 是分布式系统里最危险的结果之一。

你需要理解：

- timeout 不等于失败，也不等于成功。
- 为什么平台无法知道 endpoint 是否已经处理。
- 为什么 retry 会导致 at-least-once delivery。

产出：

- timeout 场景测试。
- 文档说明平台提供的投递语义。

### Lab 14：Rate Limiting and Circuit Breaker

主题：失败 endpoint 不应该拖垮平台。

你需要理解：

- 限流保护谁。
- 熔断什么时候有意义。
- 全局限制、租户限制、endpoint 限制的区别。

产出：

- 简单 endpoint 级限流。
- 连续失败后的暂停策略。

### Lab 14B：Webhook Security

主题：receiver 如何信任我们的请求，以及 endpoint URL 如何攻击我们。

你需要理解：

- HMAC signature 为什么是发送端必须考虑的问题。
- timestamp 和 replay protection 的作用。
- SSRF 为什么会出现在 webhook sender 项目里。
- URL validation 不能只判断格式合法。

产出：

- endpoint secret。
- outbound signature header。
- timestamp header。
- SSRF 防护设计 note。

## 6. Phase 4：React Admin UI 扩展

这一阶段目标是把 Lab09 的最小操作台扩展成更完整的产品体验。

### Lab 15：React App Skeleton

主题：建立前端工作台。

你需要理解：

- 前端不是装饰，而是操作系统状态的窗口。
- 页面要围绕日常运维动作设计。

产出：

- React 项目结构。
- API client。
- 基础 layout 和路由。

### Lab 16：Endpoint Management UI

主题：管理接收地址。

产出：

- Endpoint 列表。
- 创建、启用、禁用 endpoint。
- 输入校验和错误展示。

### Lab 17：Event Explorer

主题：查询事件。

产出：

- Event 列表。
- Event detail。
- Event 到 Delivery 的关联展示。

### Lab 18：Delivery Dashboard

主题：查看投递状态。

产出：

- Delivery 列表。
- 状态筛选。
- 失败原因展示。
- 按 endpoint/event 查询。

### Lab 19：Retry and Dead Letter UI

主题：人工恢复。

产出：

- 手动 retry。
- dead letter 查看。
- 操作确认和结果反馈。

### Lab 20：Metrics View

主题：把系统健康放到产品里。

产出：

- 简单指标页。
- 成功率、失败率、积压、延迟展示。

## 7. Phase 5：CI/CD、容器和部署

这一阶段目标是让项目接近真实团队交付方式。

### Lab 21：Docker Compose

主题：稳定本地环境。

产出：

- App + PostgreSQL + Prometheus + Grafana 的 compose。
- 一键启动本地环境。

### Lab 22：Jenkins Pipeline

主题：持续集成。

产出：

- Jenkinsfile。
- 编译、测试、打包步骤。
- 失败测试报告。

### Lab 23：Integration Pipeline

主题：带数据库的 CI 测试。

产出：

- CI 中启动 PostgreSQL。
- 运行迁移和集成测试。
- 保存测试结果。

### Lab 24：Environment Config

主题：环境差异不能写死在代码里。

产出：

- dev/test/prod 配置。
- secret 管理策略。
- 配置错误的启动失败测试。

### Lab 25：Deployment Runbook

主题：部署不是只把包丢到服务器。

产出：

- 部署步骤。
- 回滚步骤。
- 健康检查。

## 8. Phase 6：MQ、Kafka 和 Outbox

这一阶段只在你已经感受到数据库轮询和 worker 模型的限制后进入。

### Lab 26：Why MQ

主题：为什么需要消息队列。

你需要理解：

- MQ 解决解耦、削峰和异步消费。
- MQ 不自动解决一致性和幂等。
- Kafka 不是为了显得高级，而是为了解决具体压力。

产出：

- 当前 DB polling 模型的瓶颈分析。
- Kafka 引入前后的架构对比。

### Lab 27：Transactional Outbox

主题：数据库事务和消息发送不能天然原子化。

你需要理解：

- 保存业务数据成功但发消息失败会怎样。
- 发消息成功但数据库回滚会怎样。
- Outbox 如何把远程副作用变成可恢复的本地记录。

产出：

- Outbox 表。
- Outbox publisher。
- 重复发布防护。

### Lab 28：Kafka Producer

主题：把 outbox 事件发布到 Kafka。

产出：

- Kafka topic。
- Producer。
- 发布成功/失败记录。

### Lab 29：Kafka Consumer

主题：从 Kafka 消费 delivery work。

产出：

- Consumer。
- Offset 和处理结果的关系说明。
- 重复消费测试。

### Lab 30：Kafka DLQ

主题：消费失败后的隔离。

产出：

- DLQ topic。
- DLQ 查看和重放流程。

## 9. Phase 7：微服务和分布式事务

这一阶段目标是理解拆服务带来的真实代价，不是为了拆而拆。

### Lab 31：Service Split Design

主题：什么时候拆服务。

你需要理解：

- 单体里事务简单，部署简单。
- 微服务带来独立部署，也带来网络失败和一致性问题。
- 服务边界应该围绕数据所有权和业务能力。

产出：

- 拆分候选：Event service、Delivery service、Endpoint service。
- 服务边界设计文档。

### Lab 32：Service Communication

主题：同步调用和异步消息。

产出：

- 服务间 API 或消息契约。
- 失败场景分析。

### Lab 33：Distributed Transaction Problem

主题：为什么本地事务不够了。

你需要理解：

- 两个服务各有数据库时，一次业务操作无法用一个本地事务包住。
- 2PC、Saga、Outbox 各自解决什么、牺牲什么。

产出：

- 一个跨服务一致性失败实验。
- 方案对比文档。

### Lab 34：Saga and Compensation

主题：用补偿动作处理长事务。

产出：

- Saga 状态表。
- 补偿逻辑。
- 失败恢复测试。

### Lab 35：Distributed Tracing

主题：跨服务 debug。

产出：

- Trace id 贯穿多个服务。
- Jaeger/OpenTelemetry 本地链路追踪。
- 跨服务事故排查练习。

## 10. Phase 8：生产级扩展主题

这些主题不一定全部做成完整 lab，可以作为专项扩展。

- 多租户模型：tenant、endpoint、event、delivery 的归属关系。
- 权限和 API key。
- Webhook 签名和验签。
- Payload schema versioning。
- Endpoint secret rotation。
- 数据归档和 retention。
- 大 payload 存储策略。
- 分库分表和 shard key 选择。
- 批处理和 backfill。
- 成本控制和容量规划。
- SLO、错误预算和告警策略。

## 11. 近期 5 个 Lab 的重点

近期不要跳到 Kafka 或分布式事务。先把可靠 delivery 链路变成可以运行、可以展示、可以解释的
production-like 版本。

1. **Lab 05 收尾：Backoff and Dead Letter Evidence**
   - 补齐 lab note 里的 404、429、5xx、timeout 证据。
   - 用数据库里的 Delivery 和 Attempt 解释为什么 retry 或 dead-letter。

2. **Lab 06：Concurrent Workers and Locking**
   - 先复现重复领取同一条 Delivery 的 bug。
   - 再用 claim/lease 设计切成可验证的小步。
   - 最后能解释 `FOR UPDATE SKIP LOCKED` 解决的是同一个问题。

3. **Lab 07：Real HTTP Sender and Scheduler**
   - 把 `WebhookSender` 从测试 fake 变成真实 HTTP sender。
   - 把 `DeliveryWorker` 接入定时执行。
   - 用本地 receiver 证明 `POST /events` 快速返回，delivery 在后台完成。

4. **Lab 08：Delivery Read APIs**
   - 增加 Delivery list/detail、Attempt list、DLQ 查询 API。
   - 这些 API 为之后的前端操作台服务，不做无意义 CRUD。

5. **Lab 09：Operator Console MVP**
   - 做最小前端页面：queue、delivery detail、attempt timeline、dead-letter。
   - 目标是展示系统状态和失败证据，不是做营销页。

## 12. 分工边界

### Codex 可以直接做

- 文档整理。
- 低价值 validator。
- migration 样板。
- 测试骨架。
- fake/mock sender。
- 构建脚本和本地环境配置。
- 重复性的 DTO、repository、controller glue。
- code review 和失败测试解释。

### 你应该亲自做

- 核心状态流。
- 数据库约束的设计理由。
- 关键测试断言。
- Worker 如何选任务、如何更新状态。
- Retry 策略的第一版设计。
- 并发 bug 的复现和修复思路。
- Lab note 里的反思。

### 需要先讨论再做

- Kafka 引入。
- 微服务拆分。
- 分布式事务方案。
- 分库分表。
- 多租户模型。
- 权限和安全模型。
- React 页面信息架构。

## 13. Anti-goals

这些事情暂时不做：

- 不为了显得高级提前上 Kafka。
- 不在你还没遇到问题前考你 worker、attempt、锁、事务隔离的细节。
- 不把 lab spec 写成一堆规则，让你照着填代码。
- 不让 Codex 直接实现所有核心逻辑。
- 不把重点放在重复 CRUD。
- 不追求一天一个 lab 的速度；可以一天完成小 lab，但理解和 debug 不能省。

## 14. 判断一个 Lab 是否合格

一个 lab 完成时，至少应该留下这些证据：

- 测试能跑，并且失败信息能指向具体模块。
- 数据库里能看到关键状态。
- 你能解释核心表为什么这样设计。
- 你能解释这个设计防住了什么 bug。
- 你能说出这个 lab 没有解决什么问题。
- Lab note 里有一次“我原来以为 X，后来发现 Y”的记录。

这个 roadmap 后续可以继续改，但主方向保持不变：用一个 Webhook 平台，练可靠系统、生产调试、架构判断和全栈交付。
