# LabelHub 架构图与状态机流程图

> 本文档基于代码实际真相绘制(`TaskStateTransitions.java`、OpenAPI 枚举、Flyway 迁移、ADR)。原则:代码标识符、产品/类名与少数固定术语保留英文,其余一律用中文;所有图为 Mermaid 格式,可在 GitHub / VS Code / Typora 直接渲染。
>
> **角色中英对照**:任务所有者(Owner)· 标注员(Labeler)· 审核员(Reviewer)· 高级审核员(Senior Reviewer)。

## 配色语义

全文统一一套语义色板,跨所有图保持一致:

| 颜色 | 含义 | 用途示例 |
|------|------|----------|
| 🟦 蓝 | 业务核心 / 进行中 | API 边界、PUBLISHED、PROCESSING |
| 🟩 绿 | 成功 / 通过 / 事实表 | approved、completed、只追加事实 |
| 🟧 琥珀 | 待处理 / 派生 / 草稿 | pending、读时派生、DRAFT |
| 🟥 红 | 失败 / 驳回 | rejected、failed、死信 |
| 🟪 紫 | 独立工作进程 / AI | services/agent、AI 预审 |
| ⬜ 灰 | 终态 / 预留 | ENDED、RESOLVED、预留 |

---

## 一、系统架构图

模块化单体 + 独立 AI 工作进程双进程,契约先行(contract-first)。

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontFamily':'-apple-system, system-ui, Segoe UI, sans-serif','fontSize':'13px','lineColor':'#94a3b8'}}}%%
flowchart TB
    subgraph Contract["🟧 契约源头 · contract-first"]
        YAML["<b>OpenAPI YAML</b><br/>labelhub.yaml v0.10.0<br/>87 个接口<br/><i>API 唯一真相源</i>"]
    end

    subgraph Frontend["🟦 前端 · apps/web(FSD 分层)"]
        WEB["<b>React 18 + TypeScript + Vite</b><br/>Semi Design · Formily 渲染器<br/>TanStack Query / Virtual · dnd-kit<br/><i>按角色分区 owner / labeler / reviewer / platform / admin</i>"]
    end

    subgraph API["🟦 业务权威边界 · services/api(Spring Boot 模块化单体)"]
        direction TB
        M1["<b>auth · user</b><br/>认证 · RBAC 权限"]
        M2["<b>task</b><br/>任务生命周期"]
        M3["<b>schema</b><br/>版本化 · 设计器 Designer"]
        M4["<b>dataset · session · submission</b><br/>领取 · 作答 · 提交"]
        M5["<b>quality</b><br/>质量台账 · 仲裁"]
        M6["<b>ai</b><br/>AI 预审编排"]
        M7["<b>export</b><br/>可信导出 Trusted Export"]
        M8["<b>outbox</b><br/>事务出箱"]
        M9["<b>platform · admin</b><br/>计量仪表盘 · 审计"]
    end

    subgraph Agent["🟪 AI 工作进程 · services/agent(独立进程)"]
        AW["<b>OutboxAiReviewWorker · OutboxExportWorker</b><br/>RuntimeProviderResolver<br/>AgentSecretRedactor<br/><i>幂等 · 重试 · 死信</i>"]
    end

    subgraph Storage["🟩 持久化与基础设施"]
        direction LR
        DB[("<b>MySQL 8</b><br/>Flyway ×29<br/>只追加事实表群")]
        OUTBOX[("<b>outbox 事件表</b><br/>轮询队列")]
        REDIS[("Redis 7<br/><i>预留</i>")]
        MINIO[("<b>MinIO</b><br/>S3 兼容对象存储<br/>导出产物")]
        LLM["<b>外部 LLM 提供方</b><br/>deepseek-chat<br/>OpenAI 兼容接口"]
    end

    YAML -. "openapi-typescript<br/>生成 TS 类型" .-> WEB
    YAML -. "人工对齐契约" .-> API
    WEB == "REST /api · JWT Bearer" ==> API

    M2 & M4 & M5 & M6 & M7 -- "同事务原子写<br/>业务状态 + 事件" --> M8
    M8 --> OUTBOX
    API --> DB
    AW -- "轮询消费" --> OUTBOX
    AW -- "registry-first 解析<br/>本地解密密钥" --> LLM
    AW -- "写回 ai_calls / ledger" --> DB
    M7 -- "canonical 产物落盘" --> MINIO
    API -. "预留" .-> REDIS

    classDef contract fill:#fffbeb,stroke:#d97706,stroke-width:2px,color:#78350f
    classDef frontend fill:#eff6ff,stroke:#2563eb,stroke-width:1.5px,color:#1e3a8a
    classDef backend fill:#eff6ff,stroke:#2563eb,stroke-width:1.5px,color:#1e3a8a
    classDef agent fill:#f5f3ff,stroke:#7c3aed,stroke-width:1.5px,color:#4c1d95
    classDef store fill:#f0fdfa,stroke:#0d9488,stroke-width:1.5px,color:#134e4a

    class YAML contract
    class WEB frontend
    class M1,M2,M3,M4,M5,M6,M7,M8,M9 backend
    class AW agent
    class DB,OUTBOX,REDIS,MINIO,LLM store

    style Contract fill:#fffdf7,stroke:#d97706,stroke-dasharray:4 3
    style Frontend fill:#f8fbff,stroke:#2563eb
    style API fill:#f8fbff,stroke:#2563eb
    style Agent fill:#faf8ff,stroke:#7c3aed
    style Storage fill:#f6fffd,stroke:#0d9488
