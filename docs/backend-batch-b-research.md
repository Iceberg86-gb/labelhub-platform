# Backend Batch B RESEARCH: AI Review Provider Registry Source-Of-Truth Switch

任务性质: 纯调研。本文只读仓库证据,不切 cluster,不估 LOC,不写实现,不脑补无证据结论。

## 0. Baseline And Method

- 工作目录: `/Users/gods./Downloads/LabelHub - Platform/`
- 分支/HEAD: `codex/backend-llm-provider-config` / `d50cb72`
- 基线核值:
  - OpenAPI MD5: `7103f921bb1c578cff36b39985b0904e`
  - migrations: `22`
  - humanpending: `190`
- 证据命令:
  - `git branch --show-current && git rev-parse --short HEAD && md5 -q packages/contracts/openapi/labelhub.yaml && find services/api/src/main/resources/db/migration -type f | wc -l && rg -c "^- \\[" humanpending.md`
  - 输出验读: branch=`codex/backend-llm-provider-config`, HEAD=`d50cb72`, MD5=`7103f921bb1c578cff36b39985b0904e`, migrations=`22`, humanpending=`190`.
- 沙箱限制:
  - 未运行 `mvn` / Testcontainers / browser。本文为静态代码与文档 research。

## 1. P-A 当前 Provider 解析全链路

### 1.1 Labeler submit -> outbox

现状: 已做,自动 AI review 入口是 submit 后写 outbox。

证据:

- `nl -ba services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java | sed -n '305,365p'`
  - [SessionService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java:311): `submit(...)` 创建 `SubmissionEntity`。
  - [SessionService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java:337): submission status 写为 `submitted`。
  - [SessionService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java:357): 读取当前 `aiReviewRuleId`。
  - [SessionService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java:358): 调 `enqueueAiReview(...)`。
- `nl -ba services/api/src/main/java/com/labelhub/api/module/outbox/service/OutboxEventService.java`
  - [OutboxEventService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/outbox/service/OutboxEventService.java:30): `enqueueSubmissionAiReview(...)` 创建 `ai_review` outbox 事件。
  - [OutboxEventService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/outbox/service/OutboxEventService.java:60): payload 只包含 `submissionId/sessionId/taskId/schemaVersionId/datasetItemId/labelerId/contentHash/aiReviewRuleId/idempotencySeed`。

验读痕迹:

- 当前 outbox payload 没有 provider key / secret / ciphertext 字段。
- 这是 Batch B 的安全红线: 不得把解密后的明文 key 加入 outbox payload,因为 outbox 是数据库持久化事件。

### 1.2 Agent worker -> API context -> agent provider -> API result

现状: 已做,自动链路的模型调用点在 `services/agent`,不是 `services/api` 的 `AiReviewService.review(...)`。

证据:

- `nl -ba services/agent/src/main/java/com/labelhub/agent/outbox/JdbcOutboxRepository.java`
  - [JdbcOutboxRepository.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/JdbcOutboxRepository.java:20): agent 查 due `ai_review` events。
  - [JdbcOutboxRepository.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/JdbcOutboxRepository.java:30): 直接从 `outbox` 表读取事件。
- `nl -ba services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java`
  - [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:74): 从事件取 `submissionId`。
  - [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:75): 调 API `getContext(submissionId)`。
  - [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:76): 调 `aiReviewProvider.review(context)`。
  - [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:77): 将 result report 回 API。
  - [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:84): 异常只进入 retry/dead-letter 处理。
- `nl -ba services/agent/src/main/java/com/labelhub/agent/api/WebClientAiReviewApiClient.java`
  - [WebClientAiReviewApiClient.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/api/WebClientAiReviewApiClient.java:24): `GET /internal/ai-review/submissions/{submissionId}/context`。
  - [WebClientAiReviewApiClient.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/api/WebClientAiReviewApiClient.java:34): `POST /internal/ai-review/results`。
- `nl -ba services/api/src/main/java/com/labelhub/api/module/ai/web/InternalAiReviewController.java`
  - [InternalAiReviewController.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/web/InternalAiReviewController.java:32): context 由 API `AiReviewService.getInternalContext` 生成。
  - [InternalAiReviewController.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/web/InternalAiReviewController.java:40): agent result 由 API `recordInternalResult` 落库。

验读痕迹:

- 自动链路中,provider 调用发生在 `services/agent` 的 `AiReviewProvider`。
- API 内部 endpoint 提供业务 context 和接收结果,不是当前自动链路的 provider/key source。

### 1.3 Agent 当前 provider 实现

现状: 部分。agent 有 provider 抽象和 local fake provider,但未找到真实 Doubao/OpenAI provider 实现。

