# LabelHub OpenAPI 骨架搭建方案

> 配套文件:`packages/contracts/openapi/labelhub.yaml`
> 适用场景:contract-first 模式下的前后端协同
> 上游依赖:CODEX §2(契约源头规范)、baseline §10(数据模型)、baseline §11(状态机)

---

## 1. 文件定位

```
packages/
└── contracts/
    └── openapi/
        ├── labelhub.yaml           # 主契约文件(本次骨架)
        ├── README.md               # 变更规范、版本管理说明
        └── examples/               # 复杂请求/响应示例
```

`labelhub.yaml` 是**唯一的契约源头**。前端 TypeScript client、后端 Java DTO/Controller interface 都从这个文件生成。

## 2. 当前骨架覆盖范围

### 已覆盖的端点(26 个 operation)

| Tag | 端点数 | 用途 |
|-----|--------|------|
| Auth | 1 | 登录 |
| Tasks | 5 | 任务 CRUD、状态迁移、广场、领取 |
| Schemas | 3 | 创建、发布版本、历史重渲染(亮点 1) |
| Sessions | 2 | 草稿保存、提交 |
| AIReview | 4 | 字段级辅助、AI trace 查询、AI 规则配置、预审结果查询(亮点 4) |
| Reviews | 5 | 审核队列、单条/批量动作、ledger 查询、rule 重算(亮点 2) |
| Exports | 5 | 创建、查询、snapshot 查询、diff(亮点 3) |
| Internal | 1 | AI Worker 回写预审结果 |

### 已覆盖的核心 Schema(40 个)

按 baseline §10 数据模型 + 4 个亮点的关键结构都已建模。其中重点:
- `SchemaVersion`:含 `fieldStableIds` 映射(亮点 1)
- `AIReviewResult`:Function Calling 输出结构(对齐 CODEX §7)
- `FieldAITrace`:字段级 provenance 历史链(亮点 4)
- `ExportDiff`:差异归因结构(亮点 3)
- `ReviewAction` + `RecomputeJob`:ledger 派生机制(亮点 2)

## 3. 还需要扩展的端点(下一步交给开发 agent 补)

### P0 必须补(完成核心闭环)

- `GET /tasks/{taskId}` — Task 详情
- `PUT /tasks/{taskId}` — 任务编辑(草稿态可改全部、发布态只可改有限字段)
- `POST /tasks/{taskId}/datasets:import` — 数据集导入(JSON / JSONL / Excel)
- `GET /tasks/{taskId}/items` — 题目列表 + 预览
- `PUT /tasks/{taskId}/items/{itemId}` — 题目批量编辑
- `GET /sessions/{sessionId}` — Session 详情(含 schema、当前 draft、上一轮审核意见)
- `GET /sessions/{sessionId}/draft` — 获取最新草稿(刷新恢复)
- `GET /my/sessions` — Labeler 的"我的数据"(已提交/通过/打回/待修改)
- `GET /submissions/{submissionId}` — Submission 详情
- `GET /admin/audit-logs` — Audit log 查询(Admin)

### P1 强烈建议补(完善体验)

- `POST /auth/logout`、`POST /auth/refresh`
- `GET /schemas/{schemaId}/versions` — 版本列表
- `GET /schemas/{schemaId}/versions/{versionId}` — 指定版本详情
- `POST /schemas/{schemaId}/versions/{versionId}/deprecate` — 版本下架
- `GET /reviews/queue/diff/{submissionId}` — 第 1/2 轮 diff(对齐 F5.8)
- `POST /ai-review/rules` — AI 预审规则配置(Owner)
- `GET /ai-review/rules/{ruleId}` — 规则查询
- `POST /adjudication-rules` — Adjudication rule CRUD
- `GET /admin/dashboard` — Owner 数据看板

### P2 锦上添花

- 上传文件相关:`POST /files/upload`、`GET /files/{fileId}`
- 用户管理(Admin):`POST /admin/users`、`POST /admin/users/{userId}/roles`
- 健康检查:`GET /health`、`GET /ready`(通常不在 OpenAPI 里,但 Spring Actuator 暴露)

## 4. 工具链集成

### 4.1 后端 Java (Spring Boot)

