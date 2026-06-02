package com.labelhub.agent.llm.runtime;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!local")
public class JdbcRuntimeProviderConfigRepository implements RuntimeProviderConfigRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRuntimeProviderConfigRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<RuntimeProviderConfig> findEnabledPlatformProviders() {
        return jdbcTemplate.query("""
            SELECT id, owner_id, provider_type, provider_name, base_url, model_name,
                   secret_ciphertext, secret_ref, enabled
            FROM llm_provider_configs
            WHERE scope = 'platform'
              AND enabled = TRUE
            ORDER BY id ASC
            """, this::mapRow);
    }

    private RuntimeProviderConfig mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new RuntimeProviderConfig(
            rs.getLong("id"),
            rs.getLong("owner_id"),
            rs.getString("provider_type"),
            rs.getString("provider_name"),
            rs.getString("base_url"),
            rs.getString("model_name"),
            rs.getString("secret_ciphertext"),
            rs.getString("secret_ref"),
            rs.getBoolean("enabled")
        );
    }
}