证据:

- `rg -n "class|record|interface|AiReviewProvider|apiKey|provider|llm|review\\(" services/agent/src/main/java services/agent/src/main/resources -g '*.java' -g '*.yml' -g '*.yaml'`
  - [AiReviewProvider.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/llm/AiReviewProvider.java:6): agent `AiReviewProvider` interface。
  - [FakeAiReviewProvider.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/llm/FakeAiReviewProvider.java:13): only located `AiReviewProvider` implementation, `@Profile("local")`。
  - [application.yml](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/resources/application.yml:26): agent has `labelhub.llm.primary-provider`.
  - [application.yml](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/resources/application.yml:28): agent has Doubao endpoint/api-key/model env slots.
  - [application.yml](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/resources/application.yml:32): agent has OpenAI api-key/model env slots.
- `find services/agent/src/main/java/com/labelhub/agent -type f -maxdepth 4 | sort`
  - 验读: `services/agent/src/main/java/com/labelhub/agent/llm/` 下只有 `AiReviewProvider`, `FakeAiReviewProvider`, `LlmProvider`, `FakeLlmProvider`。
- `nl -ba services/agent/src/main/java/com/labelhub/agent/llm/LlmProvider.java && nl -ba services/agent/src/main/java/com/labelhub/agent/llm/FakeLlmProvider.java`
  - [LlmProvider.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/llm/LlmProvider.java:3): old/simple `LlmProvider` abstraction。
  - [FakeLlmProvider.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/llm/FakeLlmProvider.java:6): only fake implementation, `@Profile("local")`。

缺口:

- 文档/配置描述 Doubao default + OpenAI fallback,但 agent runtime 没有可实证的真实 provider implementation。
- Batch B 不是简单改 provider source-of-truth,还要补足 agent 真实 provider resolution/call path 或改变调用架构。

### 1.4 API 手动/debug AI review path

现状: 已做,但这是 deprecated/debug owner 手动 path,不是 submit outbox 自动 path。

证据:

- `nl -ba services/api/src/main/java/com/labelhub/api/module/ai/web/AiReviewController.java`
  - [AiReviewController.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/web/AiReviewController.java:52): `triggerSubmissionAiReview` 标注 `@Deprecated(since = "P-A")`。
  - [AiReviewController.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/web/AiReviewController.java:58): response header `X-LabelHub-Debug-Only: manual-ai-review`。
  - [AiReviewController.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/web/AiReviewController.java:60): 调 `aiReviewService.review(...)`。
- `nl -ba services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java | sed -n '50,115p'`
  - [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:77): API service 注入 `AiProvider`。
  - [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:100): constructor 注入 `AiProvider`。
- `nl -ba services/api/src/main/java/com/labelhub/api/module/ai/provider/OpenAiCompatibleProvider.java | sed -n '1,140p'`
  - [OpenAiCompatibleProvider.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/provider/OpenAiCompatibleProvider.java:23): bean name `openAiCompatibleAiProvider`。
  - [OpenAiCompatibleProvider.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/provider/OpenAiCompatibleProvider.java:24): selected by `labelhub.ai.active-provider=openai-compatible`。
  - [OpenAiCompatibleProvider.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/provider/OpenAiCompatibleProvider.java:47): requires `base-url`。
  - [OpenAiCompatibleProvider.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/provider/OpenAiCompatibleProvider.java:48): requires `api-key`。
  - [OpenAiCompatibleProvider.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/provider/OpenAiCompatibleProvider.java:97): sends `Authorization: Bearer ` + `props.apiKey()`.
- `nl -ba services/api/src/main/resources/application.yml | sed -n '30,56p'`
  - [application.yml](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/resources/application.yml:32): `active-provider: ${LABELHUB_AI_PROVIDER:mock}`。
  - [application.yml](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/resources/application.yml:34): API OpenAI-compatible `base-url` from `AI_BASE_URL`。
  - [application.yml](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/resources/application.yml:35): API key from `AI_API_KEY`。

验读痕迹:

- API manual/debug path 的 provider/key source 是 env/config。
- 该路径和自动 outbox/agent path 并存。Batch B 若只改 API `AiProvider` bean,不会自动改变 agent submit path。

## 2. Source-Of-Truth 切换接入点

### 2.1 Batch A registry 现状

现状: 已做 key cabinet,但尚未接入 AI review runtime。

证据:

