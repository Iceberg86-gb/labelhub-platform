# LabelHub 设计基线

## 0. 文档导读

本文是 LabelHub 项目的工程落地基线，既用于课题答辩，也作为后续开发、README、演示视频和 API 文档的依据。
评分依据优先级最高：功能完备性 60%、工程质量 25%、产品体验 15%。本文先覆盖课题硬要求，再把 4 个差异化亮点嵌入到对应功能章节。
LabelHub 的定位是“AI 监督信号治理系统”：在数据标注平台的基础上，强调 schema 演化、AI provenance、审核证据和导出可复现。
评审可优先阅读第 3-9 章检查功能闭环；阅读 5.4、7.7、8.6、9.4 查看亮点；阅读第 10-16 章检查工程质量、测试、部署和答辩材料。

| 要求 | 对应章节 | 覆盖方式 |
| --- | --- | --- |
| R1 Owner | 2、4、5、7、9 | 建任务、搭模板、配审核、看结果、导出 |
| R2 Labeler | 2、6 | 领任务、作答、草稿、提交、看打回、修改 |
| R3 Reviewer | 2、8 | 初审、复审、终审、打回、批量操作 |
| R4 AI Agent 系统账户 | 2、7 | 后台异步流水线，审核记录可追溯 |
| F1.1 任务基础信息 | 4 | 标题、描述、富文本、标签、奖励、截止、配额 |
| F1.2 任务状态机 | 4、11 | 草稿、发布中、已暂停、已结束 |
| F1.3 数据集管理 | 4、10 | JSON/JSONL/Excel、批量编辑、题目预览 |
| F1.4 分发策略 | 4、16 | 先到先得 |
| F2.1 Designer/Renderer 解耦 | 5.1、12 | 搭建与运行分离 |
| F2.2 三栏布局 | 5.1 | 左物料、中画布、右属性 |
| F2.3 JSON Schema 产物 | 5.1 | 可序列化 schema |
| F2.4 同 schema 两端共用 | 5.1 | Owner 预览与 Labeler 作答一致 |
| F2.5 七类物料 | 5.2 | 全部列出并给 schema 示例 |
| F2.6 字段联动 | 5.3、12 | condition、dependsOn |
| F2.7 自定义校验 | 5.3、12 | 必填、长度、正则、validator id |
| F2.8 分组与多 Tab | 5.3 | 布局与错误聚合 |
| F3.1 任务广场 | 6 | 搜索、筛选、任务卡片 |
| F3.2 题目导航 | 6 | 上一题、下一题、跳题 |
| F3.3 草稿自动保存 | 6、12 | Draft Snapshot、防抖保存 |
| F3.4 提交校验 | 6 | 字段级错误与聚合提示 |
| F3.5 题目级 LLM 辅助 | 6、7.7 | LLM 组件与 provenance |
| F3.6 我的数据 | 6 | 已提交、通过、打回、待修改 |
| F4.1 Prompt 与评分维度 | 7.1 | 模板、变量、阈值、维度 |
| F4.2 提交后入队 | 7.2 | outbox 与 worker |
| F4.3 三种结论 | 7.5 | 通过、打回、人工复核 |
| F4.4 结果可查 | 7.6 | AI 评语、原始 Prompt、耗时 |
| F4.5 异步任务队列 | 7.2 | MySQL outbox + 应用层轮询 |
| F4.6 Function Calling | 7.3、16 | 结构化输出，禁止裸文本解析 |
| F4.7 重试与幂等 | 7.4、12 | 幂等键、退避、人工兜底 |
| F5.1 完整审核状态机 | 8.1、11 | 提交、AI 预审、人审、入库 |
| F5.2 AI 打回路径 | 8.1 | 打回修改或人工复核 |
| F5.3 多级审核 | 8.2 | 初审、复审、终审 |
| F5.4 迁移可追溯 | 8.3 | transition log、audit log |
| F5.5 批量操作 | 8.4 | 通过、打回、指派 |
| F5.6 打回理由 | 8.5 | reason_code、comment、field_paths |
| F5.7 上一轮意见 | 8.5 | Labeler 修订可见 |
| F5.8 diff 与 AI 评语 | 8.5 | 第 1/2 轮 diff、AI 评语 |
| F6.1 四种格式 | 9.1 | JSON、JSONL、CSV、Excel |
| F6.2 异步导出 | 9.2 | export_jobs、下载历史 |
| F6.3 字段映射 | 9.3 | 选择字段、重命名、审核记录 |
| T1 Designer/Renderer 解耦 | 5.1、12 | 动态表单核心架构 |
| T2 Schema 版本管理 | 5.4、12 | 不可变 schema version |
| T3 联动与运行时校验 | 5.3、12 | condition 与 validator |
| T4 多级状态机一致性 | 8、11、12 | 审核和导出状态 |
| T5 幂等与并发控制 | 4、7.4、12 | 领取、AI 调用、导出 |
| T6 完整审计日志 | 8.3、10、12 | transition、ledger、audit |
| T7 Prompt 可配置 | 7.1、12 | Prompt 模板和评分维度 |
| T8 结构化输出 | 7.3、12 | Function Calling |
| T9 失败兜底 | 7.4、12 | 重试、转人工 |
| T10 稳定性与可解释性 | 7.6、12 | 温度、trace、reason |
| T11 TypeScript 类型安全 | 3、12 | DTO 与前端类型 |
| T12 大数据量渲染性能 | 12、13 | 分页、懒渲染 |
| T13 草稿与离线友好 | 6、12 | 自动保存、本地暂存 |
| T14 移动端适配 | 12 | 声明取舍和 breakpoint |
| S1 Monorepo 源码 | 3 | 仓库结构 |
| S2 README | 15 | README 大纲 |
| S3 演示视频 | 15 | 5-10 分钟脚本 |
| S4 文档与截图 | 15 | 架构图、技术点、Demo 截图、AI Coding 记录 |
| S5 云部署说明 | 14 | 云部署与环境变量 |
| S6 API 文档 | 15 | OpenAPI 与 Postman Collection |

