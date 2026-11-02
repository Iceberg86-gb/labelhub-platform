# M3 启动总览

> M2 完成后的 M3 阶段计划。本文件只提供总览和元问题，不包含任何 M3 代码或实施指令。

## M3 战略口径

M2 已完成浏览器级标注闭环和亮点 1 evidence。M3 应聚焦 AI 监督信号的最小可信闭环，而不是同时推进 Reviewer、Export、训练污染防控全链路。

建议口径：

- C-time：优先控制阶段体量，避免一次性引入 provider、队列、Reviewer、export 四条战线。
- B-content：先做可答辩的 AI evidence fact，不追求完整产品化 AI 审核工作台。
- B'-parallel：AI fact 写入与 UI 消费可以拆 phase，但 provider 调用、provenance 写入、成本/失败处理必须先统一契约。

## M3 初步范围

### 必达

- AI 监督信号最小集成。
- `ai_calls` 写入：provider、model、prompt version、input hash、output hash、cost、latency、status。
- `ai_calls_in_field` 写入：submission、field path、ordinal、AI call 关联。
- Owner 或 Reviewer 能触发一次 AI 检查，并看到结构化结果。
- Labeler/Submission 页面至少能展示 AI provenance 的存在和来源边界。

### 候选

- Reviewer 初版审核队列。
- 字段级 AI 建议在 Submission 详情中展示。
- AI 调用重试与幂等防重复。
- 基于 M2 `SchemaRenderer` 的只读 AI evidence overlay。

### 不做

- Trusted Export 完整实现，留 M5。
- 训练污染防控完整链路，留 M5。
- 大规模异步任务平台，除非同步 AI 调用无法满足 M3 smoke。
- 多 provider 动态切换 UI，除非元问题裁决要求。

## M3 设计风险预判

- AI provider 契约：OpenAI / Anthropic / 其他 provider 的 request、response、timeout、错误码和成本字段不一致。
- 幂等性：V1 已有 `idempotency_key`，但需要定义触发侧如何生成、失败后是否复用。
- Provenance 写入时机：AI call 与 field association 是否同事务，失败时是否保留 failed call fact。
- 成本与 latency：M3 至少要记录，不一定要做配额计费。
- Prompt version：M3 需要 prompt version 固化，否则 AI evidence 无法复现。
- 数据边界：Labeler answer、schema、dataset item payload 进入 AI provider 前需要明确最小输入和脱敏策略。
- UI 口径：AI 是监督信号，不是最终 verdict；M3 UI 不能暗示 AI 自动裁决。

## M3 元问题清单(动笔前必答)

1. 复述 M3 范围：哪些属于 AI 监督信号最小闭环，哪些延后到 M4/M5?
2. M3 使用哪个 AI provider? 直接接一个 provider，还是从第一天抽象多 provider?
3. M3 AI 调用同步还是异步? 如果同步，最大 timeout 是多少? 如果异步，是否需要轮询端点?
4. `ai_calls.idempotency_key` 由前端、后端 service、还是调用请求体生成?
5. AI 输入包含哪些内容：schema fields、answer payload、dataset item payload、task metadata、historical submission?
6. AI 输出的最小结构是什么：field findings、confidence、severity、suggestion、raw text?
7. `ai_calls` failed / timeout 是否也写入事实表? UI 如何展示失败?
8. `ai_calls_in_field.ordinal` 如何递增? 同一 field 多次调用是否全部保留?
9. Owner、Reviewer、Labeler 谁可以触发 AI? 谁可以看到 AI provenance?
10. Prompt 模板放在哪里：`services/agent`、DB、还是 `services/api` resources?
11. AI cost / latency / model metadata 在 M3 smoke 中如何验证?

## M3 拆分预期

- M3-P0：总览元问题裁决，明确 provider、同步/异步、输入输出契约、权限边界。
- M3-P1：OpenAPI 0.6.0，新增 AI review/provenance 最小端点和 DTO。
- M3-P2：AI service/provider adapter，写入 `ai_calls`，覆盖失败/timeout/幂等测试。
- M3-P3：field-level provenance 写入 `ai_calls_in_field`，关联 submission 和 schema field path。
- M3-P4：Owner/Reviewer UI 触发 AI 检查并展示结构化结果。
- M3-P5：Labeler/Submission 历史页面展示 AI provenance 边界，不暗示 AI verdict。
- M3-P6：M3 smoke、截图、decision-log、humanpending 更新。

## 启动时机

M2-P8 完成并审过 README、验收清单、截图索引和 pending 分类后，再进入 M3-P0。M3-P0 应重复 M2-P0 的工作流：总览、元问题、裁决、再拆实施 phase。