```

---

## 二、领域分层与数据流

强调只追加(append-only)事实流与裁决(Verdict)实时派生。

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontFamily':'-apple-system, system-ui, sans-serif','fontSize':'13px','lineColor':'#94a3b8'}}}%%
flowchart LR
    subgraph Owner["🟦 任务所有者"]
        O2["导入数据集"]
        O3["设计并发布 Schema 版本"]
    end

    subgraph Labeler["🟦 标注员"]
        L1["领取任务<br/>先到先得(FCFS)+ 乐观锁"]
        L3["提交"]
    end

    subgraph Facts["🟩 只追加事实表(append-only)· 不可变"]
        F1["schema_versions<br/><i>发布后不可变</i>"]
        F2["dataset_items"]
        F3["submissions<br/><i>绑定提交时 Schema 版本</i>"]
        F4["ai_calls · ai_calls_in_field<br/><i>AI 溯源</i>"]
        F5["quality_ledger_entries<br/><i>只追加</i>"]
        F6["export_snapshots"]
    end

    subgraph Derived["🟧 读时派生"]
        D1["<b>裁决 Verdict 实时派生</b><br/>pending · approved · rejected<br/><i>取自最新台账记录</i>"]
    end

    subgraph Review["🟦 审核员 / 高级审核员"]
        R1["初审 approve / reject<br/>逐条全量"]
        R2["仲裁工作台<br/>senior_review_cases"]
    end

    O3 --> F1
    O2 --> F2
    L1 -. "领取时绑定(claim-time binding)" .-> F1
    L3 --> F3
    F3 -- "outbox 触发" --> F4
    F4 -- "AI 结论自动入账" --> F5
    R1 -- "审核裁决入账" --> F5
    R2 -- "案件裁决入账" --> F5
    F5 --> D1
    F3 & F1 --> F6

    classDef role fill:#eff6ff,stroke:#2563eb,stroke-width:1.5px,color:#1e3a8a
    classDef fact fill:#f0fdf4,stroke:#16a34a,stroke-width:1.5px,color:#14532d
    classDef derived fill:#fffbeb,stroke:#d97706,stroke-width:2px,color:#78350f

    class O2,O3,L1,L3,R1,R2 role
    class F1,F2,F3,F4,F5,F6 fact
    class D1 derived

    style Facts fill:#f6fef9,stroke:#16a34a
    style Derived fill:#fffdf7,stroke:#d97706
    style Owner fill:#f8fbff,stroke:#2563eb
    style Labeler fill:#f8fbff,stroke:#2563eb
    style Review fill:#f8fbff,stroke:#2563eb
```

---

## 三、任务状态机