- `nl -ba services/api/src/main/resources/db/migration/V202612080900__llm_provider_configs.sql`
  - [V202612080900__llm_provider_configs.sql](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/resources/db/migration/V202612080900__llm_provider_configs.sql:1): `llm_provider_configs` table。
  - [V202612080900__llm_provider_configs.sql](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/resources/db/migration/V202612080900__llm_provider_configs.sql:3): owner-scoped `owner_id`。
  - [V202612080900__llm_provider_configs.sql](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/resources/db/migration/V202612080900__llm_provider_configs.sql:8): `secret_ciphertext`。
  - [V202612080900__llm_provider_configs.sql](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/resources/db/migration/V202612080900__llm_provider_configs.sql:11): `secret_ref`。
  - [V202612080900__llm_provider_configs.sql](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/resources/db/migration/V202612080900__llm_provider_configs.sql:17): `(owner_id, enabled)` index。
- `nl -ba services/api/src/main/java/com/labelhub/api/module/ai/providerconfig/LlmSecretEncryptor.java`
  - [LlmSecretEncryptor.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/providerconfig/LlmSecretEncryptor.java:16): master key env name `LABELHUB_LLM_PROVIDER_MASTER_KEY`。
  - [LlmSecretEncryptor.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/providerconfig/LlmSecretEncryptor.java:42): AES/GCM/NoPadding。
  - [LlmSecretEncryptor.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/providerconfig/LlmSecretEncryptor.java:54): decrypt method exists in API module。
- `nl -ba services/api/src/main/java/com/labelhub/api/module/ai/providerconfig/LlmProviderConfigService.java | sed -n '101,157p'`
  - [LlmProviderConfigService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/providerconfig/LlmProviderConfigService.java:108): saved test connection decrypts stored secret only when request does not supply override secret。
  - [LlmProviderConfigService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/providerconfig/LlmProviderConfigService.java:152): `decryptStoredSecret` is private service helper。
- `nl -ba packages/contracts/openapi/labelhub.yaml | sed -n '1735,1848p'`
  - [labelhub.yaml](/Users/gods./Downloads/LabelHub%20-%20Platform/packages/contracts/openapi/labelhub.yaml:1739): response schema `LlmProviderConfig`。
  - [labelhub.yaml](/Users/gods./Downloads/LabelHub%20-%20Platform/packages/contracts/openapi/labelhub.yaml:1760): response exposes `hasSecret`。
  - [labelhub.yaml](/Users/gods./Downloads/LabelHub%20-%20Platform/packages/contracts/openapi/labelhub.yaml:1762): response exposes `secretLast4`。
  - [labelhub.yaml](/Users/gods./Downloads/LabelHub%20-%20Platform/packages/contracts/openapi/labelhub.yaml:1767): response exposes `secretUpdatedAt`。
  - [labelhub.yaml](/Users/gods./Downloads/LabelHub%20-%20Platform/packages/contracts/openapi/labelhub.yaml:1771): response exposes `secretRef`。
  - [labelhub.yaml](/Users/gods./Downloads/LabelHub%20-%20Platform/packages/contracts/openapi/labelhub.yaml:1800): request `secret` is `writeOnly`。

验读痕迹:

- `LlmSecretEncryptor` 和 provider CRUD 都在 `services/api`。
- 当前没有 runtime resolver,只有 management/test connection service。

### 2.2 Runtime resolver 缺口

现状: 缺。

证据:

- `rg -n "select.*Enabled|enabled.*provider|ProviderResolver|RuntimeProvider|decryptStoredSecret|LlmSecretEncryptor|LlmProviderConfigMapper" services/api/src/main/java services/agent/src/main/java -g '*.java'`
  - 只找到 `LlmProviderConfigMapper`, `LlmProviderConfigService`, `LlmSecretEncryptor`。
  - 未找到 `ProviderResolver` / runtime provider registry / enabled/default selection。
- [LlmProviderConfigMapper.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/providerconfig/LlmProviderConfigMapper.java:28): `selectByOwner(ownerId)` 列出 owner configs。
- [LlmProviderConfigMapper.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/providerconfig/LlmProviderConfigMapper.java:39): `selectByIdAndOwner(id, ownerId)`。
- [LlmProviderConfigMapper.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/providerconfig/LlmProviderConfigMapper.java:64): delete by id/owner。

缺口:

- 没有“按 owner/task 选一个 enabled provider”的 mapper/service。
- 没有 default/priority/primary provider 字段。
- 没有 runtime DTO 能安全承载 decrypted secret。

### 2.3 可行接入点选项,需 owner 裁决

以下是方案空间,非实现裁决。

**选项 A: Agent 侧 registry resolver + 解密**

