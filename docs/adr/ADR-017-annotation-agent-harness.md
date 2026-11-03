# ADR-017 标注 Agent Harness

## Status

Proposed（2026-11）。分阶段落地，每个阶段都要先过评测关卡（见下文"落地顺序"），通过后才改为 Accepted。

| 阶段 | 关卡状态 |
|---|---|
| H1 | ✅ 通过（2026-11-03）：真实模型基线 `evals/runs/700607c95f112de6` 可以逐字节复现，CI 守护。见 [W1 基线报告](../../evals/reports/w1-baseline.md) |
| H2 | ✅ 通过（2026-11-03）：按 [ADR-019](ADR-019-h2-review-output-validation-loop.md) v2 实施，自动通过区错误率与基线持平（8.93%），562 条无效字段引用全部修正，333 条判断不一致被标记。v1 关卡未通过，作为留档保留 |
| H3–H5 | 未开始 |

本 ADR 扩展 ADR-005、006、008、011，不推翻其中任何一条。

## Context

LabelHub 的 AI 预审目前是**一次调用**：agent 从 `/internal/ai-review/submissions/{id}/context` 拉取上下文，以 `tool_choice` 强制调用 `record_ai_review` 一次，然后把结果回写给 API。这条链路已经具备生产级的"环境"：

| 已有能力 | 位置 |
|---|---|
| 可靠的异步执行：租约、幂等、重试、死信、`last_error` | `services/agent` outbox worker（ADR-008） |
| 结构化输出与 provider 解析，不做静默回退 | ADR-006 / ADR-011 |
| 每次调用的来源记录：prompt 版本、模型、input hash、token、成本、延迟 | `ai_calls` |
| 按三区阈值分流：通过 / 转人工 / 打回 | `ai_review_rules`、`AiReviewScoringPolicy` |
| 可执行的规则校验：Schema、联动 DSL | `AnswerPayloadValidator` + `packages/contracts/fixtures` 语料 |
| 人类裁决组成的判例库，append-only | `quality_ledger_entries` |

缺的是**agent 那一半**。现在的模型拿不到任何工具，不能验证自己的输出，看不到人类过去的判例，也没办法在"不确定"的时候主动要求转人工，只能被动地被阈值切分。另外，没有离线评测，所以我们也说不清"换一个 prompt 或换一个模型"到底是变好了还是变差了。

目标是把 LabelHub 演进成一个**标注垂类 Agent Harness**：一个让 LLM agent 在规则、校验和人类问责约束下生产训练数据的运行环境。和编码 agent 的 harness（工具、权限模式、测试、项目规范、操作历史）是同一种结构，只是换到了标注领域。

## Decision

### 1. 不可违反的约束

下面五条约束比本 ADR 的其余部分优先级更高，任何实现都不能绕过：

1. **agent 只产出证据和建议，永远不写最终裁决**（延续 ADR-005）。Harness 的终点是一条 `ai_overall_recommendation`，加上一个路由决定（通过 / 转人工 / 打回）。由 `reviewer_overall_verdict` 表示的最终裁决，仍然只能由人来写。
2. **路由由策略层决定，模型说了不算**。agent 可以自报置信度，也可以主动请求转人工，但"自动通过"必须同时满足：分数达到阈值、所有校验器通过、预算没有超限。任何一条不满足，就降级为转人工。
3. **所有副作用都只追加**。agent 的每一步（工具调用、校验结果、策略决定）都写入轨迹记录，不修改任何已有的事实。
4. **工具全部是只读的，或者只是提议**。唯一带写入语义的工具是 `submit_review`，而它也只是提交一个建议，由策略层裁定。agent 不能直接读写数据库或对象存储。
5. **评测关卡**：每一个改变 agent 行为的变更（prompt、工具、循环逻辑、模型），都必须在固定的黄金集上跑一次评测，并与基线对比，结果写进 PR。

### 2. 组件

```
 内置 agent 循环 ─┐                      ┌─ get_submission / get_schema / get_guidelines
                 ├─→ 策略层 ──→ 工具层 ──┼─ validate_answer（Schema + 联动 DSL）
 外部 agent(MCP) ─┘   路由 · 预算         ├─ search_precedents（人类判例，来自 Ledger）
                     权限 · 迭代上限       └─ submit_review（提议）/ request_human
                         │
                         ▼
          轨迹记录（append-only）→ ai_calls / ledger → 人类裁决 → 可复现导出
```

