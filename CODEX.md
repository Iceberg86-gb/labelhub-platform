# CODEX.md

> LabelHub 开发协同契约
>
> 设计基线在 `docs/architecture/labelhub-complete-design-baseline.md`。
> 本文档定义工程实现约束。与基线冲突时:基线是业务事实来源,本文档是工程实现规范。
> 重大技术选型变更必须在 `docs/adr/` 落 ADR,不得在本文档静默修改。


## 1. 项目定位

LabelHub 是一个个人答辩项目,在数据标注平台课题之上,落地"AI 监督信号治理系统"的完整链路。

核心闭环:

- 数据导入
- Schema 搭建
- 标注员作答
- 豆包自动预审
- 多级人工审核
- 多格式可信导出

四个差异化亮点(对齐基线):

- 亮点 1:Schema 版本化与不可变事实建模
- 亮点 2:Quality Ledger + Verdict 派生视图
- 亮点 3:Trusted Export 可复现性
- 亮点 4:AI Provenance 与训练污染防控

亮点的开发硬约束见 §10,任何实现不得绕过 §10 中列出的约束。


## 2. 技术方向

仓库:

- Monorepo
- 顶层目录:`apps/`、`services/`、`packages/`、`docs/`、`infra/`

前端 `apps/web`:

- React 18 + TypeScript
- Vite
- Formily(表单内核)
- dnd-kit(拖拽)
- Zustand(本地 UI 状态)
- TanStack Query(服务端状态)

后端 `services/api`:

- Java 17
- Spring Boot 3.2+
- MyBatis-Plus 3.5.x(ORM)
- Spring Security 6 + JWT(认证授权)
- Flyway(数据库迁移)
- HikariCP(连接池,Spring Boot 默认)
- Jackson(JSON 序列化)
- Spring 内置 `@Async` + 自定义 ThreadPoolTaskExecutor(异步,主队列走 MySQL outbox)
- AWS SDK v2 for S3(对象存储,兼容 MinIO)
- Testcontainers(集成测试)

AI Worker `services/agent`:

- Java 17 + Spring Boot 3.2+(与 `services/api` 同语言、同依赖管理)
- OkHttp / Spring WebClient(调用豆包 API)
- 共享 `packages/contracts` 中的 Java 类型(由 OpenAPI 生成)
- 不引入 LangChain4j 等抽象层,直接控制 raw HTTP 请求,避免 Function Calling 在不同 provider 上行为差异被吞没

LLM:

- 默认使用课题提供的豆包资源(EP `ep-20260514105718-jthdm`)
- 通过 `LlmProvider` 接口抽象,支持 fallback 切换到 OpenAI 或本地 fake provider
- Function Calling / structured output 是主路径,禁止裸文本解析
- AI Worker 不拥有业务事实,只回写审核证据到 API 暴露的接口

本地依赖:

- MySQL 8.0
- Redis 7
- MinIO(S3 兼容对象存储)
- Docker Compose

契约源头:

- **OpenAPI 是契约源头(contract-first)**
- OpenAPI YAML 位于 `packages/contracts/openapi/`,由人工维护
- 前端 TypeScript client 和后端 Java DTO/Controller 接口由 OpenAPI 生成
- 后端 Controller 必须 implements 由 OpenAPI 生成的 interface,签名不一致编译期就失败
- 任何 API 变更必须先改 OpenAPI,再改实现
- 工具链:`openapi-generator-cli` + Maven 插件 `openapi-generator-maven-plugin`

构建:

- Maven(后端 + 多模块)
- pnpm + Turborepo 或 nx(前端 + Monorepo 任务编排)

JDK:

- Java 17 LTS,不使用更高版本(避免与 Spring Boot 3 生态不必要的版本错配)


## 3. 架构边界

整体形态:**模块化单体 + 独立 worker 进程**。

- `services/api` 和 `services/agent` 是**独立部署单元(独立 JVM 进程)**
- 但**共享同一个 MySQL schema**、共享 `packages/contracts` 生成的 DTO
- 不是微服务:不引入服务发现、分布式追踪、独立数据库

`services/api` 是业务权威边界。

核心业务规则必须收口在 API:

- 任务管理
- 数据集导入
- Schema version 发布
- 领取与 session
- submission 写入
- AI 预审任务创建(outbox 写入)
- 人工审核状态流转
- export job 和 export snapshot 创建
- audit log 写入
- 所有状态机的 guard 校验