4 个亮点速览：

- 亮点 1：Schema 版本化与不可变事实建模，嵌入 5.4。
- 亮点 2：Quality Ledger + Verdict 派生视图，嵌入 8.6。
- 亮点 3：Trusted Export 可复现性，嵌入 9.4。
- 亮点 4：AI Provenance 与训练污染防控，嵌入 7.7。

## 1. 项目定位与核心叙事

一句话定位：LabelHub 是 AI 监督信号治理系统，不仅是数据标注平台。
传统标注平台更关注“谁来标、标得快不快”；LabelHub 额外关注“这条监督信号为什么可信、是否被 AI 影响、能否复现、能否追溯到审核证据”。
核心闭环是：数据导入 -> Schema 搭建 -> 标注 -> AI 预审 -> 人工审核 -> 多格式导出。
4 个亮点服务同一条叙事：schema 不污染历史，AI 参与要留痕，审核结论从证据推导，导出文件能复现。
Label Studio 强在通用标注配置，但 schema 演化和导出复现不是核心叙事。
CVAT 强在视觉标注协作，但对 LLM 辅助和训练数据 provenance 支持有限。
Doccano 轻量易用，但复杂动态表单、AI 预审和多级审核链路不足。
LabelHub 不追求做所有标注类型，而是围绕 AI 训练/评测数据的可信生产链路把证据做深。
这是个人项目，目标不是企业平台全功能，而是在答辩范围内跑通完整链路并展示关键工程判断。

## 2. 角色与权限模型

系统明确实现 Owner、Labeler、Reviewer 三种独立角色账户，AI Agent 由后端服务承载，但具备独立系统账户视角。
Owner 负责创建任务、导入数据、搭建模板、配置审核标准、查看结果和发起导出。
Labeler 负责浏览任务广场、领取任务、在线作答、保存草稿、提交、查看打回原因并修改。
Reviewer 负责审核工作台中的初审、复审、终审、打回、通过和批量操作。
AI Agent 以 `system:ai-agent` 写入审核记录，所有评分、结论、prompt、模型和耗时都可追溯。
Owner 不能领取自己发布的任务，避免任务设计者影响数据生产。
Labeler 不能审核自己的提交，避免自审。
Reviewer 默认只能看到审核所需字段、AI 评语、历史意见和 diff，不直接修改原始提交。
终审是人工审核链路中的最高权限，默认用于争议升级；普通任务可启用初审 + 复审两级，满足课题演示和复杂度平衡。
一人开发时可以一人多岗，但演示环境必须用不同账号模拟角色边界。

## 3. 系统架构总览

仓库采用 Monorepo，对齐提交物 S1：`apps/web`、`services/api`、`services/agent`、`packages`、`docs`。
前端 `apps/web` 使用 React 18 + TypeScript，表单内核使用 Formily，拖拽使用 dnd-kit，本地 UI 状态使用 Zustand。
API 层 `services/api` 使用 Java 17 + Spring Boot 3 + MyBatis-Plus（详见 CODEX §2），承载业务规则、状态机、权限、审计、导出和数据访问。
AI Agent `services/agent` 作为独立 worker 进程运行，与 API 共享同一个 MySQL schema 和 `packages/contracts` 类型，负责调用豆包模型（EP 由课题提供），OpenAI 作为 fallback provider，并完成 Function Calling 输出校验和失败重试。
数据层使用 MySQL 保存业务数据，Redis 可用于轻量队列或锁，对象存储保存上传文件和导出文件。
项目采用模块化单体，不拆服务；理由是个人项目更需要端到端闭环、事务清晰和调试效率。
架构图可以画成 5 个盒子：Web 前端、API 单体、AI Worker、Export Worker（均为独立 JVM 进程，基于 Spring Boot）、数据层。
Web 前端包含 Owner Console、Labeler Workbench、Reviewer Desk 三类入口，统一调用 API。
API 单体包含 Task、Schema、Dataset、Session、Review、Export、Audit 七个模块，负责业务规则和状态迁移。
AI Worker 只消费 AI 审核和字段辅助任务，默认调用豆包 API，必要时通过 `LlmProvider` fallback 到 OpenAI 或 fake provider，并把结构化结果写回 API 暴露的审核接口。
Export Worker 读取 export_jobs，生成文件到对象存储，再写入 export_snapshots 和下载历史。
数据层包括 MySQL、Redis 和对象存储；MySQL 保存权威业务事实，Redis 只做短期队列/锁，对象存储只放上传文件和导出文件。
核心数据流是：Owner 发布任务 -> Labeler 领取并提交 -> API 写 submission/outbox -> AI Worker 写 ai_calls -> Reviewer 写 ledger/verdict -> Export Worker 生成 export snapshot。
关键边界：前端不私自推断权威状态，AI Agent 不直接修改最终 verdict，所有状态迁移由 API 层统一校验。
OpenAPI YAML 作为 contract-first 契约源头，前后端类型由 openapi-generator 生成，关键对象的 schema 与实现保持一致。

