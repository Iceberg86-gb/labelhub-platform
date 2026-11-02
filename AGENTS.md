# AGENTS.md

AI 编码 agent 的入口（Codex、Claude Code 等）。人类开发者请从 [README](README.md) 开始。

## 开始工作前必读

1. [`docs/engineering/agent-charter.md`](docs/engineering/agent-charter.md)：工作方式、Zero-Pause 的激活条件，以及 LH-1~5 项目硬约束。
2. [`docs/engineering/conventions.md`](docs/engineering/conventions.md)：工程实现约束，包括幂等键格式（§7.1）、Hash 规范化（§7.2）和四个亮点的硬约束（§10）。
3. [`docs/architecture/labelhub-complete-design-baseline.md`](docs/architecture/labelhub-complete-design-baseline.md)：业务事实模型，冲突时以它为准。

## 不可违反的规则

- append-only 的对象（Schema 版本、提交、Ledger 条目、AI 调用、导出快照）**不能更新，也不能删除**，对应的 Mapper 也不得提供 update 或 delete 方法。
- AI 的输出只是证据，不是裁决。AI 不能写最终结论。
- 修改 API 时先改 `packages/contracts/openapi/labelhub.yaml`。前端类型是生成出来的，不要手改。
- 架构层面的变更必须先在 `docs/adr/` 写 ADR，再动代码。
- 无法从文档推断的业务决策，记录到 [`docs/process/humanpending.md`](docs/process/humanpending.md)，不要自己猜。

## 提交前必须全部通过

```bash
make verify                                   # 后端：单元测试 + Testcontainers 集成测试
pnpm --filter @labelhub/web lint
pnpm --filter @labelhub/web typecheck
pnpm --filter @labelhub/web test
make eval-test                                # 评测框架（改动 AI 行为时，还需附一份 make eval-record 的评测对比）
```

CI（`.github/workflows/ci.yml`）会执行同样的检查。只要有集成测试被跳过，CI 就判失败。