> 真相源:`TaskStateTransitions.java` + 迁移约束 `chk_tasks_status`。状态严格四态,迁移由白名单守护;每次迁移写入 `task_transitions` 事实。

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontFamily':'-apple-system, system-ui, sans-serif','fontSize':'13px'}}}%%
stateDiagram-v2
    direction LR
    [*] --> DRAFT: 创建任务

    DRAFT --> PUBLISHED: 发布<br/>(校验已绑数据集 / Schema)
    PUBLISHED --> PAUSED: 暂停
    PUBLISHED --> ENDED: 结束
    PAUSED --> PUBLISHED: 恢复
    PAUSED --> ENDED: 结束
    ENDED --> [*]

    note right of DRAFT
        草稿状态
        可编辑任务配置
        配额由数据集题目数派生
    end note
    note right of PUBLISHED
        已发布状态
        标注员可领取
        任务编辑被锁
    end note
    note right of ENDED
        已结束 · 终态
        无任何出向迁移
    end note

    classDef draft fill:#fffbeb,stroke:#d97706,stroke-width:2px,color:#78350f
    classDef active fill:#eff6ff,stroke:#2563eb,stroke-width:2px,color:#1e3a8a
    classDef paused fill:#f9fafb,stroke:#94a3b8,stroke-width:2px,color:#334155
    classDef terminal fill:#f3f4f6,stroke:#6b7280,stroke-width:2px,color:#1f2937

    class DRAFT draft
    class PUBLISHED active
    class PAUSED paused
    class ENDED terminal
```

**迁移白名单(代码原文)**

| 起始 \ 目标 | DRAFT | PUBLISHED | PAUSED | ENDED |
|-----------|:-----:|:---------:|:------:|:-----:|
| **DRAFT** | — | ✅ | ✗ | ✗ |
| **PUBLISHED** | ✗ | — | ✅ | ✅ |
| **PAUSED** | ✗ | ✅ | — | ✅ |
| **ENDED** | ✗ | ✗ | ✗ | — |

---

## 四、标注会话状态机

> 真相源:OpenAPI `SessionStatus` 与 `LabelerSessionWorkStatus`。会话持久态(claimed / submitted / …)与派生工作态(含 approved / rejected)分离。

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontFamily':'-apple-system, system-ui, sans-serif','fontSize':'13px'}}}%%
stateDiagram-v2
    direction LR
    [*] --> CLAIMED: 领取<br/>(先到先得 + 乐观锁 · 绑定领取快照)

    CLAIMED --> SUBMITTED: 提交<br/>(单条 / 本批次批量)
    CLAIMED --> ABANDONED: 放弃
    SUBMITTED --> RETURNED: 打回修订
    RETURNED --> SUBMITTED: 重新提交
    SUBMITTED --> [*]: 进入审核派生
    ABANDONED --> [*]

    note right of CLAIMED
        已领取
        草稿自动保存
        领取时绑定 Schema 版本(claim-time binding)
    end note
    note right of SUBMITTED
        已提交
        工作态派生 approved · rejected
        取自质量台账
    end note

    classDef claimed fill:#fffbeb,stroke:#d97706,stroke-width:2px,color:#78350f
    classDef submitted fill:#eff6ff,stroke:#2563eb,stroke-width:2px,color:#1e3a8a
    classDef returned fill:#fff7ed,stroke:#ea580c,stroke-width:2px,color:#7c2d12
    classDef abandoned fill:#f3f4f6,stroke:#6b7280,stroke-width:2px,color:#1f2937

    class CLAIMED claimed
    class SUBMITTED submitted
    class RETURNED returned
    class ABANDONED abandoned
```

**会话最终裁决派生(读时计算,无物化表)**

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontFamily':'-apple-system, system-ui, sans-serif','fontSize':'13px','lineColor':'#94a3b8'}}}%%
flowchart LR
    LE["最新 quality_ledger_entry<br/>(按 submission 维度)"] --> V{派生}
    V -- "无记录" --> P["<b>pending</b><br/>待审"]
    V -- "verdict = approve" --> A["<b>approved</b><br/>通过"]
    V -- "verdict = reject" --> R["<b>rejected</b><br/>驳回"]

    classDef source fill:#f0fdf4,stroke:#16a34a,stroke-width:1.5px,color:#14532d
    classDef pending fill:#fffbeb,stroke:#d97706,stroke-width:2px,color:#78350f
    classDef approved fill:#ecfdf5,stroke:#059669,stroke-width:2px,color:#064e3b
    classDef rejected fill:#fef2f2,stroke:#dc2626,stroke-width:2px,color:#7f1d1d

    class LE source
    class P pending
    class A approved
    class R rejected
