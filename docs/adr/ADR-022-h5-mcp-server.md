# ADR-022 H5 MCP Server：外部 agent 在同一套治理与审计规则下接入

## Status

Proposed（2026-11-07）。对应 ADR-017 的 H5 阶段。关卡：一个外部 MCP 客户端在同样的审计规则下完成一次演示审核（§7）。

## Context

- 到 H4 为止，只有平台自己的 agent（outbox worker）能做 AI 审核。外部 agent（如 Claude Code、Codex 或用户自建的 agent）想参与，只能绕开平台，这样就没有审计，也不受 ADR-019、020 的约束。
- ADR-017 的 H5 要求：外部 agent 通过 MCP 接入；外部 agent 的身份写进轨迹，并以 provider `mcp:<client>` 写进 `ai_calls`；轨迹 append-only，输入和输出存 hash 加截断摘要，并经过规范化和 AgentSecretRedactor 脱敏。
- 用户已确认的决策：
  - MCP Server 放在 services/agent；
  - 鉴权方式为 Owner 按任务签发令牌：只存 hash，可撤销，每次调用都记录令牌和客户端；
  - 轨迹记录和轨迹查看器一起做。

## Decision

### 1. 传输与协议实现

- **端点**：agent 上的 `POST /mcp`，使用 MCP Streamable HTTP 传输（协议版本 `2025-06-18`，兼容 `2025-03-26`），只返回 JSON 响应，不开 SSE 流。会话通过 `initialize` 建立，由 `Mcp-Session-Id` 头标识；一个会话就是一次 run（`run_id`）。`DELETE /mcp` 结束会话。
- **偏离"用 MCP Java SDK 实现服务端"的说明**：MCP Java SDK 2.x 已经移除 WebFlux 服务端传输（`mcp-spring-webflux` 最后的版本是 0.18.x），而 0.18.x 依赖 Spring 6.2。本仓库使用 Spring Boot 3.2，也就是 Spring 6.1；升级 Boot 属于另一项架构变更。因此服务端采用一个最小的协议实现：`initialize`、`notifications/initialized`、`ping`、`tools/list`、`tools/call`，大约三百行代码，没有新增运行时依赖。**互通性由官方 MCP Java SDK 客户端在测试中验证**（仅 test 作用域）。将来 Boot 升级后，可以换成 SDK 的服务端传输，工具层不需要改动。

### 2. 令牌（API 持有，agent 只校验）

- 新表 `mcp_access_tokens`：`task_id`、`owner_id`、`name`、`token_hash`（sha256）、`token_prefix`（明文前 8 位，用于界面识别）、`expires_at`、`created_at`、`revoked_at`、`revoked_by`。令牌格式为 `lhm_` 加 32 字节随机数的 base64url，**明文只在签发时返回一次**。撤销是对当前态的更新，签发和撤销都写进 `audit_logs`。
- Owner 接口：
  - `POST /tasks/{taskId}/mcp-tokens`（签发，可设有效期，默认 30 天，最长 90 天）
  - `GET /tasks/{taskId}/mcp-tokens`
  - `POST /mcp-tokens/{tokenId}/revoke`
- 内部接口 `POST /internal/mcp/authorize {token, submissionId?}`：agent 用客户端的 Bearer 令牌换取授权信息 `{tokenId, taskId, name}`。已撤销、已过期或不存在的令牌一律返回 401；带了 `submissionId` 而该提交不属于令牌对应的任务，返回 404，与"不存在"无法区分。**每次工具调用都会重新授权**，不做缓存，撤销立即生效。
- 令牌的作用范围是**一个任务**，由 API 判定，agent 不自行判断。

### 3. 工具（全部经过 API 的权威逻辑）

| 工具 | 作用 | 后端 |
|---|---|---|
| `list_submissions` | 列出令牌所属任务中还没有 AI 审核的提交（最多 50 条） | 新内部接口 |
| `get_submission` | 被审数据、Schema 字段、维度、阈值和业务 prompt（与内部 agent 拿到的 context 相同） | 现有 context |
| `get_guidelines` | 任务说明和规则的业务 prompt | 同上 |
| `search_precedents` | H4 判例：只有人工裁决 | ADR-021 接口 |
| `validate_review` | ADR-019 校验，返回错误列表和 API 给出的路由 | 现有 validate-review |
| `submit_review` | 提交一份审核**提议**：先在 API 侧重新校验，有错就拒绝并返回错误，由外部 agent 自己修正；校验通过后写入 `ai_calls`，`model_provider = mcp:<client>` | 现有 results 接口 |
| `request_human` | 外部 agent 声明自己无法判断，把提交交还人工处理：记录轨迹步骤，不写 AI 结论 | 轨迹 |

