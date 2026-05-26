# LabelHub OpenAPI Contract

`labelhub.yaml` is the contract source for LabelHub.

Rules:

- Update this file before implementing a new API.
- Generated Java and TypeScript clients are not committed.
- Postman collections and rendered API docs may be committed under `docs/api/` for review.
- Internal worker callbacks use the `Internal` tag and must stay behind the API boundary.

Generation commands:

```bash
pnpm --filter @labelhub/web gen:api
mvn -pl services/api generate-sources
```