## 4. 任务管理 · Owner 后台

任务基础信息字段包括：标题、描述、富文本说明、标签、奖励规则、截止时间、配额。
Owner 在任务创建页填写基础信息，保存为草稿；发布前需要绑定数据集、schema version 和审核配置。
任务状态机只保留课题要求的四个状态：草稿、发布中、已暂停、已结束。
迁移规则：草稿可发布；发布中可暂停或结束；已暂停可恢复发布或结束；已结束不可恢复。
迁移条件：发布必须有有效数据集、schema、配额、截止时间和审核规则；暂停/结束必须记录原因。
每次迁移写入 `task_transitions`：from、to、actor_id、reason、created_at。
数据集管理支持 JSON、JSONL、Excel 三种导入。导入流程为上传文件、解析字段、预览前 N 条、确认入库、生成导入报告。
批量编辑支持题目标题、标签、难度、原始字段修正；批量操作保留操作人和时间。
题目预览显示原始数据、ShowItem 映射效果、绑定 schema 后的作答预览。
分发策略选“先到先得”作为主实现，因为它对个人项目最可控，便于演示并发安全，也满足课题三选一要求。
指派和配额抢单只作为未来可扩展策略，不进入本基线的主实现。
领取时 API 使用 task_id + item_id + version 做乐观锁，避免同一题被多人同时领取。
Owner 修改配额、截止时间等 task 当前态字段只影响未来领取行为；已经 claim 成功的 session 会保存 claim 时刻的 task 参数快照，本次作答不受后续 task 修改影响。

关键 API：

```text
POST /tasks
PATCH /tasks/{id}
PATCH /tasks/{id}/transition
POST /tasks/{id}/datasets:import
GET /tasks/{id}/dataset-items
POST /tasks/{id}/publish-check
```

## 5. 标注页面动态搭建 · Designer + Renderer

### 5.1 Designer + Renderer 解耦设计

Designer 只负责生成 JSON Schema，Renderer 负责运行 schema；两者共享同一份 schema，不共享页面内部状态。
Designer 是三栏布局：左侧物料区、中间画布、右侧属性配置面板。
左侧物料用于拖拽添加字段，中间画布用于排序、分组和预览，右侧面板配置标题、默认值、校验、联动和布局。
Owner 预览端和 Labeler 作答端使用同一个 Renderer，因此“搭建时看到的”和“作答时运行的”保持一致。
搭建产物是可序列化 JSON Schema，可保存、版本化、审计和导出。

### 5.2 物料清单

| 物料 | Schema 示例 | 行为 |
| --- | --- | --- |
| 单行输入 / 多行文本 | `{ "type": "text", "multiline": true, "required": true }` | 基础文本采集，支持长度校验 |
| 单选 / 多选 / 标签选择 | `{ "type": "select", "multiple": true, "options": [...] }` | 枚举作答，提交 option value |
| 富文本编辑器 | `{ "type": "richText", "formats": ["bold","link"] }` | 长文本带格式，导出为 HTML 或 JSON |
| 文件 / 图片上传 | `{ "type": "upload", "accept": "image/*", "maxSizeMb": 10 }` | 上传对象存储，提交 storage ref |
| JSON 编辑器 | `{ "type": "json", "schema": {...} }` | 结构化输入，语法与 schema 校验 |
| LLM 交互组件 | `{ "type": "llm", "promptBinding": "p1", "outputTarget": "summary" }` | 字段级模型调用，输出可参考或预填 |
| ShowItem | `{ "type": "showItem", "sourcePath": "$.content" }` | 展示原始数据，不进入提交 payload |

### 5.3 进阶能力

字段联动使用 `condition` 和 `dependsOn` 表达：当某字段满足条件时显示、隐藏或触发额外校验。
运行时只允许安全表达式，不执行任意脚本；Renderer 根据当前 answers 计算可见性。
自定义校验包含必填、长度、正则和注册式自定义函数。自定义函数不把代码存入 schema，只保存 validator id。
布局支持分组容器和多 Tab；提交时错误聚合到 Tab 和字段两层，避免用户不知道哪里错。

### 5.4【亮点 1】Schema 版本化与不可变事实建模

