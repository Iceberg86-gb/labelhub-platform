# 让外部 agent 通过 MCP 参与审核（ADR-022）

外部 agent（Claude Code、Codex 或你自己写的 agent）通过 MCP 接入某个任务后，能读取待审数据和审核标准，并**提交审核提议**。

提议会经过和平台 agent 完全相同的校验与评分，记录为 AI 证据，供人工审核员参考：它不是裁决，也永远不会自动通过。每一次工具调用都会写进只追加的轨迹。

## 1. 签发令牌

在 Owner 的任务详情页，打开「外部 Agent（MCP）」面板：填写名称和有效期（1–90 天），然后点「签发令牌」。

- 令牌**只显示一次**，数据库里只存 SHA-256。
- 令牌只对这个任务有效，越权访问会被拒绝，并记录到轨迹。
- 撤销后，下一次调用立即被拒绝。

## 2. 接入 Claude Code

```bash
claude mcp add --transport http labelhub http://localhost:8081/mcp --header "Authorization: Bearer <令牌>"
```

`http://localhost:8081/mcp` 是本地 agent 的地址。生产环境需要在反向代理上单独开放 `/mcp`（见 humanpending #275），前端可以用 `VITE_MCP_URL` 配置显示的地址。

## 3. 工具

| 工具 | 说明 |
|---|---|
| `list_submissions` | 本任务中还没有 AI 审核的提交 |
| `get_submission` / `get_guidelines` | 被审数据、维度、阈值和业务审核标准 |
| `validate_review` | 校验一份候选审核，只读，不落库 |
| `submit_review` | 提交审核提议。校验不通过就拒绝，并返回需要修改的地方；通过后记录为 AI 证据（`ai_calls.model_provider = mcp:<客户端名>`） |
| `request_human` | 交还人工处理，不写任何 AI 结论 |

一个典型的提示词：「用 labelhub 的工具审核本任务里还没审的提交：先看 guidelines，再 validate_review，通过后 submit_review；拿不准就 request_human。」

## 4. 查看轨迹

在同一个面板的「运行与轨迹」里，每个 MCP 会话就是一次运行。点开可以看到逐步的工具调用：

- 输入和输出的 hash，以及脱敏、截断后的摘要；
- 每一步的结果：成功、失败或拒绝；
- 策略决定：提议已记录、提议被拒、重放、交还人工；
- 关联的 AI 调用。
