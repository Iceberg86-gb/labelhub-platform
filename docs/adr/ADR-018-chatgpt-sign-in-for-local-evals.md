# ADR-018 使用 ChatGPT 登录作为本地评测的模型来源

## Status

Accepted（2026-11-03）。仅作用于 `evals/`。owner 已用真实账号完成验收：登录 → 模型目录 → select → test → apply → 1038 条录制（[W1 基线报告](../../evals/reports/w1-baseline.md)）。

验收中修正过一处：模型目录请求不带 `client_version` 查询参数（这个参数来自参考实现，官方文档里没有；带上之后服务端返回空目录），见 PR #6。

本 ADR 扩展 ADR-011 和 ADR-017，不改变生产运行时的 provider 默认选择（LH-2）。

## Context

ADR-017 H1 要求在固定黄金集上跑出可复现的"一次调用"基线。跑基线需要一个真实模型。项目 owner 希望用自己的 ChatGPT 订阅直接跑评测，不必单独申请 API Key，而且希望在账号可用的新 GPT 模型上线后，可以低成本地切换并对比。

参考实现（owner 的另一个 Electron 项目）的链路是：OAuth 登录 → 获取账号模型目录 → 手动选择或 Auto latest → 实测通过 → 带回滚点切换 → 流式回答。它复用了 Codex CLI 的 client_id，并直接调用 `chatgpt.com/backend-api/codex/*`。