核心主张：Schema Version 发布后不可变，历史 submission 永远绑定当时的 schema_version，避免模板演化污染历史数据。
演示场景：v1 schema 标注 100 条；Owner 发布 v2 schema，新增字段并改校验；历史数据仍用 v1 正确渲染和导出，新任务用 v2；导出文件中两批数据分别携带自己的 schema_version。
数据模型：`schemas(id,name,status)`、`schema_versions(id,schema_id,version,json_schema,status,created_at)`、`submissions(id,schema_version_id,answers_snapshot)`。
关键 API：`POST /schemas`、`POST /schemas/{id}/versions`、`POST /schemas/{id}/versions/{version}:publish`、`GET /submissions/{id}/render-schema`。
为什么是亮点：很多轻量标注工具把当前模板当作唯一事实，模板变更后历史数据解释困难；LabelHub 用局部不可变快照保护训练数据语义。
技术难点：field stable id 必须和显示名分离；导出不能按字段名取值，而要按 stable id 映射；Renderer 要能加载旧 schema。

## 6. 标注员工作台

任务广场提供搜索、标签筛选、状态筛选、奖励和截止时间排序，任务卡片展示剩余配额、预计题量和领取按钮。
领取接口检查任务发布状态、配额、截止时间和用户资格；成功后创建 session。
作答页基于 schema 渲染表单，左侧或顶部展示题目列表，支持上一题、下一题、跳题和进度展示。
草稿自动保存采用字段变更后防抖保存，同时每 N 秒兜底保存一次。
Draft Snapshot 记录 session_id、item_id、answers、schema_version_id、updated_at；刷新页面后恢复。
提交校验分为前端即时校验和后端最终校验；错误提示定位到字段，并在页面顶部做错误聚合。
题目级 LLM 辅助复用第 5 章 LLM 组件，调用结果必须带 provenance 标记，用户可采纳、修改或忽略。
我的数据分为已提交、通过、打回、待修改四栏统计与列表。
打回项展示上一轮审核意见、AI 评语、diff 入口和继续修改按钮。

关键 API：

```text
GET /tasks/marketplace
POST /tasks/{id}/claim
GET /sessions/{id}
PUT /sessions/{id}/draft
POST /sessions/{id}/submit
GET /me/contributions
```

## 7. AI 自动预审 Agent

### 7.1 可配置审核 Prompt 与评分维度

Owner 在审核规则页配置 Prompt 模板、评分维度、阈值和结论策略。
默认维度包括相关性、准确性、格式合规和安全性；Owner 可以新增维度。
Prompt 支持变量插值：提交内容、schema、字段值、题目原文和任务说明。
每次配置保存为 prompt_version，提交审核时绑定固定版本。

### 7.2 异步处理流水线

Labeler 提交后 API 在同一事务中写 submission 和 outbox 记录。
AI Worker 轮询 MySQL outbox + 应用层轮询队列（详见 CODEX §6），取任务、调用 LLM、校验输出并写回 AI 审核结果。
异步状态包括待审核、进行中、已完成、失败，审核工作台能看到当前状态。

### 7.3 Function Calling 结构化输出

AI 调用不解析裸文本，而是使用 Function Calling 强制返回结构化结果。

```json
{
  "scores": { "relevance": 0.9, "accuracy": 0.8, "format": 1, "safety": 1 },
  "verdict": "pass",
  "reason": "答案覆盖关键事实，格式符合 schema"
}
```

输出校验失败会重试；超过上限后进入人工复核。

### 7.4 失败重试与幂等

幂等键为 `submission_id + prompt_version + model_version`。
重试采用指数退避，最多 3 次；超过后写入 failed 状态并转人工。
同一幂等键重复消费时直接返回已有结果，避免重复收费和重复写入。

### 7.5 三种结论

AI 预审输出通过、打回、人工复核三种结论。
通过表示分数达到阈值且无高危问题；打回表示明显不合格；人工复核表示分数临界、输出不稳定或规则冲突。
AI 结论只进入审核证据，不直接跳过人工审核。

### 7.6 审核结果落库与展示

AI 评语、原始 Prompt、模型、token 消耗、耗时、输入 hash 和输出 JSON 都保存到 `ai_calls`。
审核工作台展示维度分、评语、原始 Prompt、失败重试记录和最终结构化输出。

### 7.7【亮点 4】AI Provenance 与训练污染防控

核心主张：AI 参与标注或审核时必须留下 provenance，下游导出可以区分纯人工数据和 AI 辅助数据。
演示场景：某字段由 LLM 辅助生成，Labeler 修改后提交，Reviewer 采纳；导出时选择“纯人工版”会排除该字段，选择“AI 辅助版”会保留字段并带 trace。
数据模型：`ai_calls(id,prompt_version,model,input_hash,output_json,tokens,cost_ms,status)`；submission 字段保存 `{ ai_assisted, source, confidence, reviewed }`。
字段级 provenance 同时保留历史链：`ai_calls_in_field(submission_id,field_path,ai_call_id,accepted,user_modified_after,ordinal)`，记录“AI 生成 -> 人工修改 -> 再调用 AI -> 再修改”的顺序。
导出纯人工版时排除任何 `accepted=true` 的 AI 辅助字段；导出全 trace 版时保留每个字段的 ai_call_id 和人工修改标记。
关键 API：`POST /ai/field-assist`、`GET /ai/calls/{id}`、`GET /submissions/{id}/provenance`、`POST /exports`。
为什么是亮点：AI 训练数据可能被 AI 输出反向污染，导致下游无法判断数据来源；provenance 让数据消费者知道哪些信号被 AI 影响过。
技术难点：字段级 provenance 要随草稿、提交、审核和导出流转；人工修改 AI 输出后，要记录从 AI 生成到人工采纳的关系。