使用 `openapi-generator-maven-plugin`。在 `services/api/pom.xml` 中:

```xml
<plugin>
  <groupId>org.openapitools</groupId>
  <artifactId>openapi-generator-maven-plugin</artifactId>
  <version>7.6.0</version>
  <executions>
    <execution>
      <id>generate-api-interfaces</id>
      <goals><goal>generate</goal></goals>
      <configuration>
        <inputSpec>${maven.multiModuleProjectDirectory}/packages/contracts/openapi/labelhub.yaml</inputSpec>
        <generatorName>spring</generatorName>
        <library>spring-boot</library>
        <apiPackage>com.labelhub.api.generated.api</apiPackage>
        <modelPackage>com.labelhub.api.generated.model</modelPackage>
        <configOptions>
          <!-- 只生成接口,Controller 实现由开发者手写 -->
          <interfaceOnly>true</interfaceOnly>
          <useSpringBoot3>true</useSpringBoot3>
          <useJakartaEe>true</useJakartaEe>
          <skipDefaultInterface>true</skipDefaultInterface>
          <openApiNullable>false</openApiNullable>
          <useTags>true</useTags>
          <dateLibrary>java8</dateLibrary>
        </configOptions>
      </configuration>
    </execution>
  </executions>
</plugin>
```

**关键约束(对齐 CODEX §2)**:

- 生成结果**只有 interface 和 model**,不生成 Controller 实现
- 每个 Controller 必须 `implements XxxApi`,签名编译期校验
- 生成代码出现在 `target/generated-sources/`,不入版本控制

生成后的目录结构示例:
```
com.labelhub.api.generated/
├── api/
│   ├── AuthApi.java          # interface
│   ├── TasksApi.java
│   └── ...
└── model/
    ├── Task.java             # DTO
    ├── SchemaVersion.java
    └── ...
```

手写实现示例:
```java
@RestController
public class TasksController implements TasksApi {
    @Override
    public ResponseEntity<Task> createTask(CreateTaskRequest request) {
        // 业务实现
    }
}
```

### 4.2 前端 (React + TypeScript)

使用 `@openapitools/openapi-generator-cli` 或 `openapi-typescript`。后者更轻量,推荐:

```bash
pnpm add -D openapi-typescript
```

在 `apps/web/package.json` 添加脚本:
```json
{
  "scripts": {
    "gen:api": "openapi-typescript ../../packages/contracts/openapi/labelhub.yaml -o src/shared/api/generated/schema.d.ts"
  }
}
```

生成的 `schema.d.ts` 包含全部类型,配合 `openapi-fetch` 作为运行时客户端:

```bash
pnpm add openapi-fetch
```

封装 API client(`src/shared/api/client.ts`):
```typescript
import createClient from "openapi-fetch";
import type { paths } from "./generated/schema";

export const apiClient = createClient<paths>({
  baseUrl: import.meta.env.VITE_API_BASE_URL,
});

// 调用示例(类型完全自动推导):
const { data, error } = await apiClient.POST("/tasks", {
  body: { title: "...", distributionPolicy: "first_come_first_served" },
});
```

**强约束(对齐 CODEX §5)**:
- `apps/web/src/features/*` 必须通过 `apiClient` 调用,**禁止手写 fetch / axios**
- TanStack Query 包装 `apiClient` 调用作为服务端状态层

### 4.3 Mock Server(可选但强烈推荐)

Prism 可以基于 OpenAPI 启动一个 mock server,**让前端在后端未就绪时就能开发**:

```bash
pnpm add -D @stoplight/prism-cli
```

```bash
prism mock packages/contracts/openapi/labelhub.yaml -p 4010
```

前端把 `VITE_API_BASE_URL` 指向 `http://localhost:4010` 即可联调。

### 4.4 验证 OpenAPI 文件语法

提交前用 `redocly` 或 `spectral` 校验:
```bash
npx @redocly/cli lint packages/contracts/openapi/labelhub.yaml
```

## 5. 变更管理规范

### 5.1 版本号