OpenAI 面向开源 / 本地应用的官方文档（[Sign in with ChatGPT for open-source apps](https://developers.openai.com/siwc/token-sharing-open-source/sign-in)）给出的是另一套流程，和参考实现有四处不同：

| 项 | 参考实现 | 官方文档 |
|---|---|---|
| client_id | 复用 Codex CLI 的 | 首次授权用 `dynamic_agent_client` 动态注册，回调返回本应用专属的 `client_id`，之后一直用它 |
| 回调地址 | `localhost:1455`，占用时换端口 | 必须是 `127.0.0.1`，"只有端口可以变" |
| 推理 / 模型目录 | `chatgpt.com/backend-api/codex/responses`、`/codex/models` | `api.openai.com/v1/responses`、`/v1/models`，并明确要求不要调 ChatGPT 的 backend-api |
| 适用范围 | 桌面应用 | 只覆盖开源、本地运行的应用；托管 / 多用户 / 付费应用需要另行申请 |

## Decision

### 1. 以官方文档为准，只在本地评测里使用

- 实现放在 `evals/labelhub_evals/chatgpt/`，**不进入 `services/agent`**。生产 AI 预审仍按 ADR-011 走 DB/env 配置的 OpenAI 兼容 provider。
- 理由：LabelHub 生产环境是服务端、多用户的，不在官方文档覆盖的范围内；而 `evals/` 是开源仓库里由开发者在本机运行的工具，正好在覆盖范围内。
- 参考实现中与官方文档冲突的四处，一律按官方文档实现（client 动态注册、`127.0.0.1`、`api.openai.com/v1`）。

### 2. 登录链路

1. 生成 PKCE（S256）、`state`、`nonce`，每次尝试都重新生成。宿主标识 `ext_agent_host_id` 用 `urn:uuid:<UUIDv4>`，首次生成后固定。
2. **先**启动本机回调服务（`127.0.0.1:1455`，端口被占用时改用系统分配的空闲端口），**再**打开系统浏览器。密码只在 OpenAI 的页面上输入。
3. 回调时校验 `state`；`error=access_denied` 时直接终止；重新授权时，如果回调里的 `client_id` 和发起时的不一致，拒绝这次回调。
4. 用授权码 + `code_verifier` + 同一个 `redirect_uri` + `resource` 换取令牌，并校验 `id_token` 中的 `nonce`。授予的 scope 里必须有 `chatgpt.tokens.use.direct`，否则视为登录失败。
5. 令牌加密保存在本机：对称密钥优先放进系统钥匙串（`keyring`），没有的话退回到 `0600` 权限的密钥文件；令牌文件原子写入，权限同样是 `0600`。access token、refresh token、过期时间和 scope 在每次刷新后**一起**替换。
6. 每次请求前检查有效期，**距离过期不到 5 分钟就刷新**；刷新时用文件锁串行化，防止多个进程同时轮换 refresh token。推理请求遇到 `401` 时，**强制刷新并重试一次**。
7. 登出时调用 OpenID 配置里的 `revocation_endpoint` 吊销 refresh token，再删除本地记录。

### 3. 模型选择：先测试，再切换，可回滚

- 模型目录来自 `GET https://api.openai.com/v1/models`，只保留 `visibility == "list"` 的条目，以 `slug` 作为模型 ID。推理强度、推荐优先级、最低客户端版本这几个字段**有就解析，没有就跳过**。目录里出现的新模型会直接进入候选，不需要改代码。
- 切换分三步：`select` 写草稿 → `test` 用草稿配置**真实发起一次**带 `record_ai_review` 工具的请求 → `apply`。`apply` 会核对三件事：测试凭据对应的草稿没有变过、账号没有变过、配置版本没有被别人改过。全部符合时，先把当前配置压入回滚栈，再生效。`rollback` 恢复上一个配置。
- **Auto latest**：按目录的推荐优先级（数字越小越靠前），同优先级时偏向版本号更新的模型。

### 4. 对参考实现的一处有意偏离：评测里不做"自动跟随"

参考实现开启 Auto latest 后，会在后台定时（启动后约 30 秒，之后每 6 小时）自动切到新模型。**评测不能这样做**：ADR-017 的评测关卡要求基线可复现，模型在评测期间悄悄变化，等于让对比失去意义。

因此在 `evals/` 里：

- `select --auto-latest` 在执行的那一刻把"最新"解析成一个**具体的** `slug` 写进草稿，后面照常走测试 → 应用；
- `check-latest` 只报告"目录里出现了比当前更推荐的模型"，不会自动切换；
- 每次评测运行都把具体的 `provider/model@effort` 写进运行身份（`run_id`）和成绩单。

### 5. 和评测框架的衔接

ChatGPT 是 `evals` 的三个 provider 之一（另外两个是 `fake` 和 `openai-compatible`）。三者共用同一份 prompt、同一个 `record_ai_review` 工具 schema 和同一个录制 / 回放存储。录制的缓存键是 `(model 身份, prompt_version, input_hash)`。所以只要录制过一次，之后回放就不需要登录，也不消耗订阅额度。

## Alternatives Considered

- **照搬参考实现（Codex client_id + backend-api）**：能跑通，但和官方文档明确冲突，接口和授权随时可能变化，而且无法说清合规性。不采用。
- **把 ChatGPT 登录直接接进 `services/agent` 的生产 provider**：服务端多用户场景不在官方文档的覆盖范围内，还要把个人订阅的令牌放到服务器上。不采用；如果将来需要，先提交 OpenAI 的 interest form，再单独写 ADR。
- **只支持 API Key**：最简单，但 owner 希望用订阅额度跑评测。保留为 `openai-compatible` provider，和 ChatGPT provider 并列。

## Consequences

- `evals/` 新增依赖：`cryptography`（必需），`keyring`（可选）。
- ChatGPT Plus 的用量额度是所有应用共享的（官方文档：五小时窗口）。大规模评测应当先录制，之后用回放来复跑。
- 官方的动态注册流程还处于预览阶段，字段可能会变。实现对目录字段做防御式解析，失败时报出明确的错误，不静默回退到其他 provider（延续 ADR-011）。
- 验证方法：OAuth、令牌存储、刷新、401 重试、模型选择、测试 → 应用 → 回滚都有单元测试，用桩服务器（stub server）覆盖，不依赖真实账号。真实账号的端到端登录需要 owner 在本机手动验收一次。
