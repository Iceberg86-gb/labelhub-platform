# LabelHub Platform

LabelHub 是一个面向数据标注全流程的可信证据平台,核心治理理念是 **「AI 辅助 + 人类问责」**:AI 预审产出结构化证据与完整 provenance,但最终质量裁决始终由人类审核者负责;所有关键事实(Schema 版本、提交、审核、AI 调用、导出)以 append-only 方式落库,使标注结果成为**可审计、可复现的数据资产**,而不是可变的数据库快照。

## 系统架构

整体采用 **模块化单体 + 独立 AI Worker** 的双进程架构,契约先行(contract-first):

```
                       packages/contracts/openapi/labelhub.yaml
                      (API 唯一源头, 87 operations, v0.10.0)
                        ↓ openapi-typescript        ↓ 人工对齐
┌─────────────────┐   ┌──────────────────────┐   ┌──────────────────────┐
│   apps/web      │   │    services/api      │   │   services/agent     │
│ React 18 + TS   │──→│  Spring Boot 单体    │   │   AI Worker 进程     │
│ Vite + Semi     │   │  13 个业务模块       │   │  轮询 outbox 表      │
│ Formily 渲染器  │   │  业务权威边界        │   │  AI 预审 / 导出执行  │
└─────────────────┘   └──────────┬───────────┘   └──────────┬───────────┘
                                 │      MySQL 8 (Flyway ×29)│
                                 │   ┌── outbox 事件表 ←────┘
                                 ├───┤   append-only 事实表群
                                 │   └── (schema_versions / submissions /
                                 │        quality_ledger_entries / ai_calls /
                                 │        export_snapshots / senior_review_cases …)
                                 ├─── Redis 7 (预留)
                                 └─── MinIO (S3 兼容, Trusted Export 产物)
```

关键链路:

- **请求边界**:所有业务写入只经过 `services/api`。API 在同一事务内原子写入业务状态 + outbox 事件(ADR-008),不依赖 Kafka 等外部队列。
- **异步边界**:`services/agent` 轮询 outbox,消费 AI 预审与导出作业。Worker 具备幂等键、重试计数、`last_error` 记录与死信路径;配置类失败(密钥缺失、解密失败、鉴权失败)**不会静默回退**到环境变量配置,必须在重试/死信链路上可见(ADR-011 修订版)。
- **LLM 接入**:registry-first 解析——任务 Owner 在平台内配置的 LLM Provider(密钥经 `LABELHUB_LLM_PROVIDER_MASTER_KEY` 加密存库)优先;无配置时回退 env;多个启用配置视为配置错误而非任选其一。AI 预审强制 function-calling 结构化输出,纯文本解析不被接受(ADR-006)。
- **契约同步**:前端每次 dev/build/typecheck 自动执行 `gen:api`,从 OpenAPI YAML 生成 TypeScript 类型;生成物不是修改源头。

## 模块划分

### 后端 `services/api`(`com.labelhub.api.module.*`)

| 模块 | 职责 |
|------|------|
| `auth` / `user` | JWT 认证、refresh token、注册、RBAC 角色授予 |
| `task` | 任务生命周期、状态迁移事实(`task_transitions`)、配额派生 |
| `schema` | Schema Designer 后端、append-only 版本化、JSON 运行时校验、模板库 |
| `dataset` | JSON/JSONL 导入事实、`dataset_items`、当前数据集绑定 |
| `session` / `submission` | 领取(claim-time schema binding)、草稿自动保存、提交、历史渲染 |
| `quality` | Quality Ledger append-only 写入、Verdict 实时派生、`senior_review_cases` 仲裁工作台 |
| `ai` | AI 预审编排、prompt 版本、三区阈值规则、provenance、幂等复用、LLM provider 配置 |
| `export` | Trusted Export canonical 产物、多训练格式(表格快照 / OpenAI 微调 / TRL SFT / TRL 偏好)、快照 diff |
| `outbox` | 事务性事件出箱 |
| `platform` | 平台管理员视角:成本、效率、人力计量仪表盘 |
| `admin` | 审计日志(payload hash)、用户管理 |
| `shared` / `security` | canonical JSON 序列化、统一异常、安全过滤链 |

### AI Worker `services/agent`

独立 Spring 进程:`OutboxAiReviewWorker` / `OutboxExportWorker` 轮询消费;`RuntimeProviderResolver` 实现 registry-first provider 解析与本地密钥解密;`AgentSecretRedactor` 保证敏感凭据不进入日志(已用 canary 注入法验证零泄漏)。