- **工具层**（`services/agent`）：工具定义由 harness 统一给出，用 OpenAI 兼容的 `tools` 描述。底层实现调用 API 的内部接口，**业务权威仍然只在 `services/api`**。`validate_answer` 复用 API 端已有的 `AnswerPayloadValidator`，通过内部接口调用，不在 agent 侧复制一份规则。
- **agent 循环**：上限 `max_steps` 步。主要模式是"产出 → `validate_answer` → 根据错误修正"。达到步数上限、预算耗尽或者输出格式违规时，**确定性地降级为转人工**，这是 ADR-006 在多步场景下的延伸。
- **策略层**：把三区阈值从"切分一个分数"升级为**权限模式**。`auto`（满足条件时自动通过）、`suggest`（只给建议，一律转人工）、`off`。外加每次调用和每日的 token/成本预算。策略配置跟着 `ai_review_rules` 版本化，不新开一条配置通道。
- **判例记忆**：`search_precedents` 只返回**人类**写下的裁决（`reviewer_overall_verdict`，以及高级审核员的仲裁结果），不返回 AI 自己的历史输出，避免模型自我强化。检索范围限定在同一任务、同一 Schema 版本之内。
- **轨迹记录**：每次运行记录 `run_id`、按步骤的工具调用和返回摘要、校验结果、策略决定和成本。输入输出使用哈希加截断摘要，按 conventions §7.2 先规范化，再走 `AgentSecretRedactor`。前端的溯源 / 轨迹查看器基于这份记录。
- **MCP 接口**（最后一个阶段）：把同一套工具和策略层暴露成 MCP Server，让外部 agent 也能在同样的治理和审计规则下工作。外部 agent 的身份写入轨迹记录和 `ai_calls`，provider 记为 `mcp:<client>`。

### 3. 评测体系（第一阶段交付）

- 黄金集用 **HelpSteer2**（CC-BY-4.0）：抽样 500–1000 条，把人工的分项分数映射成 pass/reject 真值。评测集的版本和抽样种子都要固定。
- 运行时**录制 / 回放**：真实 provider 的响应按 `(model, prompt_version, input_hash)` 缓存。复跑时零成本，结果逐字节可复现，CI 也可以在回放模式下跑小规模的回归评测。
- 指标：一致率、Cohen's κ、**自动通过区错误率**（首要的风险指标）、转人工比例、单条成本、p50/p95 延迟、格式违规率、以及校验纠错成功率。
- 评测结果作为一种 append-only 事实保存（评测运行 + 逐条结果），并关联 `prompt_versions` 和模型。
- 技术栈：评测编排与分析用 **Python**（`evals/`，单独目录），对 API 和 agent 只通过 HTTP 和录制文件交互。生产运行时继续用 Java（`services/agent`），复用现有的密钥管理、日志脱敏和 outbox 的可靠性。

### 4. 落地顺序（每一步都以评测结果作为关卡）

| 阶段 | 内容 | 关卡 |
|---|---|---|
| H1 | 评测框架 + 一次调用的基线 | 基线成绩单可以完全复现 |
| H2 | 工具层 + `validate_answer` 自我纠错循环 | 与基线相比，自动通过区错误率不升高；同时报告格式违规率和纠错成功率 |
| H3 | 策略层：权限模式、预算、阈值校准 | 给出"转人工比例 vs 错误率"曲线，并在约束下选定工作点 |
| H4 | 判例记忆 | 和 H3 相比在评测上有提升，否则不合并 |
| H5 | MCP 接口 + 轨迹查看器 | 外部 agent 能在同样的审计规则下完成一个演示任务 |

## Alternatives Considered

- **继续一次调用，只迭代 prompt**：成本最低，但模型拿不到可执行的校验反馈，也没有判例可以参考，"可治理"只停留在事后切分阈值。这个方案保留为 H1 的基线，不作为终态。
- **用 Python agent 框架（LangGraph 等）重写 worker**：开发更快，但需要重做已经验证过的可靠性（租约、幂等、死信）、密钥解密和日志脱敏，还会出现两套运行时。所以只把评测放在 Python，生产运行时不换。
- **让 agent 直接写最终裁决（或者在高分时跳过人审）**：违反 ADR-005，也让"人类问责"变成一句空话。"自动通过"只是一个路由决定，提交仍然会进入可以被抽检、可以被导出时排除的状态。
- **判例记忆也纳入 AI 的历史输出**：会让模型的偏差自我强化，放大错误，所以不采用。

## Consequences

- 项目的核心卖点，会从"有 AI 预审"变成"有一个经过评测验证的标注 agent 运行环境"，并且每一项行为改动都有数据支撑。
- 新的成本：多步调用会增加 token 消耗，所以需要预算控制和级联路由（便宜的模型先过一遍，不确定的再交给强模型，H3 之后再考虑）；轨迹记录也会增加存储，要做截断并以哈希为主。
- 契约影响：内部接口新增工具端点（`validate`、`precedents`），轨迹记录需要新增一张 append-only 的表。这些都在各自阶段的 PR 里按 ADR-012 先改契约，并同步更新 conventions 里的 append-only 对象清单（LH-2）。
- 验证方法：每个阶段 PR 附带评测对比报告；CI 用回放模式跑小规模回归评测；现有的不变量测试（append-only、幂等、不静默回退）继续作为底线。
