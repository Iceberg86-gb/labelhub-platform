# LabelHub 文档

文档按"权威程度"和"是否还在变化"分层。遇到冲突时，以靠前的为准（[agent-charter](engineering/agent-charter.md) LH-1）。

| 层 | 目录 | 内容 |
|---|---|---|
| 业务事实 | [`architecture/`](architecture/) | [设计基线](architecture/labelhub-complete-design-baseline.md)、[架构图](architecture/diagrams/)、[状态机](architecture/state-machines/)（任务 / 提交 / 导出） |
| 决策 | [`adr/`](adr/) | 架构决策记录 ADR-001 ~ 019，新决策用 [模板](adr/ADR-template.md) |
| 工程规范 | [`engineering/`](engineering/) | [工程实现约束](engineering/conventions.md)、[AI 协作宪章](engineering/agent-charter.md) |
| 接口 | [`api/`](api/) | [接口清单](api/inventory.md)。契约源头是 [`packages/contracts/openapi/labelhub.yaml`](../packages/contracts/openapi/labelhub.yaml) |
| 指南 | [`guides/`](guides/) | [开发环境](guides/dev-environment.md)、[演示环境与部署](guides/demo-environment.md)、[演示脚本](guides/demo-script.md) |
| 设计系统 | [`design-system/`](design-system/) | 视觉语言、图标、插画、状态标签。运行时 token 在 `apps/web/src/shared/ui/tokens.css` |
| 过程记录 | [`process/`](process/) | [批次审计台账](process/humanpending.md)（append-only）、[决策日志](process/decision-log.md)、[AI Coding 日志](process/ai-coding-log.md) |
| 归档 | [`archive/`](archive/) | 已封闭里程碑（M2–M7）的预估、范围预算、验证报告，以及截图证据。只读，不再更新 |

## 写文档的约定

- 架构层面的变更必须**先写 ADR，再动代码**（[conventions §9](engineering/conventions.md)）。
- `process/humanpending.md` 和 `archive/` 是历史记录，只能追加，不能改写。
- 文档里不要写会过期的统计数字（接口数、测试数、迁移数）。需要的话，让 CI 或脚本生成。
