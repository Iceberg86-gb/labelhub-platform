# LabelHub System Overview

## 取证结论

- 后端边界是 `services/api` 模块化单体；实际模块清单来自 `services/api/src/main/java/com/labelhub/api/module/`，不是旧基线里的七模块简写。
- 前端是 `apps/web`，依赖 `react`/`react-dom` 18.x 与 `@formily/*` 2.x；API 类型由 `packages/contracts/openapi/labelhub.yaml` 生成。OpenAPI 契约是设计期产物，不在运行时请求链路上。
- 生产入口是 `nginx` 服务：静态资源来自 `infra/web-dist`，`/api/` 反代到 `api:8080`；JWT 在 Spring Security 的 `JwtAuthenticationFilter` 处校验。
- AI 调用的运行态 provider 以 `openai-compatible` 为 provider type，环境 fallback 的 provider name 是 `doubao` 或 `openai`；本地 profile 有 `fake`，API 内置默认有 `mock`。

```mermaid
flowchart LR
    browser["浏览器(Browser)<br/>apps/web<br/>React 18.3.1 + Formily 2.3.2"]
    contract["OpenAPI 契约(OpenAPI Contract)<br/>packages/contracts/openapi/labelhub.yaml<br/>契约优先(contract-first OpenAPI)<br/>bearerAuth: JWT<br/>标签(Tags): Auth, Users, Tasks, Datasets, Schemas, Sessions, AIReview, LLMProviders, PromptVersions, Reviews, Exports, AuditLogs, Platform, PlatformCost, Internal"]

    subgraph static_api["静态资源与 API 边界(Static Assets and API Boundary)"]
        nginx["静态资源与反向代理(Static Assets and Reverse Proxy)<br/>nginx<br/>/usr/share/nginx/html<br/>/api/ proxy_pass http://api:8080"]
        jwt["JWT 认证(JWT Authentication)<br/>JwtAuthenticationFilter<br/>Authorization: Bearer &lt;JWT&gt;"]
        internal_token["内部令牌认证(Internal Token Authentication)<br/>InternalTokenFilter<br/>X-Internal-Token for /internal/**"]
    end

    subgraph api["api<br/>Spring Boot 模块化单体(Spring Boot modular monolith)<br/>services/api"]
        subgraph identity["身份与管理(Identity / Admin)"]
            admin["admin"]
            auth["auth"]
            platform["platform"]
            user["user"]
        end

        subgraph setup["任务配置(Task Setup)"]
            task["task"]
            dataset["dataset"]
            schema["schema"]
        end

        subgraph work["标注作业(Labeling Work)"]
            session["session"]
            submission["submission"]
        end

        subgraph review["审核 / AI / 质量(Review / AI / Quality)"]
            ai["ai"]
            quality["quality"]
        end

        subgraph delivery["异步与交付(Async / Delivery)"]
            outbox["outbox"]
            export_module["export"]
        end
    end

    agent["异步工作容器(Async Worker Container)<br/>agent<br/>services/agent<br/>OutboxAiReviewWorker<br/>OutboxExportWorker"]

    subgraph data["数据层(Data Layer)"]
        mysql["业务数据库(Business Database)<br/>mysql<br/>MySQL 8.0<br/>业务表(business tables), outbox, quality_ledger_entries, export_snapshots"]
        minio["对象存储(Object Storage)<br/>minio<br/>S3-compatible object storage<br/>上传与导出文件(uploads and export files)"]
    end

    llm["外部 LLM 提供商(External LLM Providers)<br/>providerType: openai-compatible<br/>ENV providerName: doubao / openai<br/>DB providerName 来自(from) llm_provider_configs"]
    local_ai["本地/内置替身(Local / Built-in Doubles)<br/>API 默认(API default): mock<br/>agent 本地 profile(agent local profile): fake"]

    browser -->|"加载静态资源(load static assets)"| nginx
    browser -->|"调用 /api/*(call /api/*)"| nginx
    contract -.->|"生成前端类型(generates client types)"| browser
    contract -.->|"生成服务端接口(generates server stubs)"| api
    nginx -->|"/api/ 反代(proxy) http://api:8080"| jwt
    jwt -->|"认证通过(authenticated request)"| api

    api -->|"JDBC / Flyway / 业务权威(business authority)"| mysql
    api -->|"对象存储端点(object storage endpoint) OBJECT_STORAGE_ENDPOINT"| minio
    api -->|"追加 outbox 事件(append outbox events)"| mysql

    agent -->|"轮询 outbox(poll outbox via MySQL)"| mysql
    agent -->|"内部 API 回调(internal API callbacks)"| internal_token
    internal_token -->|"内部认证通过(authenticated internal request)"| api
    agent -->|"调用 LLM(call LLM)<br/>chat/completions<br/>function-calling compatible JSON"| llm
    agent -.->|"local profile(本地 profile)"| local_ai

    export_module -->|"导出元数据与快照(export metadata and snapshots)"| mysql
    export_module -->|"导出文件(export files)"| minio
```

## 实证来源

- 模块化单体决策：`docs/adr/ADR-001-modular-monolith.md`。
- 基线模块划分与架构叙述：`docs/architecture/labelhub-complete-design-baseline.md`。
- 实际模块清单：`services/api/src/main/java/com/labelhub/api/module/` 下的 `admin`、`ai`、`auth`、`dataset`、`export`、`outbox`、`platform`、`quality`、`schema`、`session`、`submission`、`task`、`user`。
- 前端 React/Formily 依据：`apps/web/package.json`、`apps/web/src/features/labeling/formily/SchemaFormilyRenderer.tsx`。
- Contract-first OpenAPI 与 tag 列表：`docs/adr/ADR-012-contract-first-openapi.md`、`packages/contracts/openapi/labelhub.yaml`。
- JWT 认证位置：`services/api/src/main/java/com/labelhub/api/config/SecurityConfig.java`、`services/api/src/main/java/com/labelhub/api/security/JwtAuthenticationFilter.java`、`packages/contracts/openapi/labelhub.yaml` 的 `bearerAuth`。
- 静态资源与 API 反代：`infra/docker-compose.prod.yml` 的 `nginx` 服务、`infra/nginx/labelhub.conf`。
- MySQL/MinIO 数据层：`infra/docker-compose.prod.yml` 的 `mysql`、`minio`、`minio-init`、`api` 环境变量。
- AI provider 运行态：`services/agent/src/main/java/com/labelhub/agent/llm/runtime/EnvRuntimeProviderSourceFactory.java`、`services/agent/src/main/java/com/labelhub/agent/llm/runtime/RegistryBackedAiReviewProvider.java`、`services/agent/src/main/java/com/labelhub/agent/llm/runtime/OpenAiCompatibleAiReviewRuntimeClient.java`、`services/agent/src/main/java/com/labelhub/agent/llm/FakeAiReviewProvider.java`。
- API 侧 provider 抽象与默认替身：`services/api/src/main/resources/application.yml`、`services/api/src/main/java/com/labelhub/api/module/ai/provider/OpenAiCompatibleProvider.java`、`services/api/src/main/java/com/labelhub/api/module/ai/provider/MockAiProvider.java`。