`services/agent` 只负责 AI 能力:

- 字段级 AI 辅助
- AI 自动预审
- Prompt 编排
- 豆包 API 调用
- Function Calling 输出 schema 校验
- 失败重试
- 失败上限触发后回写"转人工"状态

`services/agent` 的硬约束:

- 不得直接修改最终 verdict
- 不得绕过 API 写核心业务事实
- 所有结果回写必须通过 `services/api` 暴露的内部接口(带 system 账户鉴权)
- 只能读自己消费的 outbox 记录,不得跨域读写其他业务表

`apps/web` 的硬约束:

- 不得私自解释权威业务状态
- 前端只能提交 command,状态迁移结果以后端返回为准
- 前端校验仅用于即时反馈,**权威校验在后端**;前后端校验冲突时以后端为准
- 后端报错时前端必须显示具体字段错误,不能只显示通用错误 toast


## 4. 证据治理规则

LabelHub 采用**局部不可变**,而不是全量事件溯源。

必须保持 **append-only** 的对象(只能 insert,不能 update/delete):

- `schema_versions`
- `submissions`
- `ai_calls`
- `quality_ledger_entries`
- `export_snapshots`
- `audit_logs`
- `task_transitions`

允许作为**当前态**更新的对象(可 update):

- `tasks`
- `sessions`
- `drafts`
- `users`
- `roles`
- `current_verdicts`(派生缓存视图,可重算覆盖)
- `export_jobs`(状态字段可变,但对应的 export_snapshots 不可变)

实现层强制约束:

- append-only 对象对应的 MyBatis Mapper **不得包含 update/delete 方法**(只允许 insertXxx 和 selectXxx)
- 代码 review 时检查 append-only mapper 接口,任何 `int update*` 或 `int delete*` 方法属于违规
- 数据库层用 MySQL 触发器或应用层 aspect 兜底拦截 UPDATE/DELETE 操作(可选,但推荐对 quality_ledger_entries 至少加一道)

设计取舍:

- 只有会破坏监督信号可信链路的对象才做不可变
- 普通业务对象保持简单 CRUD,避免个人项目过度工程
- 详细决策见基线 ADR-009


## 5. 前端规则

`apps/web` 使用领域驱动的前端目录(Feature-Sliced Design 简化版)。

目录结构:

```text
apps/web/src/
  app/           # 应用入口、Provider、Router
  pages/         # 页面编排,只组合 features 和 entities,不写业务动作
  features/      # 业务动作:发布任务、领取任务、提交、打回、导出等
  entities/      # 领域对象视图模型:Task、Submission、Schema、AICall 等
  shared/        # 无业务归属的基础能力:UI 组件、hooks、utils、API client
```

规则:

- `pages` 只做页面编排,不直接调用 API,不写业务逻辑
- `features` 承载业务动作(命令、表单提交、状态切换触发)
- `entities` 承载领域对象的视图模型与查询 hook
- `shared` 只放无业务归属的基础能力(按钮、Toast、http client 封装等)
- 服务端状态使用 TanStack Query,**不要长期复制进 Zustand**
- Zustand 只放纯客户端状态(UI 折叠状态、Designer 当前选中物料、本地草稿临时缓存)
- API 调用必须通过 OpenAPI 生成的 client,**不允许手写 fetch**
- Designer 只生成 JSON Schema,Renderer 只消费 JSON Schema,两端不共享内部 state

详细前端结构决策见 ADR-010(待补)。


## 6. 数据与任务规则

MySQL 是事实数据源。

主队列:**MySQL outbox + 应用层轮询**。对齐基线 ADR-008。

Redis 用途严格限制:

- 短期任务状态缓存(例如 export job 进度推送)
- 轻量分布式锁(例如同一 task 的批量审核操作互斥)
- 本地开发缓存(限流、登录状态)

Redis 禁止用途:

- **不得作为权威队列**(主队列是 MySQL outbox)
- **不得作为主存储**(任何业务事实必须落 MySQL)
- 不得缓存 schema_versions / submissions / quality_ledger_entries(这些是 append-only 事实,直接读 MySQL)

文件存储:

- 文件本体只存对象存储(MinIO 本地 / S3 云端)
- 数据库只保存元数据和 object key
- 上传文件、导出文件统一走对象存储,不在 MySQL 存 BLOB

并发安全(对齐基线 §4 line 137、T5):

- 任务领取使用乐观锁:`UPDATE sessions ... WHERE claim_version = ?`
- 审核状态迁移在 service 层用事务 + 状态机 guard 校验
- AI 调用去重见 §7 幂等键规范

数据库迁移:

- Flyway 管理
- 迁移文件位于 `services/api/src/main/resources/db/migration/`
- 命名规范:`V{YYYYMMDDHHmm}__{snake_case_description}.sql`
- 演示环境启动时自动执行 migration
- 不允许修改已发布的迁移文件,只能追加新版本


## 7. AI 规则

所有 AI 调用必须记录到 `ai_calls` 表(append-only):

- `prompt_version`
- `model`
- `input_hash`(SHA-256,基于规范化后的输入)
- `output_json`(Function Calling 的完整结构化输出)
- `tokens`(prompt + completion)
- `duration_ms`
- `status`(success / structure_invalid / timeout / failed / refused)

结构化输出:

- 必须通过 Function Calling 强制结构化
- 输出 JSON 必须按 OpenAPI 中定义的 `AIReviewResult` schema 校验
- 校验失败 → 重试,达到上限 → 写入 `failed` 状态并触发转人工
- 禁止依赖裸文本解析作为主路径

AI 角色边界:

- AI 结论只作为 evidence,写入 `quality_ledger_entries` 和 `ai_calls`
- **不得直接覆盖人工最终 verdict**
- 字段级 AI 辅助必须携带 provenance,见 §10 亮点 4

### 7.1 幂等键命名规范

下列幂等键格式在所有模块中**强制统一**,不得各自实现:

| 场景 | 幂等键格式 | 实现位置 |
| --- | --- | --- |
| AI 自动预审调用 | `submission_id + ":" + prompt_version + ":" + model_version` | `services/agent` |
| 字段级 AI 辅助调用 | `session_id + ":" + field_path + ":" + prompt_version + ":" + call_ordinal` | `services/agent` |
| 任务领取 | `task_id + ":" + item_id`,配合 `sessions.claim_version` 乐观锁 | `services/api` |
| 导出任务创建 | 导出参数 JSON 规范化后的 SHA-256(规则见 §7.2) | `services/api` |
| Outbox 消费 | `outbox.idempotency_key`,由生产方写入,消费方校验 | 双方共用 |

幂等键的存储:

- AI 调用幂等键作为 `ai_calls` 表的唯一索引
- 导出幂等键作为 `export_jobs` 表的唯一索引(同参数 N 分钟内复用已有结果)
- 重复请求**返回已有结果**,不抛错也不重复写

### 7.2 Hash 规范化规则

所有 hash(`input_hash`、导出 `file_hash`、导出参数幂等键)的计算前**必须先做规范化**,规则统一:

- JSON 字段按 key **字母升序**排列(深度优先递归排序)
- 时间戳统一为 **UTC ISO 8601 字符串**(`YYYY-MM-DDTHH:mm:ss.SSSZ`)
- 浮点数固定保留 **6 位小数**(超出部分四舍五入)
- 列表/记录按业务键升序排列(例如导出按 `submission_id` 升序,审核证据按 `created_at + entry_id` 升序)
- 字符串编码统一 UTF-8
- 文本文件统一 **LF 换行**(不允许 CRLF)
- CSV/Excel 的**字段列顺序按 schema_version 中字段定义顺序固化**,不按运行时 map 遍历顺序
- 所有规范化逻辑收口在 `services/api/src/main/java/.../shared/canonical/Canonicalizer.java`,AI Worker 通过依赖 `packages/contracts` 中的同一份实现复用

任何模块计算 hash 时**禁止自行实现规范化**,必须调用 `Canonicalizer`。

### 7.3 Audit Log 写入清单

`audit_logs` 表只记录**管理动作和高敏操作**,不记录所有状态迁移(避免与 `task_transitions` 和 `quality_ledger_entries` 重复)。

**必须写 `audit_logs`**:

- Task 状态迁移(草稿/发布中/暂停/结束)— 同时写 `task_transitions`,audit_logs 写汇总条目
- Schema version 发布与下架
- Export job 创建、撤销(revoked)、强制删除
- 用户登录、登出、登录失败
- 权限变更(角色分配、角色撤销)
- Adjudication rule 发布、批量重算触发
- 管理接口操作(强制结束任务、强制通过提交、删除测试数据)

**不写 `audit_logs`**(走专用表):

- Submission 状态迁移 → 走 `quality_ledger_entries`
- 草稿自动保存 → 不审计(频率过高,无价值)
- AI 调用 → 走 `ai_calls`
- Reviewer 单条审核动作 → 走 `review_actions` + `quality_ledger_entries`,不重复写 audit_logs
- 普通查询、列表浏览 → 不审计

写入规范:

- `audit_logs.actor_id`:实际用户 id,系统操作填 `system:ai-agent` 或 `system:export-worker`
- `audit_logs.payload_hash`:操作 payload 的规范化 SHA-256,便于后续追溯
- 写入失败时业务操作**继续成功**(audit_logs 是观察用,不是业务关键路径),但应记录到应用日志告警


## 8. 测试规则

测试栈:

- 后端单测:JUnit 5 + Mockito + AssertJ
- 后端集成测试:JUnit 5 + Testcontainers(真实 MySQL + Redis + MinIO)
- 前端单测:Vitest + React Testing Library
- E2E:Playwright
- API 契约测试:基于 OpenAPI 的 schema 校验,前端 mock server 用 Prism

优先覆盖(核心模块覆盖率目标 ≥ 70%):

- schema lint(JSON Schema 解析、字段联动表达式校验)
- 字段联动求值
- 状态迁移 guard(Task、Submission、Export Job 三个状态机)
- verdict 派生函数(必须是 deterministic + pure function,见 §10 亮点 2)
- 领取并发(用 Testcontainers 跑真实 MySQL 验证乐观锁)
- submission → outbox → AI 回写完整链路
- export snapshot hash 稳定性(同输入两次 hash 必须相等)
- 亮点 1-4 各自的演示场景(同时作为答辩 demo 脚本)

E2E 必须覆盖:

- Owner 完整链路:建任务 → 搭模板 → 发布 → 导入数据 → 查看进度 → 导出
- Labeler 完整链路:领任务 → 作答 → 草稿恢复 → 提交 → 看打回 → 修改
- Reviewer 完整链路:接收待审 → 查看 AI 评语 → 通过/打回 → 批量操作
- 跨角色:Labeler 提交 → AI 预审 → Reviewer 通过 → Owner 导出 → 文件可下载

CI 暂不强制(个人项目),但 commit 前必须本地全绿。


## 9. 文档规则

设计基线优先级最高:`docs/architecture/labelhub-complete-design-baseline.md`。

文档目录:

- `docs/architecture/`:基线、架构图、模块设计
- `docs/adr/`:架构决策记录,**重大技术选型变更必须落 ADR**
- `docs/api/`:OpenAPI 规范、Postman Collection、关键接口示例
- `docs/workflows/`:业务流程图、状态机图、演示脚本
- `docs/internal/`:撰写过程的工件(自检表、审计日志、AI Coding 过程记录),不进入最终答辩主文档
- `docs/screenshots/`:Demo 截图,按 `01-owner-task-console.png` 顺序前缀命名

ADR 规范:

- 文件名:`ADR-{NNN}-{kebab-case-title}.md`
- 必含字段:Status(Proposed/Accepted/Superseded)、Context、Decision、Consequences、Alternatives Considered
- 任何与基线或本文档冲突的取舍必须先写 ADR

OpenAPI 文档:

- 源文件位于 `packages/contracts/openapi/labelhub.yaml`
- 变更时同步更新版本号(`info.version`)
- Postman Collection 由 OpenAPI 自动生成,不手动维护


## 10. 差异化亮点的开发硬约束

四个亮点对应基线 5.4、7.7、8.6、9.4。本节列出对开发者的**不可妥协约束**,任何实现不得绕过。

### 亮点 1:Schema 版本化与不可变事实建模

- `schema_versions` 是 append-only(见 §4)
- 一旦 `status='published'`,任何对该行的 update 都是 bug
- **`field_stable_id` 在同一 schema 内永远不可复用**,即使字段被删除,其 stable_id 也不能被新字段重用
- `field_stable_id` 生成规则:发布 schema_version 时由后端分配 UUID v4,前端 Designer **不得自行生成**
- 历史 submission 的 `answers_snapshot` 必须按 `schema_version_id` 反查渲染,**禁止用当前 schema 解读历史 answers**
- 修复已发布 schema 的 typo:必须发布新版本(v1.0.1),不允许原地改

