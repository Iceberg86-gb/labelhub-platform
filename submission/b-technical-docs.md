# B. 基础技术文档(整理索引)

> 整理日期:2026-06-26。本文档是 LabelHub 基础技术文档的分类导览,每份文档附定位摘要与新鲜度评估。AI Coding 过程类文件见 [a-ai-coding-process.md](a-ai-coding-process.md)。

## 0. 文档地图

| 分类 | 位置 | 数量 |
|------|------|------|
| 项目总览 | [README.md](../README.md) | 1 |
| 架构与设计基线 | `docs/architecture/` | 1 基线 + 4 图 |
| 架构决策记录 | `docs/adr/` | 15 份 ADR |
| 业务工作流 | `docs/workflows/` | 3 状态机 + 1 演示脚本 |
| API 契约 | `packages/contracts/openapi/` + `docs/api*` | 契约 + 2 索引 |
| 环境与运维 | `docs/dev-environment.md`、`docs/demo-environment.md`、`infra/deploy/README.md` | 3 |
| 设计资产 | `docs/design-assets/` | tokens + 30+ SVG |
| 证据索引 | `docs/screenshots/` | INDEX + 100+ 截图 |

## 1. 项目总览

- **[README.md](../README.md)** — 项目门面:系统架构图(双进程 + 契约先行)、13 个后端模块职责表、五角色流程、本地启动指引、十条关键设计取舍(每条挂 ADR 或台账依据)、文档索引。

## 2. 架构与设计基线(docs/architecture/)

- **[labelhub-complete-design-baseline.md](../docs/architecture/labelhub-complete-design-baseline.md)** — 完整设计基线(400+ 行):项目定位为"AI 监督信号治理系统",覆盖课题硬要求(四角色、任务管理、动态表单、AI 预审、多级审核、多格式导出)与四大差异化亮点。**注意**:closure 248 取证发现基线存在五处与代码漂移(七模块简写过期/实际 13 包、"独立 Export Worker"不存在、端口暴露描述过期、LLM 接入方式、旧状态机表),阅读时以代码与架构图为准。
- **四张架构图([docs/architecture/diagrams/](../docs/architecture/diagrams/))**,2026-06-06 交付,实证已校准至最新代码:
  - `system-overview.md` — 系统全景:模块划分、契约生成链、nginx 反代、agent 轮询;
  - `deployment.md` — 单 ECS 生产拓扑:7 容器、8443 公网入口、备案/HTTPS 待办;
  - `core-flow-sequence.md` — 主链路时序:发布→领取→提交→AI 预审→初审→复审→accepted;
  - `core-flow-revision.md` — 打回与修改分支:needs_revision→superseded 修改链。

## 3. 架构决策记录(docs/adr/,15 份)

| ADR | 决策 | 一句话理由 |
|-----|------|-----------|
| [001](../docs/adr/ADR-001-modular-monolith.md) | 模块化单体 + 独立 AI Worker | 个人项目要边界清晰,不要部署复杂度 |
| [002](../docs/adr/ADR-002-schema-immutability.md) | Schema 版本不可变 | 历史提交永远按原貌渲染,stableId 不复用 |
| [003](../docs/adr/ADR-003-quality-ledger.md) | Quality Ledger append-only | Verdict 从 latest entry 派生,事实不可篡改 |
| [004](../docs/adr/ADR-004-export-snapshot.md) | 导出快照不可变 | 同参数两次导出 hash 必须相等,diff 可归因 |
| [005](../docs/adr/ADR-005-ai-evidence-not-verdict.md) | AI 是证据不是裁决 | 人类审核者是训练数据准入的问责闸门 |
| [006](../docs/adr/ADR-006-function-calling-required.md) | 强制 function-calling | 禁止裸文本解析,失败路径确定性 |
| [007](../docs/adr/ADR-007-fcfs-distribution.md) | FCFS 领取 + 乐观锁 | 演示友好,指派/竞价留作扩展 |
| [008](../docs/adr/ADR-008-outbox-pattern.md) | MySQL outbox 轮询 | 事务原子写业务+事件,无需 Kafka |
| [009](../docs/adr/ADR-009-local-immutability.md) | 局部不可变 | 只冻结破坏可信链路的对象,其余保持 CRUD |
| [010](../docs/adr/ADR-010-spring-boot-mybatis-plus.md) | Spring Boot + MyBatis-Plus + Java 17 | 与课题方案对齐,JSON 列支持好 |
| [011](../docs/adr/ADR-011-doubao-default-openai-fallback.md) | LLM registry-first(Batch B 修订) | DB 配置优先,配置错误显式可见,不静默回退 |
| [012](../docs/adr/ADR-012-contract-first-openapi.md) | 契约先行 | labelhub.yaml 是唯一 API 源头,类型全生成 |
| [013](../docs/adr/ADR-013-fsd-frontend.md) | 简化 Feature-Sliced 前端 | app/pages/features/entities/shared 五层 |
| [014](../docs/adr/ADR-014-scoped-zero-pause.md) | Scoped Zero-Pause(Proposed) | 未决项入 humanpending.md,不阻塞无关开发 |
| [015](../docs/adr/ADR-015-openapi-contract-drift-control.md) | 契约漂移控制 | 4 个基线端点受保护,脚本守护 |