- agent 直接读取 `llm_provider_configs`,部署同一个 `LABELHUB_LLM_PROVIDER_MASTER_KEY`,在 agent 内存中解密后调 provider。
- 优点: 明文 key 不需要跨 API -> agent HTTP 传输,也不进入 outbox。
- 风险: 扩大 master key/解密能力到 agent; 需要在 agent 侧复用或抽取加密代码; 需要 agent 可确定 ownerId/provider selection; agent 日志/错误也必须接 `SecretRedactor` 等价能力。
- 证据基线:
  - agent 与 API 共享 DB schema 的文档设计见 [labelhub-complete-design-baseline.md](/Users/gods./Downloads/LabelHub%20-%20Platform/docs/architecture/labelhub-complete-design-baseline.md:110)。
  - agent 当前直接读 outbox DB,见 [JdbcOutboxRepository.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/JdbcOutboxRepository.java:30)。

**选项 B: API internal endpoint 向 agent 返回 runtime provider config + 明文 key**

- API 根据 submission/task/owner 解密,通过 `/internal/...` 返回 agent 所需 provider config。
- 优点: 复用 API 端加密服务和 owner/task 查询。
- 风险: 明文 key 经 internal HTTP response 流动,必须防止 WebClient/log/error/body capture 泄露; internal token 认证不等于 secret-safe 通道。
- 证据基线:
  - internal endpoint 当前由 `X-Internal-Token` 保护,见 [InternalTokenFilter.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/security/InternalTokenFilter.java:28)。
  - `SecurityConfig` 对 `/internal/**` 是 permitAll,实际依赖 filter token,见 [SecurityConfig.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/config/SecurityConfig.java:50)。

**选项 C: API 负责 provider invocation,agent 只做 outbox 编排**

- agent 仍 poll outbox,但把 context/submission 交给 API 的 internal “invoke” endpoint; API 解密并调用 provider,返回结构化 result。
- 优点: 明文 key 留在 API 内存,复用 Batch A 解密与 API-side failed-call recorder/scoring。
- 风险: 架构变化大,会把 provider-specific prompt/model/output adapter ownership 从 ADR-011 所说的 `services/agent` 边界拉回 API,需要 ADR-011 明确修订。
- 证据基线:
  - ADR-011 当前要求 provider-specific prompts/model/output adapters stay in `services/agent`,见 [ADR-011-doubao-default-openai-fallback.md](/Users/gods./Downloads/LabelHub%20-%20Platform/docs/adr/ADR-011-doubao-default-openai-fallback.md:15)。

**选项 D: Registry-first API `AiProvider` bean only**

- 只改 `services/api` 的 `AiProvider` bean。
- 结论: 不足以完成 Batch B 自动链路,因为 submit outbox 自动模型调用发生在 `services/agent`。最多影响 deprecated manual trigger 和 field assist。
- 证据:
  - manual trigger debug-only,见 [AiReviewController.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/web/AiReviewController.java:52)。
  - agent 自动链路调用 `aiReviewProvider.review(context)`,见 [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:76)。

## 3. Provider 选择粒度

现状: 部分。Batch A 表是 owner-scoped; AI review 自动 context 当前没有 ownerId 字段; API 可从 submission -> task 推导 owner。

证据:

- [V202612080900__llm_provider_configs.sql](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/resources/db/migration/V202612080900__llm_provider_configs.sql:3): provider config 有 `owner_id`。
- [TaskEntity.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/task/entity/TaskEntity.java:30): task 有 `ownerId` 字段。
- [SessionService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/session/service/SessionService.java:330): submission 写入 `taskId`。
- [OutboxEventService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/outbox/service/OutboxEventService.java:64): outbox payload 带 `taskId`。
- [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:365): internal context 生成时 API 通过 submission.taskId 查 `TaskEntity`。
- [AiReviewContext.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/api/AiReviewContext.java:7): agent context 字段包含 submissionId/idempotencyKey/prompt/rule/input/thresholds,不包含 ownerId/taskId/providerConfigId。

缺口:

- 若选 agent-side resolver,agent 当前 context 不含 ownerId。需要新增 context 字段、或 agent 自查 DB submission/task、或在 outbox payload 中安全携带 ownerId。不能携带 secret。
- 若选 API-side resolver,API 已能在 `getInternalContext` 通过 submission->task 拿 owner,但当前 context DTO 仍不返回 provider 选择。
- 多 provider selection 规则未定义:
  - owner 下可能有多个 enabled provider。
  - table 没有 `is_default`, `priority`, `purpose`, `task_id`, `provider_role`。
  - `provider_name` 仅 owner-scoped 唯一,不能表达 fallback order。

待 owner/gate 裁决:

- Provider 粒度是 owner global,task-specific,还是 purpose-specific(`ai_review`, `field_assist`)?
- 多个 enabled provider 的 primary/fallback 规则是什么?
- 是否需要在 Batch B 加 schema 字段表达 default/priority,还是先用单 enabled config 约束?