治理不变量：
- **AI 输出仍然只是证据。** `submit_review` 写的是 AI 证据（`ai_calls` 和 AI ledger 条目），和内部 agent 完全一样；不写 `reviewer_overall_verdict`。
- **外部提议永远不能自动通过**（ADR-020）：外部提议的 `responsePayload` 不带 `agentLoop`，所以 `AutoAcceptService` 判定它不符合条件。外部模型身份无法核实，所以影子证据只能来自平台自己的 agent。
- ADR-020 的升级规则照常生效：低分或 `manual_review` 的外部提议，在审核员通过后同样会开仲裁。
- 幂等：同一提交、同一 prompt 和规则版本只能有一份 AI 审核。外部提议如果晚于内部 agent，会得到"已存在"的结果（重放）。API 在追加轨迹时，根据该 `ai_calls` 行的 provider 判定这一步是 `proposal_recorded` 还是 `proposal_replayed`，结果如实写进轨迹。
- `AutoAcceptService` 额外显式拒绝 `model_provider` 以 `mcp:` 开头的调用（纵深防御）。

### 4. 轨迹（append-only）

- 新表 `agent_trajectory_steps`（由 agent 通过 `POST /internal/mcp/trajectory-steps` 追加；`run_id` 是 MCP 会话 id，`step_no` 由 agent 按会话递增，API 用唯一键兜底，并校验该 run 只属于一个令牌）：
  - 身份与关联：`run_id`、`step_no`（每个 run 内从 1 递增，`(run_id, step_no)` 唯一）、`task_id`、`submission_id`（可空）、`token_id`、`client_name`、`client_version`；
  - 调用：`tool_name`、`input_hash`、`input_summary`、`output_hash`、`output_summary`；
  - 结果：`status`（`ok` / `error` / `denied`）、`policy_decision`（例如 `proposal_recorded`、`proposal_rejected_invalid`、`proposal_replayed`、`handoff_to_human`）、`ai_call_id`（可空）、`latency_ms`、`created_at`。
- hash = 规范化 JSON 的 sha256。摘要先经 AgentSecretRedactor 脱敏（令牌明文绝不进入摘要），再截断到 500 个码点。
- Mapper 只提供 insert 和 select，由 contract test 守护。该表加入 conventions §4 的 append-only 清单，并加入任务硬删除的级联清单。`mcp_access_tokens` 同样加入级联清单。
- 查看接口（Owner 用）：
  - `GET /tasks/{taskId}/agent-runs`：按 run 汇总客户端、令牌、步数、最后状态；
  - `GET /agent-runs/{runId}/steps`。
- 前端：任务的"外部 Agent"面板，包括签发和撤销令牌、复制给 Claude Code 的配置片段，以及 run 列表和逐步的轨迹查看器。

### 5. 部署

- agent 配置 `labelhub.agent.mcp.enabled`（默认 `true`）。`/mcp` 只对持有令牌的请求开放；`/actuator` 等其他路径不受影响。
- 生产环境要把 agent 的 `/mcp` 暴露给外部客户端，需要在反向代理上单独开放这一个路径。这属于运维决策，记录到 humanpending，本 ADR 不改 compose 的端口暴露。

### 6. 不做的事

- 不支持跨任务令牌，也不支持外部 agent 写最终裁决或改规则。
- 不开 SSE 推送，不提供 MCP resources 和 prompts，只提供 tools。

### 7. 关卡（H5）

agent 模块中有一个集成测试：用**官方 MCP Java SDK 客户端**连接 `/mcp`，后端是模拟的 API。客户端按以下顺序完成一次演示审核：

1. `initialize`
2. `list_submissions`
3. `get_submission`
4. `search_precedents`
5. 一次故意无效的 `validate_review`
6. 修正后 `submit_review`

断言以下几点：
- API 收到的结果 provider 为 `mcp:<client>`；
- 每一步都追加了轨迹，带 hash，摘要已脱敏；
- 无效提议被拒绝，并且没有落库；
- 撤销令牌之后的下一次调用返回 401。

API 侧另有 Testcontainers 测试覆盖令牌、任务范围、轨迹 append-only 和查看接口。

## Consequences

- 正面：外部 agent 可以在同一套校验、评分、升级和审计规则下参与审核；每次调用都能追溯到令牌、客户端和 run。
- 代价：自己维护一个最小的 MCP 协议实现，要跟进协议版本。互通测试使用官方 SDK 客户端，协议漂移会让测试失败，不会悄悄失效。
- 风险：令牌泄漏。令牌只存 hash、限定一个任务、有有效期，撤销立即生效；所有调用都进入轨迹。
