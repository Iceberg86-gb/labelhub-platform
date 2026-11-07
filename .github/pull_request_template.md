## 改了什么，为什么

<!-- 一两句话说明动机和范围。关联 issue / ADR。 -->

## 怎么验证的

<!-- 列出跑过的命令和结果；涉及 AI 行为的，附上 evals 对比或关卡报告。 -->

## 检查清单

- [ ] 改 API 时先改了 OpenAPI 契约，并重新生成了前端类型
- [ ] 没有为 append-only 对象新增 update / delete
- [ ] 架构层面的变更已有 ADR
- [ ] 涉及 AI 行为的改动有可回放的评测对比，且通过关卡
- [ ] `make verify`、`make evals`、web 的 lint / typecheck / test 全部通过
