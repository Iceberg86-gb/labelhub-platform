# ADR-020 H3 策略层：升级规则、阈值校准与受控的自动通过

## Status

Proposed（2026-11-03）。对应 ADR-017 的 H3 阶段。

**本 ADR 显式覆盖以下条款（LH-1）：** 设计基线 §7.5"AI 结论只进入审核证据，不直接跳过人工审核"、§8.1 不变量"未通过人工复审不得导出"，以及 ADR-005 的后果条款"Human review remains the accountable gate"。覆盖范围**只限于**本 ADR §3 定义的 `auto` 模式；`off` 和 `suggest` 两种模式下，上述条款继续有效。

## Context

### 现状：AI 结论实际产生了什么作用

| AI 结论 | 现在的效果 |
|---|---|
| `pass` / `reject` | 只作为证据展示给审核员，**每条提交仍然经过审核员** |
| `manual_review` | 审核员通过后，为这条提交开一个高级仲裁 case（`SeniorReviewCaseService`，信号 `ai_manual_review`） |

系统里**没有自动通过**。ADR-017 所说的"自动通过区错误率"，衡量的是一个还不存在的模式。

### W1 / W2 的数据（HelpSteer2 1038 条，`gpt-6.1-terra@low`）

- 转人工比例 33%，对应生产环境就是**三分之一的提交会进入高级仲裁**，高级审核员的工作量很重。
- 自动通过区错误率 8.93%，把阈值提高到 0.95 也只能降到 7.4%。**目前没有任何阈值组合能让自动通过达到可以接受的质量。**
- H2 标记出的判断不一致（`agentLoop.outcome = flagged`）目前不改变路由，因为 API 只按分数路由。
- "AI 判打回、审核员判通过"这类人机分歧，目前不会进入仲裁。

## Decision

### 1. 升级规则（补上 H2 留下的缺口，同时接入人机分歧）

以下情况为提交开高级仲裁 case，复用 `SeniorReviewCaseService` 现有的"审核员通过后再打开"机制：

| 信号 | 条件 | 新增 / 已有 |
|---|---|---|
| `ai_manual_review` | AI 路由为 `manual_review` | 已有 |
| `ai_loop_flagged` | `responsePayload.agentLoop.outcome` 属于 {`flagged`, `downgraded`, `judgement_changed`, `correction_failed`, `downgraded_no_correction_support`} | **新增** |
| `ai_reviewer_disagreement` | AI 路由为 `reject`，审核员判通过 | **新增** |

信号写入 case 的 `source_signal`；同一条提交、同一个信号最多开一个 case（沿用现有的 case key 幂等）。

### 2. 阈值校准（evals）

- 新增 `evals` 命令 `calibrate`：在固定的录制上扫描 (通过阈值, 打回阈值) 网格，再叠加"flagged 一律转人工"等规则，输出两条曲线：**仲裁比例 vs 漏检率**、**自动通过比例 vs 自动通过区错误率**，并按给定约束（例如"仲裁比例 ≤ 15%"）选出工作点。
- 定义：**漏检率** = 真值为打回、却没有进入仲裁、AI 也没有判打回的条目占全部"应打回"条目的比例。也就是审核员看不到任何 AI 警告的坏样本。
- 选定的阈值通过现有的规则版本化（`ai_review_rules` 新版本）落地，不新开配置通道。

### 3. `auto` 模式：自动通过（覆盖基线，硬门槛）

**权限模式**跟随规则版本（`ai_review_rules.permission_mode`）：

| 模式 | 行为 |
|---|---|
| `off` | 不调用 AI |
| `suggest`（默认） | 现在的行为：AI 结论只是证据，加上 §1 的升级规则 |
| `auto` | AI 路由为 `pass`，并且满足门槛的提交**跳过初审**，直接进入"自动通过"状态 |

**问责：模型不承担问责，开启 auto 的 Owner 承担。**
- 自动通过写入一条新的 ledger 证据 `policy_auto_accept`，`actor_type = system`。payload 记录开启 auto 的 Owner、规则版本、门槛证据的快照、模型和 prompt 版本。
- **AI 仍然不写 `reviewer_overall_verdict`**（ADR-005 的核心不变）。Verdict 派生函数增加一个分支：存在 `policy_auto_accept` 且没有后续人工裁决时，派生为 `accepted_by_policy`，与人工的 `accepted` 区分开。

**硬门槛：证据来自系统自己的影子数据，不接受外部声明。**
- 同一规则版本在 `suggest` 模式下运行时，每条提交都有人工裁决。系统累积 (AI 路由, 人工裁决) 对，计算**影子自动通过错误率** = P(人工打回 | AI 判 pass)。
- 开启 `auto` 的条件（API guard 校验，不满足则拒绝）：
  1. 证据的 (规则版本, prompt 版本, 模型) 必须与当前完全一致，换模型或改 prompt 证据就失效；
  2. AI 判 pass 且已有人工裁决的样本 ≥ `auto_min_samples`（默认 200）；
  3. 影子错误率的 **Wilson 95% 置信上界** ≤ Owner 设定的 `auto_max_error`（必填，没有默认值）。
- 门槛证据在开启的那一刻做快照，写进规则版本和审计日志。

**开启后的持续约束：**
- **强制抽检**：自动通过的提交中，按 `auto_sample_rate`（默认 10%，下限 5%）确定性地抽样，送回初审。抽样按 submission id 的哈希选取，结果可复现。
- **熔断**：抽检样本的错误率（同样用 Wilson 上界）超过 `auto_max_error` 时，规则自动退回 `suggest`，写审计日志并通知 Owner。已经自动通过的提交保持 `accepted_by_policy`，可以整批送回复审。
- 带有 flagged / 降级标记、存在 error 级字段发现，或 AI 调用失败的提交，**一律不能自动通过**。