## 8. 多角色人工审核流转

### 8.1 工作流状态机

AI 预审是人工审核的前置环节，人工审核内部支持初审、复审、终审三级。
默认演示启用初审 + 复审；终审作为争议升级路径，既覆盖 PDF 流程图，也覆盖多级审核要求。
PDF 4.5 流程图里的“人工复审”指相对 AI 预审的整体人工审核环节；本文 F5.3 的“初审/复审/终审”指人工审核内部的三级。
因此系统状态机表达为：AI 预审 -> 人工审核环节（内部初审 -> 复审 -> 终审，可配置启用其中几级）-> 入库/可导出。

```text
[Labeler 提交] -> [AI 预审]
  ├─ AI 建议通过 -> [人工初审] -> [人工复审] -> [入库 / 可导出]
  ├─ AI 建议打回 -> [Labeler 修改] 或 [人工复核]
  └─ AI 建议人工复核 -> [人工初审]
```

状态字段记录主状态和人工审核子状态，不变量是：未完成 AI 预审不得进入人工初审，未通过人工复审不得导出。

### 8.2 多级审核

初审关注格式、完整性和明显错误；复审关注业务准确性和 AI 评语；终审处理争议、申诉和关键样本。
Reviewer 只能执行当前级别允许的动作：通过、打回、转上级、领取审核。
终审权限与普通 Reviewer 分离，演示时用独立账号模拟。

### 8.3 审计与追溯

所有状态迁移写入 transition log：from、to、actor、reason、timestamp、source。
Audit Log append-only，用于回答“谁在什么时候基于什么理由改变了状态”。
AI Agent 的动作也以系统账户写入审计链。

### 8.4 批量操作

审核员可批量通过、批量打回、批量指派。
批量操作必须先展示影响数量和样本摘要，提交后为每条 submission 写入独立审计记录。
批量打回必须填写结构化理由和自由文本说明。

### 8.5 打回理由与上一轮意见

打回理由包含 reason_code、comment、field_paths、review_round。
Labeler 修订时看到上一轮所有意见、AI 评语和第 1/2 轮 diff。
diff 视图比较 answers_snapshot，按字段展示新增、删除、修改和 Reviewer 评论。

### 8.6【亮点 2】Quality Ledger + Verdict 派生视图

核心主张：数据库不只存最终结论，而是存 AI 审、人审、打回、复审等 evidence，Current Verdict 从 ledger 派生。
演示场景：同一条 submission 在 rule v1 下 accepted；升级到 rule v2 后不改 ledger，重新派生 verdict 为 needs_arbitration，并能看到原因来自新规则。
数据模型：`quality_ledger_entries(id,submission_id,actor_type,action,scores,payload,created_at)`、`adjudication_rules(id,version,rule_json)`、`current_verdicts(submission_id,rule_version,verdict)`。
关键 API：`POST /reviews/{submissionId}/actions`、`GET /submissions/{id}/ledger`、`POST /adjudication-rules/{id}:recompute`。
为什么是亮点：训练数据质量会随规则演化变化；存证据而非只存结论，让历史可重算、可解释。
派生策略：`current_verdicts` 是缓存视图，rule 升级后写入 outbox，由 worker 按 task 分批重算并写回；重算期间读旧值，完成后切换到新 rule_version。
单条 submission 也支持 lazy 派生：读取时发现 current_verdicts 的 rule_version 落后，就即时重算该条并刷新缓存。
技术难点：ledger append-only 要避免“修改历史”；verdict 派生函数必须确定性、可测试，并能解释每个 verdict 的输入证据。

## 9. 多格式数据导出

### 9.1 四种导出格式

JSON 保留嵌套结构，适合程序消费；JSONL 一行一条，适合训练流水线。
CSV 使用扁平列名，嵌套字段用点号展开，字符串按 RFC 转义。
Excel 使用 xlsx 库生成工作簿，主表放 answers，附表放审核记录和 provenance。

### 9.2 异步导出与下载历史

导出通过 `export_jobs` 异步执行，状态为 created、queued、running、completed、failed、revoked。
下载历史展示导出时间、参数、状态、文件大小、下载次数、创建人和失败原因。
导出失败保留错误信息，允许按同一参数重试。

### 9.3 字段映射可配置

Owner 可选择导出字段、重命名列名、配置嵌套展开方式。
可选是否包含审核记录：verdict、reviewer、AI 分数、打回理由。
可选是否包含 AI provenance trace，支持纯人工版、AI 辅助版、全 trace 版。

### 9.4【亮点 3】Trusted Export 可复现性

核心主张：每次导出生成 immutable Export Snapshot，记录文件 hash、schema_version、rule_version、数据范围和字段映射快照。
演示场景：同一任务导出两次，中间调整 verdict rule；两次 file hash 不同，系统能定位差异来自 rule v1 到 v2，并列出影响的 N 条记录。
数据模型：`export_snapshots(id,job_id,file_hash,schema_versions,rule_version,field_mapping_snapshot,record_count,created_at)`。
关键 API：`POST /exports`、`GET /exports/{id}`、`GET /exports/{id}/download`、`POST /exports/{id}:diff`。
为什么是亮点：训练流水线常见问题是模型效果变了却不知道数据集哪里变了；Export Snapshot 把差异归因到 schema、rule 或字段映射。
技术难点：导出时必须冻结查询范围、字段映射和 verdict rule；文件 hash 要基于稳定排序生成，避免同数据不同 hash。
hash 计算前先做规范化：字段按 key 字母序、时间戳统一 UTC ISO 8601、浮点数固定 6 位、记录按 submission_id 升序、CSV 统一 LF 换行。

