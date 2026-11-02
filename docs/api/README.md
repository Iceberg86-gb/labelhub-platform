# API

**唯一事实来源：[`packages/contracts/openapi/labelhub.yaml`](../../packages/contracts/openapi/labelhub.yaml)**（契约先行，[ADR-012](../adr/ADR-012-contract-first-openapi.md)；漂移控制见 [ADR-015](../adr/ADR-015-openapi-contract-drift-control.md)）。

- 后端的 DTO 和接口由 `openapi-generator` 在构建时生成，生成物在 `services/api/target/generated-sources`。
- 前端的类型由 `pnpm --filter @labelhub/web gen:api` 生成，输出到 `apps/web/src/shared/api/generated/`。CI 会检查生成物和契约是否一致。
- [`inventory.md`](inventory.md)：按模块整理的接口清单，附带每个接口的权限要求。