## 4. Fallback / 缺配置 / 失败语义

### 4.1 当前 ADR-011 fallback baseline

现状: 文档已做,实现部分。

证据:

- `nl -ba docs/adr/ADR-011-doubao-default-openai-fallback.md`
  - [ADR-011-doubao-default-openai-fallback.md](/Users/gods./Downloads/LabelHub%20-%20Platform/docs/adr/ADR-011-doubao-default-openai-fallback.md:9): AI worker defaults to Doubao endpoint; OpenAI and fake providers behind `LlmProvider` abstraction as fallback options。
  - [ADR-011-doubao-default-openai-fallback.md](/Users/gods./Downloads/LabelHub%20-%20Platform/docs/adr/ADR-011-doubao-default-openai-fallback.md:14): Provider failures can fall back without changing business modules。
  - [ADR-011-doubao-default-openai-fallback.md](/Users/gods./Downloads/LabelHub%20-%20Platform/docs/adr/ADR-011-doubao-default-openai-fallback.md:15): Provider-specific prompts/model/output adapters stay in `services/agent`。
- `nl -ba services/agent/src/main/resources/application.yml | sed -n '26,34p'`
  - [application.yml](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/resources/application.yml:27): `LLM_PRIMARY_PROVIDER` default `doubao`。
  - [application.yml](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/resources/application.yml:29): `DOUBAO_ENDPOINT`。
  - [application.yml](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/resources/application.yml:30): `DOUBAO_API_KEY`。
  - [application.yml](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/resources/application.yml:33): `OPENAI_API_KEY`。
- `find services/agent/src/main/java/com/labelhub/agent -type f ...`
  - 验读: 未找到真实 Doubao/OpenAI/fallback provider implementation,仅 fake。

结论:

- ADR-011 的 fallback 语义存在于文档/配置,但当前代码中未实证到真实 agent provider fallback。
- Batch B 修订 ADR-011 时,必须明确是 registry 替代 env/fallback,还是 registry 优先且 env/fallback 保留。

### 4.2 当前失败处理语义

现状: 分两条路径。

证据:

- Agent 自动 path:
  - [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:84): catch any exception。
  - [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:98): schedule retry or dead-letter。
  - [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:100): max attempts -> dead-letter。
  - 验读: agent failure path 未调用 `reportResult`,未看到向 API 写 failed `ai_calls` 的 internal failure endpoint。
- API manual/debug path:
  - [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:476): `invokeProvider(...)`。
  - [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:488): `retryPolicy.invokeWithRetry(...)`。
  - [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:493): failed attempt recorder。
  - [FailedAiCallRecorder.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/FailedAiCallRecorder.java:40): failed attempt recorded in a new transaction。
  - [FailedAiCallRecorder.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/FailedAiCallRecorder.java:57): creates failed `AiCallEntity`。
  - [FailedAiCallRecorder.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/FailedAiCallRecorder.java:77): status `FAILED`。

缺口:

- 如果 Batch B 在 agent 侧新增真实 provider call,则 provider failure 当前只会影响 outbox retry/dead-letter,不会自动复用 API `FailedAiCallRecorder`。
- gate 必须裁决: agent provider failure 是否需要通过 API internal failure endpoint 记录 failed `ai_calls`,还是保持现有 outbox retry/dead-letter 语义。

### 4.3 Fallback 方案空间,需 owner 裁决

以下只列选项,不裁决。

1. **Registry required / fail closed**
   - owner 没有可用 DB provider config 时,AI review 不调用 env fallback。
   - 风险: 未配置 owner 的 submit 后 AI review 可能进入 retry/dead-letter 或失败事实,影响 P-A 可用性。
   - 优点: source-of-truth 清晰,不会悄悄使用旧 env key。

2. **Registry-first, env fallback**
   - owner 有 enabled registry provider 时用 DB;无配置或 DB provider 不可用时退回 ADR-011 env Doubao/OpenAI fallback。
   - 风险: source-of-truth 不纯,owner UI 可能显示已未配置但系统仍调用 env provider。
   - 优点: 切换期最不容易导致 AI review 挂掉。

3. **Feature-flag / staged opt-in**
   - 默认保留 env/config;只有 owner 显式启用 registry runtime 时切到 DB provider。
   - 风险: 实现与测试矩阵增加。
   - 优点: 承重路径可灰度,较适合 Batch B 这种最高承重变更。

4. **Registry fallback chain**
   - 在 owner 的 provider configs 内按 priority/default/createdAt/providerType fallback。
   - 风险: 当前 schema 没 priority/default/purpose 字段;若硬用 createdAt/enabled 会把产品语义藏在偶然排序里。