**导出：**
- 导出时提供"仅人工裁决"选项（与亮点 4 的"纯人工版"一致），可以排除 `accepted_by_policy` 的条目。
- 导出 manifest 记录每条数据的准入来源（`human` / `policy`）。

### 4. 分阶段落地

| 阶段 | 内容 | 关卡 |
|---|---|---|
| W3a | §1 升级规则；§2 `calibrate`；影子错误率的统计和展示（只读） | 用 evals 回放给出曲线，在仲裁比例约束下选定工作点，漏检率不高于现状 |
| W3b | §3 `auto` 模式：权限模式、门槛 guard、`policy_auto_accept`、verdict 派生、抽检、熔断、导出选项、前端开关 | Testcontainers 集成测试覆盖门槛拒绝、开启、抽检、熔断、导出排除的全链路；**实际开启需要生产环境的影子数据达到门槛，不在本 ADR 的交付范围内** |

## Alternatives Considered

- **不引入 auto，只预留**：最安全，但 owner 明确要求引入。保留为"开启门槛永远达不到"时的实际效果。
- **用离线评测结果作为开启证据**：HelpSteer2 和真实任务的分布不同，而且离线结果只能由人声明，系统无法验证。不采用，改用系统内的影子数据。
- **auto 模式让 AI 写 `reviewer_overall_verdict`**：违反 ADR-005 的核心，也会混淆问责主体。不采用，新增独立的 `policy_auto_accept` 证据和 `accepted_by_policy` 派生状态。
- **自动打回**：W1 显示打回区错误率是 53%，即使只打回 ≤ 0.05 分的条目，错误率仍有 31%。**本 ADR 不引入自动打回。**

## Consequences

- 契约变化：
  - 规则 DTO 新增 `permissionMode`、`autoMaxError`、`autoMinSamples`、`autoSampleRate`；
  - 新增影子统计接口、开启和关闭 auto 的命令接口；
  - verdict 枚举新增 `accepted_by_policy`；
  - 导出参数新增准入来源过滤；
  - senior case 的 `source_signal` 新增两个取值。
- 数据库变化：`ai_review_rules` 新增列；ledger 新增证据类型 `policy_auto_accept`（append-only 对象清单不变，属于已有的 ledger 表）。迁移遵循 Flyway。
- 风险：
  - 影子错误率依赖审核员的裁决质量。审核员越宽松，测出来的错误率越低。所以抽检必须由独立的审核员完成，这一点 W3b 需要确认。
  - 高级仲裁量可能先**上升**（§1 新增两个信号），W3a 的校准就是为了把总量压回去。
- 验证：
  - W3a 用 evals 回放（W1 和 W2 的录制，0 次新调用）给出曲线；
  - W3b 用集成测试覆盖门槛、抽检、熔断、导出排除的全部分支。

## W3a 实施记录（2026-11-03）

### 校准改变了 §1 的设计：两个新信号都改为按规则配置，默认关闭

`evals/reports/w3a-calibration.md` 用 W2 的录制（1038 条，0 次新调用）对比了约 4,000 种策略，结论如下：

- **"人机分歧"信号效果很大，但必须设阈值。** 如果所有被 AI 判为打回、审核员却判通过的条目都升级，仲裁比例会从 23% 涨到 44%：这个模型的打回有 53% 是错的。改成只在"AI 分数 ≤ 分歧阈值"时升级之后，效果明显（假设审核员错误率为 10%，按留出集计）：

  | 仲裁预算 | 每千条导出的坏样本 | 相比现状的 21.1 |
  |---|---|---|
  | ≤ 15%（低于现状的 23%） | 14.2 | −33% |
  | ≤ 25%（约等于现状） | 8.5 | −60% |
  | ≤ 35% | 6.1 | −71% |

  ≤ 25% 预算下的最优策略：通过阈值 0.60、打回阈值 0.45、分歧阈值 0.25，且不升级"自相矛盾"。

- **"AI 自相矛盾"信号精度低。** 这 333 条里真值为打回的比例是 22.5%，低于 29% 的平均水平，而单靠它就会产生至少约 24% 的仲裁量。所有预算下的最优策略都把它关掉了。

据此，§1 的两个新信号都改为**按规则配置**（`ai_review_rules.disagreement_threshold`、`escalate_loop_flags`）：

- 默认值都是关闭，现有规则的行为不变。
- 分歧阈值不能大于打回阈值，与校准的搜索空间一致。
- 规则的取值在 AI 预审时快照进 `ai_overall_recommendation` 的 payload，仲裁模块读取快照，不需要跨模块查询规则。

### 影子统计

- 接口 `GET /ai-review/rules/{ruleId}/shadow-stats`，只读，Owner 权限。
- 按 (prompt 版本, 模型) 分组，人工裁决取最新一次（高级仲裁的结论优先）。
- 返回 Wilson 95% 上界。例如 W1 基线 25/280 的自动通过错误，上界是 12.8%。

**范围调整**：影子统计的前端展示，与 W3b 的 auto 开关放在同一处一起做，W3a 只交付接口。

### 选定工作点

校准结果是一组取舍，而不是唯一答案：具体选哪个点，取决于高级审核员的人力和审核员的真实错误率，需要 owner 决定（humanpending #270）。
