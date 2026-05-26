# M3 验收清单(亮点 4 AI Provenance)

> 本清单用于 M3 验收和答辩演示复现。M3-P6 已完成 OpenAI-compatible provider 抽象、配置切换和单元测试；真实 provider smoke 因本机暂无可用 API key/额度，按 R8 记录为待跑项。

## 准备

- API 默认使用 mock provider：`LABELHUB_AI_PROVIDER` 未设置时等价于 `mock`。
- 切换到真实 OpenAI-compatible provider 需要通过环境变量注入：
  - `LABELHUB_AI_PROVIDER=openai-compatible`
  - `AI_BASE_URL`
  - `AI_API_KEY`
  - `AI_MODEL_NAME`
  - `AI_PROVIDER_NAME`
- `application.yml` 不提交具体 provider URL 或 model name，避免代码库绑定特定厂商。

## M3 已完成验证

| 验证项 | 操作 | Evidence |
|--------|------|----------|
| Owner 触发 AI 检查(mock) | Owner 进入 `/owner/tasks/{taskId}/submissions/{submissionId}` 后点击 `AI 检查` | `docs/screenshots/phase-m3p4-ai-drawer-first.png` |
| Idempotency 复用(mock) | Owner 使用同 prompt 再次点击 `AI 检查` | `docs/screenshots/phase-m3p4-ai-drawer-idempotency-hit.png` |
| Owner provenance 展示 | Owner Submission 详情页显示 AI 调用 metadata | `docs/screenshots/phase-m3p5-owner-shared-provenance.png` |
| Labeler provenance 透明展示 | Labeler Submission 详情页显示同一 AI metadata | `docs/screenshots/phase-m3p5-labeler-ai-provenance.png` |
| Provider 切换代码层验证 | `OpenAiCompatibleProviderTest` 用 JDK fake server 验证 wire-level request/response | `mvn -pl services/api -Dtest=OpenAiCompatibleProviderTest,AiProviderConditionalTest test` |
| 配置切换验证 | `AiProviderConditionalTest` 验证默认 mock、显式 openai-compatible、缺 key fail-fast | 同上 |

## 真实 Provider Smoke(待 API key/额度)

| 必达项 | 操作 | 期望 |
|--------|------|------|
| 1. Owner 触发真实 AI 检查 | 设置真实 `AI_*` env，重启 API，Owner 点击 `AI 检查` | Drawer 显示真实 provider 返回的字段级反馈 |
| 2. `ai_calls` 真实写入 | 查询 `ai_calls` / `ai_calls_in_field` | `status=completed`，`model_provider` 等于 env 中 provider name，latency 为真实毫秒 |
| 3. Submission 详情显示 provenance | Owner 和 Labeler 分别打开 submission 详情 | 两端显示同一 provider/model/cost/latency/hash metadata |
| 4. Idempotency 复用 0 成本 | Owner 用同 prompt 再次触发 | Drawer 显示 `复用历史结果`，DB 不新增同 key `ai_calls` 行，provider 不再被调用 |

待生成截图命名建议：

- `phase-m3p6-real-provider-drawer-first.png`
- `phase-m3p6-real-provider-ai-calls-db.png`
- `phase-m3p6-real-provider-labeler-card.png`
- `phase-m3p6-real-provider-idempotency-hit.png`

## Provider 切换演示

1. 默认 mock 模式启动 API，跑通 Owner AI 检查。
2. 停止 API，设置 `LABELHUB_AI_PROVIDER=openai-compatible` 及 `AI_*` env。
3. 重启 API，同一 submission 使用同 prompt 触发 AI 检查。
4. 因 provider/model 进入 idempotency key，不同 provider 会产生新的 `ai_calls` 行。
5. 业务代码、OpenAPI、前端均无需修改。

## 答辩演示路径

- 短演示(90 秒)：Owner 首次触发 mock AI → Drawer 字段级反馈 → 再次触发显示 `复用历史结果` → Labeler 页面显示同一 provenance metadata。
- 完整演示(5 分钟，需真实 API key)：执行真实 Provider Smoke 四项，并展示 provider 切换只改 env。

完整截图清单见 `docs/screenshots/INDEX.md`。
