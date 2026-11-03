# ADR-019 H2：校验 AI 审核输出的自我纠错循环

## Status

Proposed（2026-11-03）。修订 ADR-017 的 H2 阶段定义；ADR-017 的其余部分不变。评测关卡通过后改为 Accepted。

## Context

ADR-017 把 H2 写成"产出 → `validate_answer`（Schema + 联动 DSL）→ 根据错误修正"，并且让 `validate_answer` 复用 `AnswerPayloadValidator`。实施时发现这条循环**没有东西可以修正**：

- 标注员提交答案时，`SessionService.validateAnswerPayload` 已经用同一个 `AnswerPayloadValidator` 校验过，校验不通过的答案根本提交不了。
- 所以 AI 预审拿到的每一份答案，再用这个校验器检查一遍，结果永远是零错误。

W1 基线（`evals/runs/700607c95f112de6`，1038 次真实调用）显示，**真正出错的是 AI 自己的审核输出**：

| 问题 | 出现次数 | 后果 |
|---|---|---|
| `overallSuggestion` 和分数所在区间矛盾（例如建议 `reject`，分数却落在转人工区） | 333 / 1038（32%） | 路由只看分数，模型给出的建议被静默忽略 |
| `fieldFindings` 引用了不存在的字段（`response-1`、`finding-1`、`length` 等） | 562 / 1038（54%） | 生产环境会写进 `ai_calls_in_field` 和账本，成为无效的溯源记录 |
| 合计：至少有一项错误 | **656 / 1038（63%）** | 用 `evals/labelhub_evals/review_validation.py` 对 W1 录制回放得出，不需要新的模型调用 |

此外，`AiReviewScoringPolicy` 遇到缺失的维度时，会**静默地**用 `confidence` 顶替，也属于同一类问题。

## Decision

### 1. H2 校验对象改为 AI 的审核输出

新增确定性的 `AiReviewOutputValidator`（`services/api`，ai 模块）。输入是一份候选的 `record_ai_review` 参数，加上本次审核的上下文：生效的维度列表、通过和打回阈值、Schema 中全部 stableId（递归包含 children 和 tabs）。输出是错误列表，每条错误包含 `code`、`path`、`message`。

| code | 规则 |
|---|---|
| `invalid_structure` | 缺少必填字段、类型错误、枚举值非法、分数或置信度超出 [0, 1] |
| `missing_dimension` | 配置的维度没有给出分数（对应目前被静默顶替的情况） |
| `duplicate_dimension` | 同一个维度出现了多次 |
| `unknown_dimension` | 给出了没有配置的维度 |
| `unknown_field` | `fieldFindings[i]` 的 `stableId`，或 `fieldPath` 的首段，不在 Schema 的 stableId 集合里 |
| `suggestion_score_conflict` | 按 `AiReviewScoringPolicy` 的同一套算术得出的区间和 `overallSuggestion` 不一致 |
| `error_finding_on_pass` | 建议 `pass`，但存在 `severity = error` 的字段发现 |

规则的**唯一行为源头**是新增的契约语料 `packages/contracts/fixtures/review-output-validation-corpus.json`：Java 实现和 `evals/` 的 Python 镜像都必须让这份语料全部通过，和 `validation-corpus.json` 的做法一样。这样业务权威仍然在 `services/api`，两边的实现也不会走样。

### 2. 内部接口

新增接口 `POST /internal/ai-review/submissions/{submissionId}/validate-review`：

- 请求体是候选输出，返回 `{valid, errors[]}`。
- 只读，不写库。和其他 `/internal/**` 接口一样走 `InternalTokenFilter`。
- 按 ADR-012，先改契约，再实现。

### 3. agent 循环（`services/agent`）

```
step 1: 调用模型（与 H1 相同的 prompt 和强制工具调用）
        → validate-review
        → 无错误：上报结果
        → 有错误，且 step < max_steps：把错误列表作为工具结果反馈给模型，要求重新调用 record_ai_review
        → 有错误，且已达到 max_steps：确定性降级
```