### 前端 `apps/web`(Feature-Sliced Design,ADR-013)

```
src/
├── app/        应用骨架、路由、鉴权守卫
├── pages/      按角色分区: owner / labeler / reviewer / platform / admin …
├── features/   16 个特性切片: schema-design, labeling, quality, export, ai, …
├── entities/   领域实体
└── shared/     生成的 API 类型、通用 UI、工具
```

核心技术:Semi Design 组件库、Formily 驱动的 Schema Designer/Renderer(支持联动 DSL、富文本、附件链、虚拟化长表单)、TanStack Query/Virtual、dnd-kit。Labeler 全链路已完成移动端适配。

### 其他目录

- `packages/contracts` — OpenAPI 契约源头 + fixtures
- `infra` — 本地 / demo / 生产三套 docker compose,MySQL 初始化、MinIO bucket 初始化、nginx
- `scripts` — `dev-up.sh`、`deploy-api.sh` / `deploy-web.sh`(四阶段生产部署)、受保护端点检查
- `docs` — 完整设计基线、15 份 ADR、四张架构图、各里程碑验收清单、截图证据索引
- `humanpending.md` — **append-only 审计台账**,259 个已封闭批次的交付与验证记录
- `submission` — 答辩提交材料索引

## 角色与核心流程

五种角色,职责正交:

1. **Owner**:创建任务 → 导入数据集(配额由数据集题目数派生,不再手填)→ Designer 设计并发布 Schema 版本 → 发布任务 → 触发 AI 预审 → 创建 Trusted Export。
2. **Labeler**:任务广场领取(FCFS + 乐观锁,ADR-007)→ Renderer 按领取时绑定的 Schema 版本作答 → 自动保存 → 支持自定义数量批量领取与本批次批量提交。
3. **Reviewer**:全量初审队列 → 单条标注 approve/reject → Verdict 从 latest ledger entry 实时派生 → 疑难可标记升级。
4. **Senior Reviewer**:不做二次全量审核,而是基于独立 `senior_review_cases` 的**仲裁工作台**——处理 AI 升级、Reviewer 疑难标记与抽检 case。
5. **Platform Admin**:LLM Provider 接入、成本/效率/人力计量仪表盘、用户与角色管理、审计日志。

## 本地启动指引

### 前置依赖

- JDK 17(Makefile 会自动探测并强校验)
- Node + pnpm(workspace 由 `pnpm-workspace.yaml` 管理)
- Docker(MySQL 8 / Redis 7 / MinIO)

### 快速启动

```bash
# 0. 环境自检
make doctor

# 1. 启动基础设施(MySQL + Redis + MinIO,自动建 bucket)
make dev-up

# 2. 启动后端(自动确保 labelhub_test 测试库存在;默认 context path 为 /api)
make dev-api
# 或手动: mvn -pl services/api spring-boot:run
# 端口冲突时: LABELHUB_API_PORT=18080 mvn -pl services/api spring-boot:run

# 3. 启动前端(自动从 OpenAPI 生成 TS 类型)
pnpm install
pnpm --filter @labelhub/web dev
# 后端跑在 18080 时:
# LABELHUB_API_PROXY_TARGET=http://127.0.0.1:18080 pnpm --filter @labelhub/web dev
```

浏览器访问 `http://127.0.0.1:5173`。环境变量样例见 `.env.example`(JWT 密钥、数据库、对象存储、LLM provider 等)。

Demo 账号(密码均为 `demo1234`):`owner_demo` / `labeler_demo` / `reviewer_demo` / `senior_reviewer_demo`。新增演示账号请走注册页创建,不要手工 SQL INSERT(避免 hash 转义与 id 冲突问题)。

### 常用验证

```bash
make verify                                  # 后端测试(自动准备测试库 + JDK17)
make migrate-check                           # 仅验证 Flyway 迁移 + 上下文启动
pnpm --filter @labelhub/web test             # 前端测试(当前 69 文件 / 338 条)
pnpm --filter @labelhub/web typecheck
pnpm --filter @labelhub/web build
bash scripts/check-protected-endpoints.sh    # 受保护端点鉴权检查
pnpm --filter @labelhub/web bench            # Formily vs legacy 渲染基准
```

### 生产部署