```

---

## 五、AI 预审与 outbox 状态机

> 真相源:`outbox` 表 status 字段、`PrereviewStatus`、`AiCallStatus`、ADR-008/011。AI 是证据非裁决(ADR-005),输出强制结构化函数调用(function-calling,ADR-006)。

### 5.1 outbox 事件生命周期

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontFamily':'-apple-system, system-ui, sans-serif','fontSize':'13px'}}}%%
stateDiagram-v2
    [*] --> PENDING: API 同事务写入事件<br/>(原子 业务状态 + 事件)
    PENDING --> PROCESSING: 工作进程轮询领取<br/>(加锁 locked_by)
    PROCESSING --> COMPLETED: 处理成功<br/>写入 processed_at
    PROCESSING --> PENDING: 瞬时失败重试<br/>retry_count++ · 退避 next_retry_at
    PROCESSING --> DEAD: 永久失败 / 重试耗尽<br/>记 last_error
    COMPLETED --> [*]
    DEAD --> [*]: 死信 · 显式可见

    note right of PROCESSING
        幂等键(idempotency key)
        命中 input_hash 则复用历史结果
        不再调用提供方
    end note
    note left of DEAD
        配置错误不静默回退
        (ADR-011 修订)
    end note

    classDef pending fill:#fffbeb,stroke:#d97706,stroke-width:2px,color:#78350f
    classDef processing fill:#eff6ff,stroke:#2563eb,stroke-width:2px,color:#1e3a8a
    classDef completed fill:#ecfdf5,stroke:#059669,stroke-width:2px,color:#064e3b
    classDef dead fill:#fef2f2,stroke:#dc2626,stroke-width:2px,color:#7f1d1d

    class PENDING pending
    class PROCESSING processing
    class COMPLETED completed
    class DEAD dead
```

### 5.2 AI 预审服务端派生状态(PrereviewStatus)

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontFamily':'-apple-system, system-ui, sans-serif','fontSize':'13px','lineColor':'#94a3b8'}}}%%
flowchart TB
    START(["任务所有者触发任务级 AI 预审"]) --> OB["写 outbox 事件"]
    OB --> S{"<b>PrereviewStatus</b><br/>服务端派生<br/>由 outbox + ai_calls + ledger 推导"}

    S -- "pending" --> PEND["pending · 等待工作进程"]
    S -- "processing" --> PROC["processing · 处理中"]
    S -- "failed" --> FAIL["failed · 失败"]

    PROC --> RESOLVE["RuntimeProviderResolver<br/>registry-first 解析提供方"]
    RESOLVE --> CALL["调用 deepseek-chat<br/>结构化函数调用 function-calling"]
    CALL --> WRITE["写 ai_calls · ai_calls_in_field"]
    WRITE --> LEDGER["AI 结论自动入账<br/>quality_ledger_entries(ai_field_finding)"]
    LEDGER --> COMP["completed · 已完成"]

    classDef start fill:#f5f3ff,stroke:#7c3aed,stroke-width:1.5px,color:#4c1d95
    classDef step fill:#eff6ff,stroke:#2563eb,stroke-width:1.5px,color:#1e3a8a
    classDef pending fill:#fffbeb,stroke:#d97706,stroke-width:2px,color:#78350f
    classDef completed fill:#ecfdf5,stroke:#059669,stroke-width:2px,color:#064e3b
    classDef failed fill:#fef2f2,stroke:#dc2626,stroke-width:2px,color:#7f1d1d
    classDef fact fill:#f0fdf4,stroke:#16a34a,stroke-width:1.5px,color:#14532d

    class START start
    class OB,S,RESOLVE,CALL step
    class PEND,PROC pending
    class COMP completed
    class FAIL failed
    class WRITE,LEDGER fact
```

---

## 六、高级审核仲裁状态机

> 真相源:OpenAPI `SeniorReviewCaseStatus / Type / SourceSignal / Resolution`。闭环 258 正交化:高级审核员不做二次全审,而是处理独立案件(case)。

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontFamily':'-apple-system, system-ui, sans-serif','fontSize':'13px'}}}%%
stateDiagram-v2
    direction LR
    [*] --> PENDING_REVIEWER: 案件生成<br/>来源信号 ai_manual_review / ai_error_conflict<br/>/ reviewer_difficulty / sampling

    PENDING_REVIEWER --> OPEN: 高级审核员接入
    PENDING_REVIEWER --> CANCELED: 取消
    OPEN --> RESOLVED: 裁决
    OPEN --> CANCELED: 取消
    RESOLVED --> [*]
    CANCELED --> [*]

    note right of PENDING_REVIEWER
        案件类型 case type
        arbitration 仲裁 / sampling 抽检
    end note
    note right of RESOLVED
        裁决结果 resolution
        uphold_reviewer 维持初审
        overturn_to_reject 推翻为驳回
        boundary_approved 边界通过
        boundary_rejected 边界驳回
    end note

    classDef pending fill:#fffbeb,stroke:#d97706,stroke-width:2px,color:#78350f
    classDef open fill:#eff6ff,stroke:#2563eb,stroke-width:2px,color:#1e3a8a
    classDef resolved fill:#ecfdf5,stroke:#059669,stroke-width:2px,color:#064e3b
    classDef canceled fill:#f3f4f6,stroke:#6b7280,stroke-width:2px,color:#1f2937

    class PENDING_REVIEWER pending
    class OPEN open
    class RESOLVED resolved
    class CANCELED canceled
```