### 亮点 2:Quality Ledger + Verdict 派生视图

- `quality_ledger_entries` 是 append-only(见 §4)
- `current_verdicts` 是缓存视图,可由 ledger + 当前 `adjudication_rule` 完整重算
- **Verdict 派生函数必须满足**:
  - **Deterministic**:相同输入 → 相同输出,任何时间任何机器
  - **Pure function**:不读时钟、不读随机数、不读外部状态
  - **可测试**:输入是 `(List<QualityLedgerEntry>, AdjudicationRule)`,输出是 `Verdict`
- 派生函数实现位置:`services/api/src/main/java/.../verdict/VerdictDeriver.java`
- 单元测试必须覆盖:同输入两次调用结果相等、不同 rule 版本下相同 ledger 产出不同 verdict、空 ledger 的边界行为
- Adjudication rule 升级时:写入 outbox 触发批量重算 worker,worker 按 task 分批写回 `current_verdicts`,过程中读旧值,完成后切换 `rule_version`

### 亮点 3:Trusted Export 可复现性

- `export_snapshots` 是 append-only(见 §4)
- `file_hash` 计算前必须用 §7.2 的规范化规则
- Export Snapshot 必须冻结的字段:`schema_versions`(可能多个)、`adjudication_rule_version`、`field_mapping_snapshot`、`data_range`(submission_id 列表或筛选条件 + 截止时间戳)
- 导出文件**列顺序按 schema_version 字段定义顺序固化**,不按运行时 map 遍历
- 同参数两次导出:hash 必须相等(集成测试强制验证)
- 导出 diff 接口必须能定位差异来源(schema 变化 / rule 变化 / 数据范围变化)

### 亮点 4:AI Provenance 与训练污染防控

- 每次 AI 调用必须写 `ai_calls`(append-only,见 §4)
- Submission 的字段级 provenance 通过 `ai_calls_in_field(submission_id, field_path, ai_call_id, accepted, user_modified_after, ordinal)` 表记录**历史链**,不是单次状态
- "AI 生成 → 人工修改 → 再调用 AI → 再修改" 的每一步都要写入一行 `ai_calls_in_field`,`ordinal` 递增
- 导出"纯人工版":排除任何在 `ai_calls_in_field` 中存在 `accepted=true` 的字段
- 导出"AI 辅助版":保留字段值,附 `ai_call_id` 引用
- 导出"全 trace 版":携带完整 `ai_calls_in_field` 历史
- 字段级 provenance 必须随草稿、提交、审核、导出全链路流转,不允许中间某一步丢失


## 11. 安全与凭证

- 所有 LLM 凭证(豆包 APIKEY、OpenAI API Key)只通过环境变量注入,**禁止写入仓库**
- `.env.example` 只保留变量名和说明,不包含真实值
- JWT secret、数据库密码、对象存储 secret 同样禁止入库
- 演示环境无真实敏感数据,只使用样例数据
- 文件上传必须有大小限制(默认 10MB)和类型白名单(图片、PDF、JSON、JSONL、CSV、Excel),由 `services/api` 在接收前校验
- LLM Prompt 注入风险:Prompt 模板的变量插值必须做转义,避免 Labeler 提交内容覆盖 system prompt;详细策略在 ADR-011(待补)


## 12. 约定速查

- 任何**重大技术选型变更**(语言、框架、ORM、LLM provider)→ 落 ADR
- 任何 **API 变更** → 先改 OpenAPI,再改实现
- 任何 **append-only 对象的实现** → 检查 Mapper 是否只有 insert/select
- 任何 **hash 计算** → 调用 `Canonicalizer`,不自行实现规范化
- 任何 **AI 调用** → 走 `services/agent`,不在 `services/api` 直接调豆包
- 任何 **状态迁移** → 后端 guard 校验,前端只发 command
- 任何 **幂等键** → 按 §7.1 表格统一格式
- 任何 **不确定** → 回到设计基线 `labelhub-complete-design-baseline.md` 查事实模型,回到本文档查实现约束