`scripts/deploy-api.sh` / `deploy-web.sh` 实现四阶段闭环:rsync 同步(exclude 模式必须锚定,如 `--exclude=/submission`)→ 远端构建 → 带 `--env-file .env.prod` 重启 → 健康探针自检。生产编排见 `infra/docker-compose.prod.yml`(含内存限额与健康检查)。生产命令仅由项目 Owner 执行。

## 关键设计取舍

以下取舍均有 ADR 或 `humanpending.md` 闭环记录支撑:

**1. 模块化单体,而非微服务(ADR-001)。** 毕设项目需要清晰边界而非部署复杂度。13 个业务模块以包边界隔离、单进程部署、单一数据库;唯一拆出的进程是 AI Worker——因为它消费异步事件、可独立失败,不应拖垮请求处理。

**2. Append-only 事实流,而非可变状态(ADR-002/003/009)。** 已发布 Schema 版本不可变,字段 stableId 发布后不可复用;每个 submission 绑定提交时的 Schema 版本,历史提交永远能按原貌渲染。Quality Ledger 只追加不更新,Verdict(`pending/approved/rejected`)从 latest entry **实时派生**而非维护物化表——用读时计算换取写路径的绝对简单与事实不可篡改。

**3. AI 是证据,不是裁决(ADR-005)。** AI 预审结果(含 prompt 版本、模型、input/output hash、cost、latency)作为 provenance 写入 ledger,可被检视、导出或排除,但不直接改写最终 verdict。人类审核者是训练数据准入的问责闸门。配套约束:AI 输出必须经 function-calling 结构化(ADR-006),校验失败走确定性的重试/转人工路径——这也是选 deepseek-chat 而非不支持 `tool_choice` 的变体的原因。

**4. Outbox 轮询,而非消息队列(ADR-008)。** MySQL outbox 表 + 轮询 worker,让 API 事务能原子地写业务状态和事件,Stage 1 无需引入 Kafka。代价是 worker 必须自带幂等、重试与死信;收益是部署面缩到 2C4G 单机可承载。

**5. Provider 解析拒绝静默回退(ADR-011 修订)。** DB 配置的 provider 出现永久性配置错误时,agent 不会悄悄退回 env 配置"让它先跑起来",而是让失败在重试/死信链路上显式可见——隐藏配置错误比短暂不可用更危险。

**6. 契约先行 + 漂移控制(ADR-012/015)。** OpenAPI YAML 是唯一 API 源头,前端类型全部生成,契约 MD5 与迁移数作为每个批次的硬变更锚点,杜绝前后端口径漂移。

**7. Formily 换可扩展性,接受首屏开销(M7-P2 基准)。** 5000 字段首屏 Formily 约 96ms vs legacy 52ms,但 500 字段表单单字段变更时 legacy 重跑全部 500 个字段分支,Formily 只触发 1 次回调。标注场景以高频局部编辑为主,精确响应式 + 虚拟化是正确的取舍。

**8. Senior Reviewer 正交化(闭环 258)。** 高级审核从"二次全量 approve/reject"重构为独立 `senior_review_cases` 承载的仲裁工作台,只处理 AI 升级、疑难标记与抽检——避免两层审核职责重叠导致问责模糊。

**9. 配额语义收敛(闭环 259)。** 任务配额不再由 Owner 手填,改为绑定数据集后由题目数派生,领取按实际可用 dataset item 控制——消除配额字段与真实题量脱节这一整类不一致。

**10. Scoped Zero Pause(ADR-014)。** 无法安全推断的业务规则不阻塞无关开发,而是作为 scoped 条目记入根目录 `humanpending.md`;该文件同时是 append-only 的批次审计台账,每个合入批次留下交付与验证记录。配套流程纪律:FCFS 领取用乐观锁守护配额(ADR-007)、所有合并 `--no-ff`、单元测试通过不等于功能正确——浏览器实测 + 数据库 COUNT 才是最高证据标准。

## 文档索引

- `docs/architecture/labelhub-complete-design-baseline.md` — 系统完整设计基线
- `docs/architecture/diagrams/` — 四张架构图
- `docs/adr/` — ADR-001 ~ ADR-015 架构决策记录
- `docs/internal/decision-log.md` — 架构与实现决策日志
- `docs/m2 ~ m5-acceptance-checklist.md` — 各里程碑验收清单
- `docs/screenshots/INDEX.md` — 截图证据索引
- `humanpending.md` — 全部批次闭环台账(当前至 #259)
- `CODEX.md` / `coderules.md` — 实施代理工作约定与代码规则