---

## 七、可信导出复现性流程

> 真相源:ADR-004、`export` 模块。导出是源事实的规范化(canonical)函数,不是可变数据库快照(闭环 259 新增多训练格式)。

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontFamily':'-apple-system, system-ui, sans-serif','fontSize':'13px','lineColor':'#94a3b8'}}}%%
flowchart TB
    OWNER(["任务所有者对任务触发导出<br/>选择训练格式"]) --> FMT{格式选择}
    FMT --> T1["表格快照"]
    FMT --> T2["OpenAI 对话微调"]
    FMT --> T3["TRL 指令微调(SFT)"]
    FMT --> T4["TRL 偏好训练"]

    T1 & T2 & T3 & T4 --> BUILD["ExportArtifactBuilder<br/>canonical JSON / JSONL"]
    BUILD --> HASH["计算三层 hash<br/>manifestHash · sourceStateHash · fileHash"]
    HASH --> S3["写入 MinIO<br/>export_snapshots 落事实"]
    S3 --> DIFF{"两次独立同参导出<br/>diff 对比"}
    DIFF -- "三层 hash 全一致<br/>10 个内容文件 SHA-256 全一致" --> OK["✅ 可复现"]

    classDef start fill:#eff6ff,stroke:#2563eb,stroke-width:1.5px,color:#1e3a8a
    classDef fmt fill:#fffbeb,stroke:#d97706,stroke-width:1.5px,color:#78350f
    classDef step fill:#f0fdfa,stroke:#0d9488,stroke-width:1.5px,color:#134e4a
    classDef fact fill:#f0fdf4,stroke:#16a34a,stroke-width:1.5px,color:#14532d
    classDef ok fill:#ecfdf5,stroke:#059669,stroke-width:2px,color:#064e3b

    class OWNER start
    class T1,T2,T3,T4 fmt
    class BUILD,HASH step
    class S3 fact
    class OK ok
```

---

## 八、端到端主链路时序

四角色协作的完整证据链。

```mermaid
%%{init: {'theme':'base','themeVariables':{'fontFamily':'-apple-system, system-ui, sans-serif','fontSize':'13px','actorBkg':'#eff6ff','actorBorder':'#2563eb','noteBkgColor':'#fffbeb','noteBorderColor':'#d97706'}}}%%
sequenceDiagram
    autonumber
    participant O as 任务所有者
    participant API as services/api
    participant DB as MySQL 事实表
    participant OB as outbox
    participant AG as services/agent
    participant L as 标注员
    participant R as 审核员
    participant S as 高级审核员

    O->>API: 发布 Schema v1 / 发布任务
    API->>DB: schema_versions(不可变)
    L->>API: 领取任务(乐观锁)
    API->>DB: 绑定领取时 Schema 快照
    L->>API: 作答 + 自动保存 + 提交
    API->>DB: submissions(绑定 v1)
    API->>OB: 同事务写 AI 预审事件

    AG->>OB: 轮询领取
    AG->>AG: 解析提供方 + 结构化函数调用
    AG->>DB: ai_calls + ledger(ai_field_finding)

    R->>API: 初审 approve / reject
    API->>DB: ledger(reviewer_overall_verdict)
    Note over DB: 裁决实时派生于最新台账记录

    R->>API: 标记疑难升级
    API->>DB: senior_review_cases(pending_reviewer)
    S->>API: 仲裁裁决
    API->>DB: 案件裁决入账

    O->>API: 创建可信导出
    API->>DB: export_snapshots(三层 hash)
    Note over O,S: 全链路只追加 · 可审计 · 可复现
```

---

## 图例说明

- **只追加事实表(append-only)**:只追加不更新 / 删除,保证审计可追溯
- **读时派生**:裁决(Verdict)等不维护物化表,从最新台账记录实时计算
- **领取时绑定(claim-time binding)**:领取时绑定 Schema 版本快照,后续任务编辑不影响在途工作
- **终态**:无任何出向迁移的状态(如 ENDED / RESOLVED)