## 4. 业务工作流(docs/workflows/)

- **[state-machine-task.md](../docs/workflows/state-machine-task.md)** — Task 四态:draft→published→paused/ended,迁移写 `task_transitions` + `audit_logs`;
- **[state-machine-submission.md](../docs/workflows/state-machine-submission.md)** — Submission 核心链:created→under_ai_review→under_human_initial→…→accepted,打回分支 needs_revision→superseded(基线旧表已过期,**以此文档为准**);
- **[state-machine-export.md](../docs/workflows/state-machine-export.md)** — Export Job:created→queued→running→completed/failed,支持 revoked;
- **[demo-script.md](../docs/workflows/demo-script.md)** — 10 分钟答辩演示脚本,含四亮点分钟级排程。

## 5. API 契约

- **[packages/contracts/openapi/labelhub.yaml](../packages/contracts/openapi/labelhub.yaml)** — API 唯一源头:v0.10.0,89 个 operation、15 个 tag、151 个 schema;前端类型与后端 Controller 接口均由此生成。
- **[docs/api-inventory.md](../docs/api-inventory.md)** — 整理版 API 清单(2026-06-10):全部 89 端点按业务域分组,含方法/路径/operationId/权限/中文说明、角色模型、命名约定,以及 3 处契约与实现差异记录。
- **[docs/api/README.md](../docs/api/README.md)** — API 产物索引(极简,声明契约源头)。

## 6. 环境与运维

- **[docs/dev-environment.md](../docs/dev-environment.md)** — 开发环境手册(~170 行):JDK 17 探测、pnpm workspace、本地基础设施 compose、测试库隔离(`labelhub_test`)、Makefile 目标、关键环境变量。
- **[docs/demo-environment.md](../docs/demo-environment.md)** — 演示环境说明(2026-06-26):公网入口 `http://120.26.182.61:8443/`、健康探针、账号名与密码发放规则、7 容器拓扑、五角色演示动线、已知限制与挂账。
- **[infra/deploy/README.md](../infra/deploy/README.md)** — 生产部署 runbook:单 ECS 九步部署、密钥生成、备份 cron 与恢复演练、ICP/TLS 切换计划。

## 7. 设计资产(docs/design-assets/)

- **[README.md](../docs/design-assets/README.md)** — 资产清单:功能 icons、空状态 illustrations、审核状态 status 标签、hero 图,附 SVGR 导入与 CSS 变量使用规范;
- **[tokens/](../docs/design-assets/tokens/README.md)** — 设计令牌:`tokens.css` 与 `tokens.ts` 双格式同步维护(浅色表面、冷灰绿侧栏渐变、低饱和语义色、柔和圆角)。

## 8. 证据索引(docs/screenshots/)

- **[INDEX.md](../docs/screenshots/INDEX.md)** — 按 M1→M5 演进链组织的截图总索引,核心是四大亮点证据链与防御性证据(403/404/状态守卫),含"90 秒四亮点演示路径";批次级 before/after 对照目录(m6p6、m7p1、m7p2 等)各自带子 INDEX。

## 9. 新鲜度评估与已知漂移

| 文档 | 状态 | 说明 |
|------|------|------|
| ADR 全集 | ✅ 稳定 | 仅 ADR-011 经 Batch B 修订,ADR-014 为 Proposed |
| 架构图四张 | ✅ 最新 | 2026-06-06 按代码实证校准 |
| 状态机文档 | ✅ 权威 | 基线中旧状态机表已声明以 workflows 为准 |
| 设计基线 | ⚠️ 部分过期 | closure 248 记录五处漂移,读架构细节时需对照代码 |
| README | ⚠️ 统计过期 | "87 operations"实际 89;"Flyway ×29"实际 30;引用的 `docs/m2~m5-acceptance-checklist.md` 在仓库中不存在(疑似 closure 247 docs 整理时移除,README 未同步);`submission` 目录此前不存在,本批整理时创建 |
| OpenAPI 契约 | ⚠️ 一处缺口 | `POST /internal/exports/jobs/{id}/run` 实现存在但未登记(详见 api-inventory.md 第 14 节) |
