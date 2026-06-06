# LabelHub Core Flow Sequence

## 取证结论

- 当前提交流转状态以 `docs/workflows/state-machine-submission.md` 为准：`created` -> `under_ai_review` -> `under_human_initial` -> `under_human_review` -> `under_human_final` -> `accepted`；打回分支使用该文档中的 `under_human_initial` -> `needs_revision`、`under_human_final` -> `needs_revision`、`needs_revision` -> `superseded`。
- AI 预审输出写入证据，不直接拥有最终 verdict；这与 ADR-005、ADR-003 的 append-only ledger 设计一致。
- Reviewer 与 Senior Reviewer 在实现里对应 `reviewer`、`senior_reviewer` review level；Senior Reviewer 需要已有 reviewer approval。
- 可信导出以 `export_snapshots` 的不可变快照承载 file hash、schema versions、rule version 和 field mapping snapshot。

```mermaid
sequenceDiagram
    autonumber
    participant Owner as 任务发布者(Owner)
    participant Labeler as 标注员(Labeler)
    participant Submission as 提交状态机(Submission State Machine)
    participant AI as AI 预审(AI Pre-review)
    participant Reviewer as 审核员(Reviewer)
    participant Senior as 高级审核员(Senior Reviewer)
    participant Ledger as 质量台账(Quality Ledger) quality_ledger_entries
    participant Export as 导出快照(Export Snapshot) export_snapshots

    Owner->>Labeler: 发任务(publish task)：任务、数据集、schema、AI review rule
    Labeler->>Labeler: 领题作答(claim and answer)：claim session，保存 draft
    Labeler->>Submission: 提交答案(submit answers)
    Submission->>Submission: 状态迁移(state transition)：created -> under_ai_review
    Submission->>AI: 触发 AI 预审(trigger AI pre-review)
    AI->>Ledger: 追加 AI 证据(append AI evidence)：ai_field_finding / ai_overall_recommendation
    Note over AI,Ledger: ADR-005：AI 预审只产生证据/溯源(evidence/provenance)，不直接覆盖最终 verdict
    AI->>Submission: AI 完成或失败转人工(AI completed or fail-to-human)：under_ai_review -> under_human_initial
    Submission-->>Reviewer: 进入初审(enter initial review)：under_human_initial
    alt 初审驳回(initial review rejects)
        Reviewer->>Ledger: 追加打回 entry(append rejection entry)：reviewLevel reviewer，reviewer_overall_verdict
        Reviewer->>Submission: 状态迁移(state transition)：under_human_initial -> needs_revision
        Submission-->>Labeler: 需要修改(needs revision)：needs_revision
        Labeler->>Submission: 修改后重提(resubmit corrected version)：needs_revision -> superseded
        Submission->>Submission: 新提交进入(new submission enters)：created -> under_ai_review
    else 初审通过并进入复审/终审(initial approves and enters senior/final review)
        Reviewer->>Ledger: 追加初审通过 entry(append reviewer approval entry)：reviewLevel reviewer，reviewer_overall_verdict
        Reviewer->>Submission: 状态迁移(state transition)：under_human_initial -> under_human_review
        Reviewer->>Submission: 配置复审/终审门(configured final gate)：under_human_review -> under_human_final
        Submission-->>Senior: 进入复审/终审(enter senior/final review)：under_human_final
        alt 复审/终审驳回(senior/final review rejects)
            Senior->>Ledger: 追加打回 entry(append rejection entry)：reviewLevel senior_reviewer，reviewer_overall_verdict
            Senior->>Submission: 状态迁移(state transition)：under_human_final -> needs_revision
            Submission-->>Labeler: 需要修改(needs revision)：needs_revision
            Labeler->>Submission: 修改后重提(resubmit corrected version)：needs_revision -> superseded
            Submission->>Submission: 新提交进入(new submission enters)：created -> under_ai_review
        else 复审/终审通过(senior/final review approves)
            Senior->>Ledger: 追加复审/终审通过 entry(append senior approval entry)：reviewLevel senior_reviewer，reviewer_overall_verdict
            Senior->>Submission: 状态迁移(state transition)：under_human_final -> accepted
            Owner->>Export: 发起可信导出(start trusted export)
            Export->>Ledger: 读取派生 verdict(read derived verdict)：current_verdicts from quality_ledger_entries
            Export->>Export: 生成不可变 snapshot(create immutable snapshot)：file_hash、schema_versions、rule_version、field_mapping_snapshot、object_key
            Export-->>Owner: 返回下载与快照记录(return download and snapshot record)
        end
    end
```

## 实证来源

- 提交状态机的状态名与迁移：`docs/workflows/state-machine-submission.md`。
- AI evidence 不直接裁决最终 verdict：`docs/adr/ADR-005-ai-evidence-not-verdict.md`。
- Quality Ledger 作为 append-only evidence、`current_verdicts` 为派生视图：`docs/adr/ADR-003-quality-ledger.md`。
- Export Snapshot 不可变快照字段：`docs/adr/ADR-004-export-snapshot.md`、`docs/architecture/labelhub-complete-design-baseline.md`。
- AI evidence 落账实现：`services/api/src/main/java/com/labelhub/api/module/ai/service/AiReviewService.java`、`services/api/src/main/java/com/labelhub/api/module/quality/service/LedgerService.java`。
- Reviewer / Senior Reviewer review level：`packages/contracts/openapi/labelhub.yaml` 的 `ReviewLevel`、`services/api/src/main/java/com/labelhub/api/module/quality/service/ReviewLevels.java`、`services/api/src/main/java/com/labelhub/api/module/quality/service/LedgerService.java`。
- 导出任务与快照实现：`services/api/src/main/java/com/labelhub/api/module/export/`、`services/agent/src/main/java/com/labelhub/agent/outbox/OutboxExportWorker.java`。
