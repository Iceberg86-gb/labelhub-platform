-- ADR-020 §1/§2: per-rule escalation signals chosen by calibration (evals/reports/w3a-calibration.md).
-- Defaults keep the pre-W3 behaviour: no disagreement escalation, no agent-loop-flag escalation.
ALTER TABLE ai_review_rules
    ADD COLUMN disagreement_threshold DECIMAL(8,4) NULL AFTER reject_threshold,
    ADD COLUMN escalate_loop_flags TINYINT(1) NOT NULL DEFAULT 0 AFTER disagreement_threshold;
