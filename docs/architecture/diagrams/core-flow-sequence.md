# LabelHub Core Flow Sequence

## 取证结论

- 当前提交流转状态以 `docs/workflows/state-machine-submission.md` 为准：`created` -> `under_ai_review` -> `under_human_initial` -> `under_human_review` -> `under_human_final` -> `accepted`。
- AI 预审输出写入证据，不直接拥有最终 verdict；这与 ADR-005、ADR-003 的 append-only ledger 设计一致。
- Reviewer 与 Senior Reviewer 在实现里对应 `reviewer`、`senior_reviewer` review level；Senior Reviewer 需要已有 reviewer approval。
- 可信导出以 `export_snapshots` 的不可变快照承载 file hash、schema versions、rule version 和 field mapping snapshot。

```mermaid
sequenceDiagram
    autonumber
    participant Owner as Owner
    participant Labeler as Labeler
    participant AI as AI 预审
    participant Reviewer as Reviewer
    participant Senior as Senior Reviewer
    participant Ledger as 质量台账 quality_ledger_entries
    participant Export as 导出快照 export_snapshots

    Owner->>Labeler: 发任务：发布任务、数据集、schema、AI review rule
    Labeler->>Labeler: 领题作答：claim session，保存 draft
    Labeler->>AI: 提交答案：submission status created -> under_ai_review
    AI->>Ledger: 写入 ai_field_finding / ai_overall_recommendation evidence
    Note over AI,Ledger: ADR-005：AI 预审只产生 evidence/provenance，不直接覆盖最终 verdict
    AI-->>Reviewer: AI 完成或失败转人工：under_ai_review -> under_human_initial
    Reviewer->>Ledger: 初审通过：reviewLevel reviewer，追加 reviewer_overall_verdict
    Ledger-->>Reviewer: under_human_initial -> under_human_review
    Reviewer->>Senior: 争议或配置 final gate：under_human_review -> under_human_final
    Senior->>Ledger: 复审/终审通过：reviewLevel senior_reviewer，追加 reviewer_overall_verdict
    Ledger-->>Senior: under_human_final -> accepted
    Ledger-->>Export: current_verdicts 由 quality_ledger_entries 派生
    Owner->>Export: 发起可信导出
    Export->>Export: 生成 export job 与不可变 snapshot：file_hash、schema_versions、rule_version、field_mapping_snapshot、object_key
    Export-->>Owner: 返回可信导出下载与快照记录
```

## 实证来源

- 提交状态机的状态名与迁移：`docs/workflows/state-machine-submission.md`。
- AI evidence 不直接裁决最终 verdict：`docs/adr/ADR-005-ai-evidence-not-verdict.md`。
- Quality Ledger 作为 append-only evidence、`current_verdicts` 为派生视图：`docs/adr/ADR-003-quality-ledger.md`。
- Export Snapshot 不可变快照字段：`docs/adr/ADR-004-export-snapshot.md`、`docs/architecture/labelhub-complete-design-baseline.md`。
- AI evidence 落账实现：`services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java`、`services/api/src/main/java/com/labelhub/api/module/quality/service/LedgerService.java`。
- Reviewer / Senior Reviewer review level：`packages/contracts/openapi/labelhub.yaml` 的 `ReviewLevel`、`services/api/src/main/java/com/labelhub/api/module/quality/service/ReviewLevels.java`、`services/api/src/main/java/com/labelhub/api/module/quality/service/LedgerService.java`。
- 导出任务与快照实现：`services/api/src/main/java/com/labelhub/api/module/export/`、`services/agent/src/main/java/com/labelhub/agent/outbox/OutboxExportWorker.java`。