- `info.version` 遵循 SemVer:`MAJOR.MINOR.PATCH`
- 破坏性变更(删字段、改 enum、改路径)→ MAJOR
- 新增端点 / 字段 → MINOR
- 修描述 / 注释 → PATCH
- 当前 v0.2.0 表示 M1 已补齐 Owner 任务管理最小契约、错误模型、分页和 bearer auth,但尚未到 v1.0 稳定版

### 5.2 PR 流程

任何 API 变更走以下步骤:

1. **改 `labelhub.yaml`**(契约先变更)
2. 跑 `redocly lint` 通过
3. **后端**`mvn generate-sources` 重新生成 interface,Controller 实现按编译错误修正
4. **前端**`pnpm gen:api` 重新生成 schema,使用方按 TS 类型错误修正
5. 跑后端集成测试 + 前端类型检查
6. 在 PR 描述中说明:本次变更是 breaking / non-breaking、影响的 endpoint、是否需要数据迁移

### 5.3 严禁的事

- ❌ 在 Java Controller 加 `@Operation` 注解假装是 contract——这会让两份契约并存
- ❌ 在 OpenAPI 里用 `additionalProperties: true` 逃避建模(本骨架中只对 DraftPayload 等明确"开放结构"的对象使用)
- ❌ 不修 OpenAPI 直接改后端接口签名 ——会导致前端类型与实际响应不一致
- ❌ 跨多个 endpoint 共享一个超大 schema(应拆分)

## 6. 与 Postman / 文档生成的集成

- **Postman Collection**:用 `openapi-to-postmanv2` 自动生成,放到 `docs/api/postman/`。**不手动维护 Postman**
- **HTML 文档**:用 `redocly build-docs labelhub.yaml -o docs/api/index.html` 生成,可托管到 GitHub Pages
- **Swagger UI**:Spring Boot 启动后自动暴露在 `/swagger-ui.html`(用 springdoc-openapi-starter-webmvc-ui,但**仅作为开发期调试,不是契约源头**)

## 7. 与 CODEX 的对应关系

| OpenAPI 内容 | CODEX 约束 |
|-------------|-----------|
| `bearerAuth` 安全方案 | CODEX §11 凭证管理 |
| `AIReviewResult` schema | CODEX §7 Function Calling 结构 |
| 幂等接口的 description 标注幂等键 | CODEX §7.1 |
| append-only 对象在 description 中标注 | CODEX §4、§10 |
| `Internal` tag 的端点 | CODEX §3 services/agent 边界 |
| 错误响应统一 `ApiError` | CODEX §5 错误提示规范 |

## 8. 给开发 agent 的扩展指引

当 agent 接手扩展 OpenAPI 时,严格按以下顺序:

1. **先确认要新增的端点在 baseline 哪一节有依据**
2. **检查端点是否触发 CODEX §7.1 的幂等键场景**,如是,description 明确写出幂等键格式
3. **检查端点是否修改 append-only 对象**,如是,这是 bug,回到 baseline 重新评估
4. **检查端点是否跨 services/api 与 services/agent 边界**,如是,放在 `Internal` tag 下
5. **复用已有 schema**,不要给同一个领域对象建两份 schema(例如 Task 不要再建 TaskDto)
6. **错误响应统一引用 `#/components/responses/Error*`**,不要新建 inline 错误结构
7. **新增字段时遵循 nullable 显式标注**,避免 generator 产出 Optional 不一致

## 9. 当前骨架已知的简化(后续要补的)

- 未做分页元数据规范化(应统一抽出 `PageMeta` schema,目前各 Paged* 重复定义)
- 未做请求/响应示例(`examples:` 节)
- 未做 webhook 定义(MVP 阶段不需要)
- 未做版本化路径(`/api/v1` 已在 server URL,但未来若需 v2 需引入路径前缀策略)
- `ApiError.code` 是字符串,后续可枚举化(但要权衡 codes 数量爆炸)

这些简化都是 P2 优先级,**不影响当前开发启动**。

---

## 10. 一句话总结

> **这份骨架不是完整的 API 列表,而是一个"够用即可启动 + 标准化扩展规则"的起点。先用它跑通 monorepo 脚手架和第一个端到端调用,再按 §3 列表逐步扩端点。OpenAPI 文件本身比代码文件更重要,因为它是前后端唯一的真理来源。**
