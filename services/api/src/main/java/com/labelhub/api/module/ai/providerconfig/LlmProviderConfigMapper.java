package com.labelhub.api.module.ai.providerconfig;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface LlmProviderConfigMapper {

    @Insert("""
        INSERT INTO llm_provider_configs (
            owner_id, scope, provider_type, provider_name, base_url, model_name,
            secret_ciphertext, secret_last4, secret_updated_at, secret_ref, enabled
        )
        VALUES (
            #{ownerId}, #{scope}, #{providerType}, #{providerName}, #{baseUrl}, #{modelName},
            #{secretCiphertext}, #{secretLast4}, #{secretUpdatedAt}, #{secretRef}, #{enabled}
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(LlmProviderConfigEntity entity);

    @Select("""
        SELECT id, owner_id AS ownerId, scope, provider_type AS providerType, provider_name AS providerName,
               base_url AS baseUrl, model_name AS modelName, secret_ciphertext AS secretCiphertext,
               secret_last4 AS secretLast4, secret_updated_at AS secretUpdatedAt, secret_ref AS secretRef,
               enabled, created_at AS createdAt, updated_at AS updatedAt
        FROM llm_provider_configs
        WHERE scope = 'platform'
        ORDER BY created_at DESC, id DESC
        """)
    List<LlmProviderConfigEntity> selectPlatformProviders();

    @Select("""
        SELECT id, owner_id AS ownerId, scope, provider_type AS providerType, provider_name AS providerName,
               base_url AS baseUrl, model_name AS modelName, secret_ciphertext AS secretCiphertext,
               secret_last4 AS secretLast4, secret_updated_at AS secretUpdatedAt, secret_ref AS secretRef,
               enabled, created_at AS createdAt, updated_at AS updatedAt
        FROM llm_provider_configs
        WHERE id = #{id} AND scope = 'platform'
        """)
    LlmProviderConfigEntity selectPlatformById(@Param("id") Long id);

    @Select("""
        SELECT id
        FROM llm_provider_configs
        WHERE scope = 'platform'
          AND enabled = TRUE
          AND id <> #{targetId}
        ORDER BY id ASC
        """)
    List<Long> selectEnabledPlatformProviderIdsExcept(@Param("targetId") Long targetId);

    @Update("""
        UPDATE llm_provider_configs
        SET enabled = FALSE
        WHERE scope = 'platform'
          AND enabled = TRUE
          AND id <> #{targetId}
        """)
    int disableOtherPlatformProviderRows(@Param("targetId") Long targetId);

    default List<Long> disableOtherPlatformProviders(Long targetId) {
        List<Long> disabledIds = selectEnabledPlatformProviderIdsExcept(targetId);
        if (!disabledIds.isEmpty()) {
            disableOtherPlatformProviderRows(targetId);
        }
        return disabledIds;
    }

    @Update("""
        UPDATE llm_provider_configs
        SET enabled = TRUE
        WHERE id = #{id} AND scope = 'platform'
        """)
    int enablePlatformProvider(@Param("id") Long id);

    @Update("""
        UPDATE llm_provider_configs
        SET provider_type = #{providerType},
            provider_name = #{providerName},
            base_url = #{baseUrl},
            model_name = #{modelName},
            secret_ciphertext = #{secretCiphertext},
            secret_last4 = #{secretLast4},
            secret_updated_at = #{secretUpdatedAt},
            secret_ref = #{secretRef},
            enabled = #{enabled}
        WHERE id = #{id} AND scope = 'platform'
        """)
    int update(LlmProviderConfigEntity entity);

    @Delete("""
        DELETE FROM llm_provider_configs
        WHERE id = #{id} AND scope = 'platform'
        """)
    int deletePlatformById(@Param("id") Long id);
}