必须明确的 failure semantics:

- owner 未配置 provider: 记录可诊断的 AI review provider config missing? 进入 outbox retry? 直接 dead-letter? 是否写 failed `ai_calls`?
- DB provider 调用 401/403: 是否 fallback env? 是否 retry? 是否记录 failed attempt?
- DB provider timeout/5xx: 是否 fallback env? 是否按当前 retry policy? agent/API 两边如何统一?
- registry decrypt failure/master key missing: 启动失败还是该调用失败? 这会影响 API/agent 部署。

## 5. P-A / P8 Evidence 链不受影响的边界线

Batch B 只应改“用哪个 provider/key 调模型”的来源。以下 evidence 语义应保持行为不变或空 diff,除非 gate 明确裁决。

### 5.1 ADR-005: AI 仍非裁决

证据:

- [ADR-005-ai-evidence-not-verdict.md](/Users/gods./Downloads/LabelHub%20-%20Platform/docs/adr/ADR-005-ai-evidence-not-verdict.md:9): AI calls produce evidence and provenance, do not directly overwrite final dataset verdict。
- [ReviewerSubmissionPage.tsx](/Users/gods./Downloads/LabelHub%20-%20Platform/apps/web/src/pages/reviewer/ReviewerSubmissionPage.tsx:141): reviewer page renders AI evidence panel。
- [ReviewerSubmissionPage.tsx](/Users/gods./Downloads/LabelHub%20-%20Platform/apps/web/src/pages/reviewer/ReviewerSubmissionPage.tsx:144): UI copy says “辅助参考,不作为最终裁决。人工审核结果以右侧操作为准。”
- [ReviewerSubmissionPage.tsx](/Users/gods./Downloads/LabelHub%20-%20Platform/apps/web/src/pages/reviewer/ReviewerSubmissionPage.tsx:171): human decision card titled “人工最终裁决”。

Batch B boundary:

- 不改 reviewer final verdict derivation。
- 不把 provider recommendation 升级成 automatic approve/reject。

### 5.2 Idempotency

证据:

- API manual/debug idempotency:
  - [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:207): builds idempotency key。
  - [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:209): select existing by idempotency key。
  - [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:760): key format includes provider/model/promptVersionId/adapter。
- Agent internal idempotency:
  - [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:385): internal context includes `internalIdempotencyKey(...)`。
  - [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:423): recordInternalResult reuses existing row by command idempotency key。
  - [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:832): internal key format `submission:%d:ai_review:promptVersionId:%d:adapter:%s` plus optional rule id。

Batch B boundary:

- 不应 accidentally change idempotency key format unless gate explicitly includes migration/backfill/compatibility.
- If provider source changes but idempotency key omits provider config id on internal path, stale evidence reuse risk must be evaluated. This is a Batch B gate question, because changing provider source may require idempotency key to include provider provenance.

### 5.3 Retry / failed-call recording

证据:

- API retry:
  - [AiRetryPolicy.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiRetryPolicy.java:27): retry wrapper。
  - [AiRetryPolicy.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiRetryPolicy.java:31): max attempts from OpenAI-compatible properties。
  - [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:493): failed attempt recorder call。
  - [FailedAiCallRecorder.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/FailedAiCallRecorder.java:57): failed `AiCallEntity` persisted。
- Agent retry:
  - [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:98): worker failure handler。
  - [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:104): exponential backoff。

Batch B boundary:

- Do not silently replace current retry/failure persistence semantics.
- If real provider call stays in agent, need explicit design for failed provider attempts as evidence, because current API failed-call recorder is not in agent path.

### 5.4 P8 scoring and normalization

证据:

- [AiReviewScoringProperties.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewScoringProperties.java:11): default pass threshold 0.80。
- [AiReviewScoringProperties.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewScoringProperties.java:12): reject floor 0.20。
- [AiReviewScoringProperties.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewScoringProperties.java:13): `equal-weight-three-zone-v2`。
- [AiReviewScoringPolicy.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewScoringPolicy.java:21): score entrypoint with configured dimensions/threshold。
- [AiReviewScoringPolicy.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewScoringPolicy.java:50): recommendation derived by scoring policy。
- [AiReviewScoringPolicy.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewScoringPolicy.java:92): pass/reject/manual_review 3-zone decision。
- [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:541): API converts agent command into raw result。
- [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:563): agent raw result is normalized by API scoring authority。

Batch B boundary:

- Do not change `AiReviewScoringPolicy`, `AiReviewScoringProperties`, threshold validation, or `internalCommandResult -> normalizedResult` authority unless separately gated.

### 5.5 Ledger / AI recommendation evidence

证据:

