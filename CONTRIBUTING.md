# 参与贡献

感谢你愿意改进 LabelHub。这个项目对"事实"和"证据"要求很严格，下面几条规则会保证你的改动能顺利合并。

## 开始之前

1. 先读 [AGENTS.md](AGENTS.md) 和 [docs/README.md](docs/README.md)。AI 编码 agent 与人类贡献者遵守同一套规则。
2. 业务事实以[设计基线](docs/architecture/labelhub-complete-design-baseline.md)为准；工程约束见 [conventions](docs/engineering/conventions.md)。
3. 本地环境搭建见[开发环境指南](docs/guides/dev-environment.md)，`python3 scripts/seed-demo.py` 可以生成一条完整的演示链路。

## 不可违反的规则

- **append-only 对象不能更新，也不能删除**：Schema 版本、提交、AI 调用、Quality Ledger 条目、导出快照、审计日志、策略事件、agent 轨迹。对应的 Mapper 只能有 insert 和 select 方法，契约测试会检查。
- **AI 的输出只是证据**。AI 不能写最终裁决（`reviewer_overall_verdict`）。
- **契约先行**：改 API 时先改 [`packages/contracts/openapi/labelhub.yaml`](packages/contracts/openapi/labelhub.yaml)，前端类型由 `pnpm --filter @labelhub/web gen:api` 生成，不要手改。
- **架构变更先写 ADR**（[模板](docs/adr/ADR-template.md)），再动代码。
- **改 AI 行为要先过评测**：涉及 prompt、校验器、路由或策略的改动，需要在 `evals/` 中给出可回放的对比，并通过对应的关卡。没有提升的改动不合并；负结果同样写进 ADR 留档（例如 [ADR-021](docs/adr/ADR-021-h4-precedent-memory.md)）。
- 文档推断不出来的业务决策，记到 [docs/process/humanpending.md](docs/process/humanpending.md)（只追加），不要自己猜。

## 提交前

```bash
make verify                                   # 后端：单元测试 + Testcontainers 集成测试
make evals                                    # 离线评测（改动 evals/ 或 AI 行为时必跑）
pnpm --filter @labelhub/web lint
pnpm --filter @labelhub/web typecheck
pnpm --filter @labelhub/web test
```

CI 会执行同样的检查。只要有集成测试被跳过，CI 就判失败。

## Pull Request

- 一个 PR 只做一件事。标题写清楚改了什么，描述里写为什么改、怎么验证的。
- 按 PR 模板填写检查清单。改动了契约、Schema 或多个限界上下文的，请在描述里明确说明。
- 前端改动只用设计 token（`apps/web/src/shared/ui/tokens.css`）和 `shared/ui` 组件，不要写死颜色或字号。