## 10. 数据模型总览

`users / roles`：保存账号、角色和演示权限；索引 email、role。
`tasks`：标题、描述、富文本说明、标签、奖励、截止、配额、状态、owner_id。
`task_transitions`：task_id、from_status、to_status、actor_id、reason、created_at；append-only。
`datasets / dataset_items`：导入文件、解析状态、原始题目、批量编辑字段；索引 task_id、batch_id。
`schemas / schema_versions`：schema 主体与不可变版本；`schema_versions.json_schema` 保存发布快照。
`sessions`：labeler_id、task_id、item_id、status、schema_version_id、claim_version；领取并发控制。
`drafts`：session_id、answers_json、schema_version_id、updated_at；允许覆盖当前草稿。
`submissions`：session_id、schema_version_id、answers_snapshot、status、submitted_at；正式提交后 immutable。
`ai_calls`：submission_id、prompt_version、model、input_hash、output_json、tokens、duration_ms、status。
`quality_ledger_entries`：submission_id、actor_type、action、scores、payload、created_at；append-only。
`adjudication_rules`：version、rule_json、status；用于派生 current verdict。
`review_actions`：submission_id、review_round、level、reason_code、comment、field_paths。
`export_jobs`：format、status、params、created_by、started_at、finished_at、error。
`export_snapshots`：job_id、file_hash、schema_versions、rule_version、field_mapping_snapshot、object_key。
`audit_logs`：actor_id、action、target_type、target_id、payload_hash、created_at；append-only。
`outbox`：event_type、payload、idempotency_key、status、retry_count、next_run_at。
不变量：submission、ledger、export snapshot、audit log 不原地改；task、session、draft 是当前态对象，可按业务规则更新。

## 11. 关键状态机

Task 状态机：

| 当前状态 | 可迁出状态 | Guard 条件 | 触发者 |
| --- | --- | --- | --- |
| 草稿 | 发布中 | 已绑定数据集、schema、审核规则、配额、截止时间 | Owner |
| 发布中 | 已暂停 | 提供暂停原因，不影响已 claim session | Owner |
| 发布中 | 已结束 | 截止、配额耗尽或 Owner 手动结束 | Owner / System |
| 已暂停 | 发布中、已结束 | 恢复时重新校验截止时间和配额 | Owner |
| 已结束 | 无 | 终态，不允许恢复 | System |

Submission 状态机：

| 当前状态 | 可迁出状态 | Guard 条件 | 触发者 |
| --- | --- | --- | --- |
| created | under_ai_review | 正式提交成功，outbox 写入成功 | API |
| under_ai_review | under_human_review、rejected | AI 结构化输出完成或失败转人工 | AI Agent / API |
| under_human_review | accepted、rejected、superseded | 人工审核级别满足任务配置 | Reviewer |
| rejected | created | Labeler 基于打回意见修订并重新提交 | Labeler |
| accepted | superseded | 后续修订或 rule 重算生成新 verdict | System |

Export Job 状态机：

| 当前状态 | 可迁出状态 | Guard 条件 | 触发者 |
| --- | --- | --- | --- |
| created | queued | 参数校验通过，字段映射和格式合法 | Owner |
| queued | running | Worker 获取任务并加锁 | Export Worker |
| running | completed、failed | 文件生成成功或失败 | Export Worker |
| completed | revoked | 发现导出错误或需要撤销下载入口 | Owner / Reviewer |
| failed | queued | 保留失败原因后允许重试 | Owner |

状态机不变量：所有迁移都必须通过后端 guard；所有失败都要给用户可读原因；所有关键迁移都写审计。
人工审核子状态 `initial_review / second_review / final_review` 只存在于 `under_human_review` 内部，AI 预审不算人工级别。

## 12. 技术挑战与实现要点