- [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:594): `appendAiEvidenceLedger`。
- [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:597): AI field findings ledger。
- [AiReviewService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java:599): AI overall recommendation ledger。
- [LedgerService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/quality/service/LedgerService.java:41): evidence type `ai_field_finding`。
- [LedgerService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/quality/service/LedgerService.java:42): evidence type `ai_overall_recommendation`。
- [LedgerService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/quality/service/LedgerService.java:250): appends AI overall recommendation。
- [LedgerService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/quality/service/LedgerService.java:369): validates recommendation enum pass/reject/manual_review。

Batch B boundary:

- Do not change evidence type names or ledger payload shape except adding safe provider provenance if explicitly gated.
- No secret/last4/ciphertext should enter ledger payload.

## 6. ADR-011 修订范围

现状: Batch B 明确需要修订 ADR-011,因为 DB registry 成为 runtime source-of-truth 会改变已接受的 env/fallback source rule。

证据:

- [ADR-011-doubao-default-openai-fallback.md](/Users/gods./Downloads/LabelHub%20-%20Platform/docs/adr/ADR-011-doubao-default-openai-fallback.md:5): status Accepted。
- [ADR-011-doubao-default-openai-fallback.md](/Users/gods./Downloads/LabelHub%20-%20Platform/docs/adr/ADR-011-doubao-default-openai-fallback.md:9): current decision is Doubao default, OpenAI/fake fallback behind `LlmProvider`。
- [ADR-011-doubao-default-openai-fallback.md](/Users/gods./Downloads/LabelHub%20-%20Platform/docs/adr/ADR-011-doubao-default-openai-fallback.md:15): provider-specific prompts/model/output adapters stay in `services/agent`。
- [humanpending.md](/Users/gods./Downloads/LabelHub%20-%20Platform/humanpending.md:471): Batch A closure explicitly says Batch B switches AI review from env/config provider to DB provider registry and requires ADR-011 revision/owner 裁决。

ADR revision option space:

1. **Registry-first with env fallback**
   - New rule: owner DB registry is primary; missing/disabled/provider failure can fall back to ADR-011 env Doubao/OpenAI according to explicit conditions.
   - ADR must define when fallback is allowed and how evidence records fallback provider.

2. **Registry-only**
   - New rule: DB provider registry is the only runtime source for AI review provider/key; env configs are bootstrapping/test only.
   - ADR must define owner-not-configured behavior and failed evidence semantics.

3. **Staged opt-in**
   - New rule: env/config remains default until task/owner opts into DB registry; then registry becomes source-of-truth for that owner/task.
   - ADR must define migration/rollout and final desired end state.

4. **Agent-owned adapters, API-owned credentials**
   - Possible compromise: provider-specific prompts/output adapters remain in agent per ADR-011, while provider credential/model endpoint source moves to API DB registry.
   - ADR must state how agent obtains runtime credentials without persisting/logging plaintext.

Stop condition:

- If implementation discovers that provider-specific prompts/model/output adapters must move out of agent, or env fallback semantics must change beyond owner-approved scope, stop and ask for ADR-011 owner裁决 before implementation.

## 7. 跨服务密钥架构专节

这是 Batch B 的新增安全红线。Batch A 已守住“DB 只存密文/API 不回传完整 key/audit redaction”,但 Batch B 需要让实际 model caller 拿到可用 secret。

### 7.1 已核实事实

- 当前 automatic AI review model caller 是 `services/agent`,见 [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:76)。
- 当前 decrypt implementation 在 `services/api`,见 [LlmSecretEncryptor.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/providerconfig/LlmSecretEncryptor.java:54)。
- 当前 outbox payload 不含 secret,见 [OutboxEventService.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/outbox/service/OutboxEventService.java:60)。
- 当前 internal context 不含 secret/providerConfigId,见 [AiReviewContext.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/api/AiReviewContext.java:7)。
- 当前 internal endpoints 靠 `X-Internal-Token`,见 [InternalTokenFilter.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/security/InternalTokenFilter.java:28)。

### 7.2 红线

- 明文 key 不得写入 outbox payload。
- 明文 key 不得写入 `ai_calls.request_payload`, `quality_ledger_entries.payload`, audit payload, exception message, or frontend/generated response。
- 明文 key 不得通过普通 business DTO 长期存在。
- 如果 API 将明文 key 返回 agent,必须防止 request/response body logging、error propagation、test failure dump。
- 如果 agent 自行解密,agent 必须拥有同等 secret redaction/test guard,并且 master key 部署范围扩大需 owner 明确接受。

### 7.3 架构判断