- `max_steps` 默认为 3，即首次输出加两次修正。
- **确定性降级**：`overallSuggestion` 改为 `manual_review`，同时剔除 `unknown_field` 的字段发现，避免写入无效的溯源记录。原始输出和每一步的错误都保留在 `responsePayload.agentSteps` 中。
- 一次审核仍然**只写一条 `ai_calls`**：token 和延迟取各步的总和，幂等键不变（conventions §7.1）。
- H2 不新增数据库表。逐步骤的轨迹表留到 H5，和轨迹查看器一起设计。
- 修正步骤用新的 prompt 版本（`h2-validate-loop-v1`）。第 1 步的 prompt 和 H1 完全相同，所以 H1 的录制可以直接复用，评测只需要为修正步骤新增调用。

### 4. 路由不变

H2 不改变 `AiReviewScoringPolicy` 的算术和阈值。服务端是评分权威，它会根据维度分数重新计算路由，**不采信 agent 给出的 `overallSuggestion`**（现有行为，见 `AiReviewService.internalCommandResult`）。

这意味着：降级只改写 `overallSuggestion` 字段，并剔除无效的字段发现；**降级后的结果仍然按分数路由**，分数落在通过区时仍会自动通过。这和 H1 的行为完全一致，H2 不会让情况变得更糟，但也没有修补这个缺口。修补交给 H3 策略层：读取 `responsePayload.agentLoop.outcome = downgraded`，强制转人工。评测里的路由同样只看最终输出的分数，和生产保持一致。

### 5. 评测关卡

在 W1 的同一个黄金集、同一个模型（`gpt-6.1-terra@low`）上做对比：

- **关卡**：自动通过区错误率不高于基线的 8.93%。
- **同时报告**：校验错误率（第 1 步和最终各一份）、纠错成功率、平均步数、额外 token，以及其他 H1 指标的变化。
- 修正可能把分数推向打回区，而 W1 显示打回区本来就有 53% 的误判。所以自动打回区错误率和转人工比例必须一起报告，不允许只报对 H2 有利的数字。

## Alternatives Considered

- **按 ADR-017 原文，用 `AnswerPayloadValidator` 校验标注答案**：提交时已经校验过，循环永远是空转。不采用。
- **让 AI 提议修正后的答案，再校验这个提议**：这是"AI 辅助改标"，属于新功能，需要新的交互和问责设计，超出了预审的范围。不采用；以后需要时单独写 ADR。
- **跳过 H2，直接做 H3**：W1 数据显示 32% 的输出自相矛盾，H2 有明确、可测量的改进目标。不跳过。
- **只在 agent 侧做校验**：维度配置和 Schema 都属于业务权威，放在 agent 侧就是复制规则，违反 ADR-017 §2。不采用。

## Consequences

- 契约变化：新增一个内部接口和两个 schema（`InternalAiReviewValidationRequest`、`InternalAiReviewValidation`），新增一份契约语料。不改数据库，也不改 append-only 对象清单。
- 成本：有错误的条目会多出 1～2 次模型调用。按基线 63% 的出错率估算，调用量增加 63%～126%。如果实测成本过高，H3 可以把 `unknown_field` 改为确定性剔除（不再回到模型），只把 `suggestion_score_conflict` 交给模型修正。
- 有修正时，`ai_calls` 中这一条的延迟和 token 会明显变大，仪表盘解读时需要知道这一点。
- **outbox 租约从 60 秒调到 150 秒。** 一次审核最多 3 次模型调用（每次超时 30 秒），再加上校验请求，会超过原来 60 秒的租约。租约过期后，别的 worker 会重新领取同一个事件：写入有幂等键保护，但模型调用会重复。新值覆盖了 `application.yml`、`infra/.env.prod.example`、`docker-compose.prod.yml`，以及 `OutboxAiReviewWorker` 的代码默认值。导出 worker 使用独立的 `export-outbox.lease-seconds`（120 秒），不受影响。代价是 AI 预审 worker 崩溃后，事件要多等约 90 秒才会被重新领取。
- **回滚开关**：`LABELHUB_AGENT_REVIEW_LOOP_ENABLED=false` 恢复 H1 的单次调用行为，不需要回滚代码。
- 验证方法：
  - Java 和 Python 两边都跑通共享语料。
  - 用单元测试覆盖 agent 循环的四个分支：一次通过、修正成功、达到上限后降级、校验接口不可用。
  - evals 给出相对 W1 基线的对比报告，结果写进 PR。