T1 Designer/Renderer 解耦：Designer 只产出 JSON Schema，Renderer 只消费 JSON Schema，两端不能各自维护一份表单逻辑。
同一份 schema 支撑 Owner 预览、Labeler 作答、历史 submission 重渲染。
T2 Schema 版本管理：发布后的 schema_version 不可改，历史 submission 绑定旧版本。
Owner 修改模板时只能生成新版本，避免新校验规则反向污染旧数据。
T3 字段联动与运行时校验：`condition + dependsOn` 负责条件显示，`required / min / max / regex / validator_id` 负责校验。
Renderer 不执行用户提交的任意代码，自定义函数通过注册 id 调用。
T4 多级状态机事务一致性：Task、Submission、人工审核子状态、Export Job 都有 guard 条件和触发者。
前端只发起 command，能否迁移由后端状态机决定。
T5 幂等与并发控制：任务领取用 task_id + item_id + version；AI 调用用 submission_id + prompt_version + model_version；导出用 export 参数 hash。
重复请求返回已有结果，不重复写事实。
T6 完整审计日志：业务状态迁移写 audit_logs，审核证据写 quality_ledger_entries。
这样普通操作和质量证据都能追溯，但不会把所有对象都变成复杂事件流。
T7 Prompt 工程可配置：Owner 配置 Prompt 模板、变量和评分维度，保存为 prompt_version。
AI 调用绑定固定版本，后续追溯时能看到当时使用的规则。
T8 结构化输出：Function Calling 输出固定 JSON，后端按 schema 校验。
校验失败不猜测文本含义，而是重试或转人工。
T9 失败兜底与人工介入：AI 超时、输出不合法、重试耗尽时，submission 进入人工复核。
用户看到的是“待人工复核”，不是一个静默失败的后台任务。
T10 评分稳定性与可解释性：温度参数设为 0，固定 prompt_version、结构化 schema 和 input_hash。
审核工作台展示 prompt、维度分、reason、原始输出和重试记录，便于解释。
T11 TypeScript 类型安全：API DTO、schema 配置、Renderer props、导出参数都要有类型。
OpenAPI 生成类型是主路径，避免前后端用 `any` 临时对接。
T12 大数据量渲染性能：任务广场和审核列表使用分页或虚拟列表；复杂表单按 Tab 懒渲染。
目标是在 1280x800 和 1920x1080 下关键路径不卡顿。
T13 草稿自动保存与离线友好：字段变更防抖 + 定时兜底。
网络异常时先写浏览器本地缓存，恢复网络后带 schema_version 同步，避免旧 schema 草稿误提交。
T14 移动端适配取舍：移动端不是本期主演示路径，但布局保留响应式 breakpoint。
移动端保证可查看任务、填写基础表单和提交，不承担完整拖拽搭建体验。

## 13. 测试策略

Unit 测试覆盖 schema lint、字段联动求值、状态迁移 guard、verdict 派生函数。
Integration 测试覆盖任务创建、数据集导入、并发领取、提交后入队、AI 回写、导出生成。
E2E 测试使用 Playwright 覆盖 Owner、Labeler、Reviewer 三大角色完整链路。
亮点 1 测试：v1/v2 schema 同时存在，旧 submission 仍按 v1 渲染。
亮点 2 测试：ledger 不变，切换 rule 后 current verdict 可重新派生。
亮点 3 测试：相同导出参数生成稳定 file hash，rule 改变后 diff 可解释。
亮点 4 测试：AI 辅助字段带 provenance，导出纯人工版时可排除。
核心模块测试覆盖率目标大于 70%，优先覆盖状态机、schema、审核和导出。

## 14. 部署与运维

本地启动使用 docker-compose，包含 MySQL、Redis、API、Agent、Web 和对象存储模拟服务。
最小启动检查清单：Web 首页可打开；API `/health` 返回 OK；MySQL migration 已执行；AI Worker 能消费一条 fake job；Export Worker 能生成一个测试文件。
docker-compose 需要暴露 Web、API、MySQL、Redis、对象存储管理页五类端口，README 中列出默认账号和重置方式。
数据库迁移使用 Flyway，迁移文件位于 `services/api/src/main/resources/db/migration/`，演示环境启动时自动执行 migration。
环境变量分为五组：数据库、Redis、对象存储、LLM 模型、认证密钥。
关键变量包括：`DATABASE_URL`、`REDIS_URL`、`OBJECT_STORAGE_ENDPOINT`、`OBJECT_STORAGE_BUCKET`、`DOUBAO_API_KEY`、`DOUBAO_ENDPOINT`、`OPENAI_API_KEY`（fallback）、`JWT_SECRET`。
LLM 凭证默认使用课题提供的豆包 EP 和 APIKEY，OpenAI API Key 仅作为 fallback；所有密钥禁止写入仓库，本地 `.env.example` 只放变量名和说明。
云部署建议：前端部署到 Vercel，后端和 worker 部署到 Railway 或 Render，数据库使用托管 MySQL，对象存储使用兼容 S3 的服务。
云部署图可以画成：Vercel Web -> API Service -> MySQL/Object Storage；AI Worker 和 Export Worker 与 API 共用数据库和对象存储。
演示环境需要准备三类测试账号：Owner、Labeler、Reviewer；并提供一套可恢复的 demo seed 数据。
运维页面至少展示 AI 队列失败任务、导出失败任务、最近审计记录和最近 10 次状态迁移。
失败恢复策略保持简单：AI 失败任务可重试或转人工，导出失败任务可按原参数重试，误导出可 revoked。
启动检查脚本建议输出 5 个结果：数据库连接、迁移版本、AI fake call、导出测试文件、Web 静态资源访问。
演示环境每天重置 demo seed，避免前一天的审核状态影响答辩流程。
部署说明中要写清楚“无真实敏感数据”，演示文件只使用样例数据，避免评审担心隐私问题。
默认调用豆包；豆包不可用时按 `LlmProvider` 抽象切换到 OpenAI 或 fake provider，仍展示 Function Calling 输出结构和人工兜底路径。
启动验收以“Owner 发布、Labeler 提交、Reviewer 通过、导出下载”四步全链路跑通为准。