- 最安全的方向通常是“不跨服务传明文 key”: agent 自行解密并只在内存使用,或 API 自己完成 provider invocation。
- 最危险的方向是把 decrypted key 塞进 outbox 或 durable internal result/context。当前没有任何证据支持这么做,且会直接破坏 Batch A 的安全目标。
- 若 gate 选择 API->agent plaintext transfer,必须把 transfer DTO 标为 internal-only,加 redaction tests,并证明 `WebClient` / exception / audit 不会泄漏 body。

## 8. 缺口清单

| 缺口 | 状态 | 证据 |
| --- | --- | --- |
| Provider resolver | 缺 | `rg "ProviderResolver|RuntimeProvider"` 无 runtime resolver; mapper only list/get by owner,见 [LlmProviderConfigMapper.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/providerconfig/LlmProviderConfigMapper.java:28)。 |
| 跨服务 key 传递/解密架构 | 缺 | decrypt 在 API [LlmSecretEncryptor.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/providerconfig/LlmSecretEncryptor.java:54), caller 在 agent [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:76)。 |
| Provider 选择粒度 | 部分 | DB owner-scoped [V202612080900__llm_provider_configs.sql](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/resources/db/migration/V202612080900__llm_provider_configs.sql:3), agent context 无 ownerId/provider id [AiReviewContext.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/api/AiReviewContext.java:7)。 |
| 多 provider primary/fallback 规则 | 缺 | table 有 enabled,无 default/priority/purpose [V202612080900__llm_provider_configs.sql](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/resources/db/migration/V202612080900__llm_provider_configs.sql:12)。 |
| Fallback 语义 | 部分 | ADR says Doubao default/OpenAI fallback [ADR-011-doubao-default-openai-fallback.md](/Users/gods./Downloads/LabelHub%20-%20Platform/docs/adr/ADR-011-doubao-default-openai-fallback.md:9), real agent fallback implementation not found. |
| Agent real provider implementation | 缺/未核实到 | only fake agent providers found [FakeAiReviewProvider.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/llm/FakeAiReviewProvider.java:13), [FakeLlmProvider.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/llm/FakeLlmProvider.java:6)。 |
| Agent provider failure -> failed `ai_calls` evidence | 缺/需裁决 | worker catch only schedules retry/dead-letter [OutboxAiReviewWorker.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/agent/src/main/java/com/labelhub/agent/outbox/OutboxAiReviewWorker.java:84); failed recorder exists on API manual path [FailedAiCallRecorder.java](/Users/gods./Downloads/LabelHub%20-%20Platform/services/api/src/main/java/com/labelhub/api/module/ai/service/FailedAiCallRecorder.java:40)。 |
| ADR-011 revision | 缺 | current ADR accepted [ADR-011-doubao-default-openai-fallback.md](/Users/gods./Downloads/LabelHub%20-%20Platform/docs/adr/ADR-011-doubao-default-openai-fallback.md:5), Batch B changes source-of-truth per humanpending closure [humanpending.md](/Users/gods./Downloads/LabelHub%20-%20Platform/humanpending.md:471)。 |

## 9. 承重 + ADR 判断

- 承重等级: 最高承重。
- 原因:
  - Batch B 改 P-A AI review runtime provider source-of-truth。
  - 自动链路跨 `services/api` 和 `services/agent`。
  - 需要处理明文 key 在服务边界处的安全生命周期。
  - 需要修订 ADR-011。
  - 不能破坏 ADR-005、P-A idempotency/retry/evidence、P8 scoring。

必须保持不变:

- ADR-005: AI evidence 不是最终裁决。
- P8: API-side scoring authority and `equal-weight-three-zone-v2` 三段阈值。
- Ledger evidence types and payload semantics.
- Outbox durable payload 不含 secret。
- Existing review UI principle: AI 证据弱化,人工裁决强化。

Batch B 可改范围:

- Runtime provider source/resolver。
- Runtime provider provenance fields, only if needed and safe。
- ADR-011 text, after owner 裁决。
- Agent/API internal provider invocation architecture, after gate 裁决。

## 10. Gate Must Answer

1. Runtime source-of-truth: registry-only, registry-first env fallback, or staged opt-in?
2. Provider selection granularity: owner global vs task/purpose-specific?
3. Multi-provider order: single enabled required, explicit default, priority, or provider type fallback?
4. Cross-service key architecture: agent decrypts, API invokes, or API sends plaintext runtime config?
5. Missing config behavior: fail closed, fallback env, or no-op evidence?
6. Provider call failure behavior: outbox retry/dead-letter only, failed `ai_calls`, or both?
7. Internal idempotency key: should provider config/provenance become part of the key to avoid stale evidence reuse when provider source changes?
8. ADR-011 revision wording: what remains in `services/agent` vs DB registry?

