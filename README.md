# LabelHub

[![CI](https://github.com/Iceberg86-gb/labelhub-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/Iceberg86-gb/labelhub-platform/actions/workflows/ci.yml)

**面向训练数据生产的可信工作台。AI 负责提供证据，人类负责最终裁决，每一份交付都可以复现、可以验证。**

LabelHub 覆盖从数据导入、标注、AI 预审、多级人工审核，到导出 SFT/偏好训练格式的完整链路。和普通标注工具不同的是，它把每一个关键事实都以 append-only 的方式记录下来，包括 Schema 版本、提交、审核结论、AI 调用和导出快照。所以它能回答一个大多数工具答不上来的问题：

> **你凭什么说这批训练数据是对的？**

项目正在演进为一个**标注垂类 Agent Harness**：让 LLM agent 在规则、校验器和人类问责的约束下生产训练数据。详见[路线图](#路线图)。

---

## 它如何工作

```
 数据集导入 ──→ Schema 设计（版本不可变） ──→ 标注员领取并作答（绑定领取时的 Schema 版本）
                                                      │
                                                      ▼
                                AI 预审（强制 function calling，按三区阈值分流）
                                    ├─ 高置信度 → 建议通过 ┐
                                    ├─ 不确定   → 转人工   ├──→ 人类审核与仲裁（最终裁决）
                                    └─ 低分     → 建议打回 ┘          │
                                                                      ▼
             Quality Ledger（append-only，结论实时派生）──→ 可信导出（可复现，按哈希校验）
```

- **请求边界**：所有业务写入只经过 `services/api`。业务状态和 outbox 事件在同一个事务里写入（[ADR-008](docs/adr/ADR-008-outbox-pattern.md)）。
- **异步边界**：`services/agent` 轮询 outbox，执行 AI 预审和导出作业，具备幂等、重试、死信，并会记录 `last_error`。
- **契约边界**：`packages/contracts/openapi/labelhub.yaml` 是唯一的 API 源头，前端类型全部由它生成（[ADR-012](docs/adr/ADR-012-contract-first-openapi.md)）。

## 核心设计取舍

| 取舍 | 理由 | 记录 |
|---|---|---|
| **AI 是证据，不是裁决** | AI 结论连同 prompt 版本、模型、input hash、成本和延迟一起作为证据写入账本，可以检视、导出或排除，但不能改写最终结论。人类审核者是训练数据准入的问责闸门。 | [ADR-005](docs/adr/ADR-005-ai-evidence-not-verdict.md) |
| **事实只追加，不修改** | 已发布的 Schema 版本不可变，历史提交永远能按原样渲染。Verdict 从最新的账本条目实时派生，用读时计算换取写路径的简单，以及事实不可篡改。 | [ADR-002](docs/adr/ADR-002-schema-immutability.md) / [003](docs/adr/ADR-003-quality-ledger.md) / [009](docs/adr/ADR-009-local-immutability.md) |
| **导出可复现** | 规范化序列化后，同一份数据导出两次得到逐字节相同的文件和 manifest 哈希，并且有测试守护。 | [ADR-004](docs/adr/ADR-004-export-snapshot.md) |
| **结构化输出是硬约束** | AI 预审必须通过 function calling 输出，不解析纯文本。校验失败时，走确定性的重试或转人工路径。 | [ADR-006](docs/adr/ADR-006-function-calling-required.md) |
| **拒绝静默回退** | Provider 配置出错时，失败会在重试和死信链路上显式暴露，不会悄悄退回到环境变量的配置。 | [ADR-011](docs/adr/ADR-011-doubao-default-openai-fallback.md) |
| **模块化单体 + 独立 worker** | 13 个业务模块之间靠包边界隔离，单库单进程部署。只有消费异步事件、可能独立失败的 AI worker 被拆成单独进程。 | [ADR-001](docs/adr/ADR-001-modular-monolith.md) |

## 快速开始

依赖：JDK 17、Node 20+、pnpm 9、Docker。

```bash
make dev-up                          # MySQL 8 + Redis 7 + MinIO（自动创建 bucket）
make dev-api                         # 启动后端，默认运行在 :8080/api，自动准备 labelhub_test 测试库
pnpm install
pnpm --filter @labelhub/web dev      # 启动前端 http://127.0.0.1:5173（启动前从 OpenAPI 生成类型）
```

本地演示账号的密码都是 `demo1234`：`owner_demo`、`labeler_demo`、`reviewer_demo`、`senior_reviewer_demo`。环境变量样例见 [`.env.example`](.env.example)，详细说明见 [开发环境指南](docs/guides/dev-environment.md)。

**验证**（CI 每次 push 都会执行同样的检查）：

```bash
make verify                          # 后端单元测试 + Testcontainers 集成测试
pnpm --filter @labelhub/web lint && pnpm --filter @labelhub/web typecheck && pnpm --filter @labelhub/web test
```

## 角色

| 角色 | 职责 |
|---|---|
| Owner | 创建任务、导入数据集（配额由题目数派生）、设计并发布 Schema、触发 AI 预审、创建可信导出 |
| Labeler | 先到先得领取（乐观锁守护配额，[ADR-007](docs/adr/ADR-007-fcfs-distribution.md)），按领取时绑定的 Schema 版本作答，自动保存草稿，批量领取和提交 |
| Reviewer | 初审 approve/reject，疑难提交可以标记升级 |
| Senior Reviewer | 在独立的仲裁工作台处理 AI 升级、疑难标记和抽检，不做二次全量审核 |
| Platform Admin | 配置 LLM Provider（密钥加密存库）、查看成本/效率/人力仪表盘、管理用户与角色、查看审计日志 |

## 仓库结构

```
apps/web/              React 18 + TypeScript + Vite，Semi Design，Formily 渲染器（Feature-Sliced Design，ADR-013）
services/api/          Spring Boot 模块化单体：业务权威、契约实现、Flyway 迁移
services/agent/        AI worker：轮询 outbox，执行 AI 预审和导出
packages/contracts/    OpenAPI 契约（唯一源头）与校验语料
infra/                 本地 / 演示 / 生产三套 compose，nginx，MinIO 初始化
scripts/               开发启动、部署、CI 辅助脚本
docs/                  设计基线、ADR、指南、设计系统、过程记录（导航见 docs/README.md）
```

## 文档

从 [**docs/README.md**](docs/README.md) 开始。常用入口：

- [设计基线](docs/architecture/labelhub-complete-design-baseline.md)：业务事实模型的权威来源
- [架构决策记录](docs/adr/)：ADR-001 ~ 015
- [工程规范](docs/engineering/conventions.md) 和 [AI 协作宪章](docs/engineering/agent-charter.md)，AI 编码 agent 的入口是 [AGENTS.md](AGENTS.md)
- [批次审计台账](docs/process/humanpending.md)：append-only，记录了每个合入批次的交付和验证过程

## 路线图

| 阶段 | 内容 | 状态 |
|---|---|---|
| W0 | CI 与工程基线：集成测试真实运行，跳过即判失败 | ✅ 完成 |
| W1 | 离线评测框架：基于 HelpSteer2 的黄金集、录制/回放、单次调用的基线成绩 | 进行中 |
| W2 | Agent 循环：工具层 + 基于 Schema 校验器的自我纠错 | 计划中 |
| W3 | 策略层：三区阈值改造成权限模式，预算控制，阈值校准 | 计划中 |
| W4 | 判例记忆：检索人类裁决作为参考，人机分歧回流 | 计划中 |
| W5 | MCP Server：外部 agent 可以在同一套治理与审计规则下接入 | 计划中 |

## 在线演示

演示入口是 `http://120.26.182.61:8443/`。备案和 HTTPS 切换还没完成，所以访问时必须带上 `http://` 和 `:8443`。公网演示账号由项目 Owner 私下发放。部署方式见 [演示环境说明](docs/guides/demo-environment.md)。