## 15. 提交物清单与答辩准备

README 大纲：项目定位、架构图、模块划分、本地启动、环境变量、核心 API、4 个亮点、关键取舍、已知限制。
README 的“快速开始”必须做到 10 分钟内能跑起来：安装依赖、复制 `.env.example`、启动 docker-compose、执行 migration、启动 Web/API/Worker。
演示视频 0:00-1:00：展示首页和架构图，说明 LabelHub 不是单纯标注平台，而是监督信号治理系统。
演示视频 1:00-2:00：切到 Owner 任务管理页，点击新建任务，填写标题、富文本说明、标签、奖励、截止和配额，保存草稿。
演示视频 2:00-3:00：进入模板搭建页，拖拽文本、选择、ShowItem、LLM 组件，配置联动和校验，发布 schema version。
演示视频 3:00-4:00：导入 JSONL 或 Excel 数据，预览题目，发布任务，展示先到先得的剩余配额。
演示视频 4:00-5:00：切到 Labeler 账号，进入任务广场领取任务，在标注台上一题/下一题/跳题，触发草稿自动保存。
演示视频 5:00-6:00：调用题目级 LLM 辅助，修改 AI 建议后提交，展示提交校验和 provenance 标记。
演示视频 6:00-7:00：切到 Reviewer 账号，查看 AI 预审结果、原始 Prompt、维度评分，执行初审/复审和打回。
演示视频 7:00-8:00：Labeler 查看打回原因、上一轮意见和 diff，修订后再次提交，Reviewer 通过。
演示视频 8:00-9:00：演示 4 个亮点：schema v1/v2 历史渲染、rule 重派生 verdict、导出 diff、AI provenance 导出选项。
演示视频 9:00-10:00：进入导出中心生成 JSONL/CSV/Excel，展示下载历史、file hash 和导出参数快照。
API 文档由 OpenAPI 生成，并导出 Postman Collection；答辩材料中至少包含任务、schema、session、review、export 五组接口。
AI Coding 过程记录包含关键 prompt、设计取舍、问题修复、测试记录和截图，用来回答“为什么这样设计”。
Demo 截图清单：任务管理、模板搭建、数据导入、任务广场、标注台、我的数据、AI 预审、审核工作台、导出中心。
每张截图都要显示关键状态：任务状态、schema_version、草稿状态、AI 评分、审核意见、verdict、export hash。
云演示说明文档包含访问地址、测试账号、启动状态、已知限制、重置 demo 数据的方式。
提交目录建议包含：`README.md`、`docs/architecture`、`docs/api`、`docs/demo-script.md`、`docs/screenshots`、`docs/ai-coding-log.md`。
架构图至少准备 3 张：系统总览图、端到端业务流图、核心数据模型图。
关键技术点文档至少准备 4 页：动态表单、AI 预审、人工审核状态机、导出可复现。
截图命名采用 `01-owner-task-console.png` 这类顺序前缀，方便评审按视频脚本对照。
API 文档需要包含请求示例和响应示例，不能只有 path 列表。

## 16. ADR 列表

ADR-001：采用模块化单体而非拆服务，优先保证个人项目的调试效率和端到端一致性。
ADR-002：Schema Version + Field Stable ID 发布后不可变，解决模板演化污染历史数据的问题。
ADR-003：Quality Ledger append-only，Current Verdict 作为派生视图，保留审核证据而非只存最终结论。
ADR-004：Export Snapshot immutable + file hash，保证导出文件可复现、可 diff。
ADR-005：AI 调用进入 ledger 或 provenance，不直接修改最终 verdict。
ADR-006：Function Calling 强制结构化输出，禁止裸文本解析。
ADR-007：分发策略选择先到先得，因为并发边界清晰、演示稳定，并满足课题任选其一。
ADR-008：采用 MySQL outbox + 应用层轮询保障 submission 写入和 AI 任务创建最终一致，队列方案本期固化，不引入 BullMQ。
ADR-009：差异化设计采用局部不可变，而不是全量 Event Sourcing；只有 schema、submission、quality ledger、export snapshot 四类关键证据对象 immutable，task、session、user 仍是普通当前态对象。这样把复杂度限制在监督信号可信链路上，避免为了概念牺牲迭代速度。
ADR-010：后端采用 Spring Boot 3 + MyBatis-Plus + Java 17；理由是 Java 生态对 JSON 字段处理友好（MyBatis-Plus JacksonTypeHandler），append-only 模式天然适配，并与课题“后端语言任选、Java 可选”的建议一致。
ADR-011：LLM 默认使用课题提供的豆包资源，OpenAI 作为 fallback；通过 `LlmProvider` 接口抽象供应商切换。
ADR-012：OpenAPI 采用 contract-first，YAML 为契约源头，前后端类型由 openapi-generator 生成。
ADR-013：前端采用 Feature-Sliced Design 简化版目录结构，保证页面、业务动作、领域对象和共享能力边界清晰。

## 17. 未来扩展

本期不展开组织级权限和大规模数据治理能力，后续可在不改变主链路的前提下增强权限粒度、标注员画像、模型调用预算、超大文件导出、历史重算和实验分组。
这些能力只作为后续方向，不影响本次答辩的硬性功能范围和演示闭环。
