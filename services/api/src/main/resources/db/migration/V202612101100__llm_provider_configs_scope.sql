ALTER TABLE llm_provider_configs
  ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'platform' AFTER owner_id,
  ADD COLUMN platform_enabled_key VARCHAR(20)
    GENERATED ALWAYS AS (
      CASE WHEN scope = 'platform' AND enabled = TRUE THEN 'platform' ELSE NULL END
    ) VIRTUAL,
  ADD CONSTRAINT chk_llm_provider_configs_scope CHECK (scope IN ('platform', 'owner')),
  ADD UNIQUE KEY uk_llm_provider_configs_platform_enabled (platform_enabled_key);
