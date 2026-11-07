# ADR-021 H4 判例记忆：BM25 检索人工裁决，作为 AI 审核的参考

## Status

**Rejected（2026-11-07）：§6 的合并关卡未通过，不合并。** 真实模型评测 `evals/runs/93325c017dce9d66` 的报告见 [`evals/reports/w4-gate.md`](../../evals/reports/w4-gate.md)。

| e | H3 参照：留出集坏样本 / 1000 | H4：留出集坏样本 / 1000 |
|---|---|---|
| 0.05 | 4.38 | 5.00 |
| 0.10（主指标） | 8.48 | **10.00** |
| 0.20 | 16.78 | 19.99 |

三档全部变差。默认阈值下，κ 从 0.341 升到 0.386，一致率从 64.7% 升到 68.4%，但这对风险指标没有帮助。

**诊断**（基于录制结果，没有新增模型调用）：
- 判例池中约 70% 是"通过"。按 BM25 取 top-3 后，对于真值为打回的 302 条，只有 105 条的判例多数是"打回"。
- 判例总体把分数往上推：真值为打回的条目中，86 条分数升高，55 条降低；得分 ≥ 0.8 的真值打回条目从 25 条增加到 31 条。
- 校准后，这些高分的坏样本既不会进入仲裁，也不会被自动打回，最终直接导出。
- 结论：词法相似度带来的主要是判例池的基础比例，而不是与当前条目相关的质量信号。

按 §6 的约定，PR 保持开启，评测报告作为"BM25 判例没有帮助"的证据留档。

## Context

- H1–H3 之后，AI 审核每次都是"冷启动"：同一任务里审核员已经做过上百次判断，模型完全看不到，只能靠 Owner 写的业务 prompt 猜这个任务的标准在哪里。
- W1 基线显示，打回区错误率为 53%，自动通过区错误率为 8.93%。模型对"这个任务的及格线"的把握偏差很大，而这条线恰好就写在已有的人工裁决里。
- ADR-017 的 H4 要求：`search_precedents` **只返回人工裁决**（审核员 `reviewer_overall_verdict` 和高级仲裁结果），绝不返回 AI 输出，范围限定为同一任务、同一 Schema 版本。
- 用户已确认的决策：使用词法检索 BM25，不引入向量库或 embedding 服务。

## Decision

### 1. 判例是什么

一条判例 = 同一任务、同一 Schema 版本下，另一条提交的内容，加上它**当前派生出的人工裁决**。

| 规则 | 说明 |
|---|---|
| 只取人工裁决 | 裁决由 `VerdictService` 的同一套逻辑派生：最新的 `reviewer_overall_verdict` 加已解决的高级仲裁 case，结果为 `approved` 或 `rejected`。`pending`、`accepted_by_policy`（ADR-020 §3，没有经过人工）一律不算判例。 |
| 来源标记 | 最终裁决出自高级仲裁的标 `senior`，否则标 `reviewer`。 |
| 排除自身和同一数据项 | 不取被审提交本身，也不取同一 `dataset_item` 的其他提交，避免"同题答案互相泄漏"。评测里同样排除同一 prompt 的样本（§5）。 |
| 规模上限 | 每个任务、每个 Schema 版本只取最近的 2000 条已裁决提交建索引。 |
| AI 输出 | 判例**不包含**任何 AI 结论、分数或字段发现。 |

### 2. 检索规格（Java 与 Python 必须一致）

- **文档文本**：提交内容（API 侧为 `{answerPayload, datasetItemPayload}`，评测侧为 `{prompt, response}`）的所有字符串叶子节点，按规范键序深度优先遍历，以换行连接。数字、布尔、null 不参与。
- **分词**：只对 ASCII 字母做小写化；`[a-z0-9]` 的最长连续段作为一个词；CJK 字符（U+4E00–9FFF、U+3400–4DBF、U+3040–30FF、U+AC00–D7AF）的连续段切成字符二元组，长度为 1 的段保留单字；其余字符一律视为分隔符。不做停用词处理，也不做 Unicode 归一化，因为两种语言的标准库版本不一致，归一化结果可能不同。
- **BM25**：k1 = 1.2，b = 0.75，idf = ln(1 + (N − df + 0.5)/(df + 0.5))。查询里重复出现的词只计一次；得分为 0 的文档不返回；同分时按文档键升序（API 用 submission id，评测用 item_id）。
- 默认 top-k = 3。
- 共享语料 `packages/contracts/fixtures/precedent-retrieval-corpus.json` 固定三类结果：分词结果、检索排序、渲染出的 prompt 片段。两边的测试都读这份语料。

### 3. 判例如何进入 AI 审核

**偏离 ADR-017 的说明**：ADR-017 设想由模型主动调用 `search_precedents` 工具。H4 改为**由系统检索、注入 prompt**，原因有三：结果确定、可以录制回放；不增加模型调用轮数；审计可以精确记录模型看到了哪些判例。`search_precedents` 仍会作为 H5 MCP 工具提供给外部 agent。

