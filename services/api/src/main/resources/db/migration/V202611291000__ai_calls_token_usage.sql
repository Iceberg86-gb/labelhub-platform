ALTER TABLE ai_calls
    ADD COLUMN prompt_tokens INT NULL AFTER cost_decimal,
    ADD COLUMN completion_tokens INT NULL AFTER prompt_tokens,
    ADD COLUMN total_tokens INT NULL AFTER completion_tokens,
    ADD COLUMN cache_hit_tokens INT NULL AFTER total_tokens;
