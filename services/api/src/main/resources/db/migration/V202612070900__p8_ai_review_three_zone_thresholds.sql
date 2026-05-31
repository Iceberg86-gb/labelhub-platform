ALTER TABLE ai_review_rules
    ADD COLUMN pass_threshold DECIMAL(8,4) NULL AFTER threshold,
    ADD COLUMN reject_threshold DECIMAL(8,4) NULL AFTER pass_threshold;