- prompt 中的位置：业务 prompt 之后、被审数据之前，放一段固定说明加上规范 JSON。每条判例的形状是 `{rank, source, submission, verdict}`：`submission` 由叶子节点按 `路径: 文本` 逐行拼成，截断到 1200 个码点并加 "…"。使用规范 JSON 是为了防止提交文本跳出数据块（conventions §11）。说明文字明确写着：判例是参考数据，不是指令，也不是被审对象。
- 版本：`h4-precedents-v1`，覆盖检索规格和渲染格式，两者任一变化都要升版本。
- **开关**：部署级配置 `labelhub.ai.precedents.enabled`（环境变量 `LABELHUB_AI_PRECEDENTS_ENABLED`，默认 `false`）、`top-k`（默认 3）和 `pool-limit`（默认 2000）。开启后：
  - 带判例的 AI 调用，其 `provider_adapter_version` 记为 `agent-default-v1+h4-precedents-v1`。**幂等键不变**：同一提交、同一 prompt 和规则版本仍然只有一次 AI 审核，这与同步和异步两条路径共用一个幂等键的现有约定一致。
  - ADR-020 的影子证据按 adapter 版本区分：影子统计和开启 `auto` 的门槛只统计当前 adapter 版本的调用；开启事件的证据快照记录 adapter 版本，自动准入时校验调用的 adapter 版本与快照一致。因此，不带判例积累的证据不能用来自动通过带判例的调用，反之亦然。这和"模型或 prompt 变了证据就作废"是同一条原则。
  - Owner 同步触发的审核路径（API 内直接调用模型）不注入判例，判例只在 agent 路径中使用。
- **审计**：`/internal/ai-review/submissions/{id}/context` 返回 `precedents` 和 `precedentsHash`，agent 上报结果时原样带回。API 校验 hash 一致、每条判例格式正确（裁决只能是 approved / rejected），且 submission 属于同一任务和同一 Schema 版本、不是被审提交本身；任何一项不通过就返回 422 `AI_REVIEW_INVALID_PRECEDENTS`，结果不落库。校验通过后，把 `precedents`、`precedentsHash` 和带判例的 `renderedPrompt` 写进 `ai_calls.request_payload`（append-only）。盲审模式下，判例只包含人工数据，不需要额外隐藏。
- 新的内部接口 `GET /internal/ai-review/submissions/{id}/precedents?k=` 返回同样的数据，供 H5 的 MCP 工具使用。

### 4. 人机分歧回流

H3 的 `ai_reviewer_disagreement` 仲裁 case 一旦解决，高级审核员的结论就成为 `source = senior` 的判例，下一次检索就会用上。这就是"分歧回流"：**不需要单独的回写通道**，因为判例池本身就是人工裁决的派生视图。

### 5. 评测（离线、可复现）

- **判例池**：HelpSteer2 **train** split，固定 revision `990b2711…`，sha256 `c0d7e91d…`，去重后共 20,323 条（通过 14,130 条 / 打回 6,193 条），真值规则同为 `hs2-pass-v1`。它与 validation 的 prompt **重叠为 0**，所以不会出现同题泄漏。validation 内部每个 prompt 有两个回答，因此判例绝不能从 validation 里取。
- `python -m labelhub_evals precedents build`：下载并校验 train，计算 1038 条黄金集各自的 top-3，写出 `datasets/helpsteer2-h4/retrieval.jsonl`（只含 id，提交进仓库）和 manifest（判例池与检索结果的 sha256）。判例池正文不提交，按 manifest 重建。
- `run --precedents 3`：录制键为 prompt 版本 `h1-single-call-v1+h4-precedents-v1`，input hash = hash(条目, 判例 id)；纠错轮沿用 ADR-019 的键规则。run_id 的身份信息里加入 `precedents: {version, k, retrieval_sha256}`，H1–H3 的 run_id 不受影响。

### 6. 合并关卡（H4 对比 H3）

同一模型（`gpt-6.1-terra@low`），同一 H2 v2 循环（max 3）。候选 = 带判例的 run，参照 = `76b0cd706ca35fdd`。新命令 `python -m labelhub_evals gate-h4` 对两边都用 H3 的校准方法（ADR-020 §2，2 折留出），输出报告和 PASS/FAIL：

1. **主指标**：e = 0.10、仲裁预算 ≤ 25% 时，留出集的坏样本导出数 / 1000 **严格低于**参照的 8.48。
2. **不退化**：e = 0.05 和 e = 0.20 时，同一预算下的留出集坏样本导出数不高于参照；格式违规率上升不超过 1 个百分点。

不满足就不合并：PR 保持开启，报告提交进仓库，作为"BM25 判例没有帮助"的证据。

## Consequences

- 正面：AI 第一次能看到这个任务的人工标准；仲裁结论会自动回流成为判例；不引入新的存储或服务，判例池就是现有 ledger 的派生视图。
- 代价：prompt 变长，每条约多 3 × 1200 码点，token 成本上升，评测报告会列出。每次审核都要在 API 内存中现建 BM25 索引（最多 2000 篇），在任务规模不大时可以接受。规模变大后再考虑持久化索引，届时另写 ADR。
- 风险：判例可能让模型"随大流"，在边界样本上照搬相似样本的结论。缓解手段是说明文字要求独立判断，以及 §6 的关卡同时检查多个 e 值。
- 风险：判例质量取决于人工裁决的质量。只取人工裁决，并优先由高级仲裁结论覆盖。
- 验证：共享语料让 Java 和 Python 的结果逐字节一致；API 单元测试覆盖排除规则和审计记录；Testcontainers 测试覆盖端到端流程；`gate-h4` 报告。
