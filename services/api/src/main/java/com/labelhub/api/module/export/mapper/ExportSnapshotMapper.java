package com.labelhub.api.module.export.mapper;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.export.entity.ExportSnapshotEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

@Mapper
public interface ExportSnapshotMapper {

    @Insert("""
        INSERT INTO export_snapshots
        (export_job_id, task_id, file_hash, manifest_hash, source_state_hash, object_key,
         file_manifest, record_counts, schema_version_ids, verdict_rule_version_id,
         data_scope, field_mapping_snapshot, canonicalization_version, generated_at)
        VALUES
        (#{exportJobId}, #{taskId}, #{fileHash}, #{manifestHash}, #{sourceStateHash}, #{objectKey},
         #{fileManifest, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{recordCounts, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{schemaVersionIds, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{verdictRuleVersionId},
         #{dataScope, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{fieldMappingSnapshot, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{canonicalizationVersion}, #{generatedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ExportSnapshotEntity entity);

    @Select("""
        SELECT id, export_job_id, task_id, file_hash, manifest_hash, source_state_hash, object_key,
               file_manifest, record_counts, schema_version_ids, verdict_rule_version_id,
               data_scope, field_mapping_snapshot, canonicalization_version, generated_at
        FROM export_snapshots
        WHERE id = #{id}
        """)
    @Results(id = "exportSnapshotResultMap", value = {
        @Result(column = "export_job_id", property = "exportJobId"),
        @Result(column = "task_id", property = "taskId"),
        @Result(column = "file_hash", property = "fileHash"),
        @Result(column = "manifest_hash", property = "manifestHash"),
        @Result(column = "source_state_hash", property = "sourceStateHash"),
        @Result(column = "object_key", property = "objectKey"),
        @Result(column = "file_manifest", property = "fileManifest", typeHandler = JacksonTypeHandler.class),
        @Result(column = "record_counts", property = "recordCounts", typeHandler = JacksonTypeHandler.class),
        @Result(column = "schema_version_ids", property = "schemaVersionIds", typeHandler = JacksonTypeHandler.class),
        @Result(column = "verdict_rule_version_id", property = "verdictRuleVersionId"),
        @Result(column = "data_scope", property = "dataScope", typeHandler = JacksonTypeHandler.class),
        @Result(column = "field_mapping_snapshot", property = "fieldMappingSnapshot", typeHandler = JacksonTypeHandler.class),
        @Result(column = "canonicalization_version", property = "canonicalizationVersion"),
        @Result(column = "generated_at", property = "generatedAt")
    })
    ExportSnapshotEntity selectById(@Param("id") Long id);

    @Select("""
        SELECT id, export_job_id, task_id, file_hash, manifest_hash, source_state_hash, object_key,
               file_manifest, record_counts, schema_version_ids, verdict_rule_version_id,
               data_scope, field_mapping_snapshot, canonicalization_version, generated_at
        FROM export_snapshots
        WHERE task_id = #{taskId}
        ORDER BY generated_at DESC, id DESC
        LIMIT #{size} OFFSET #{offset}
        """)
    @ResultMap("exportSnapshotResultMap")
    List<ExportSnapshotEntity> selectByTaskId(
        @Param("taskId") Long taskId,
        @Param("offset") Long offset,
        @Param("size") Long size
    );

    @Select("SELECT COUNT(*) FROM export_snapshots WHERE task_id = #{taskId}")
    Long selectCountByTaskId(@Param("taskId") Long taskId);

    @Delete("DELETE FROM export_snapshots WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("""
        SELECT id, export_job_id, task_id, file_hash, manifest_hash, source_state_hash, object_key,
               file_manifest, record_counts, schema_version_ids, verdict_rule_version_id,
               data_scope, field_mapping_snapshot, canonicalization_version, generated_at
        FROM export_snapshots
        WHERE task_id = #{taskId}
        ORDER BY id ASC
        """)
    @ResultMap("exportSnapshotResultMap")
    List<ExportSnapshotEntity> selectAllByTaskId(@Param("taskId") Long taskId);

    @Select("""
        SELECT id, export_job_id, task_id, file_hash, manifest_hash, source_state_hash, object_key,
               file_manifest, record_counts, schema_version_ids, verdict_rule_version_id,
               data_scope, field_mapping_snapshot, canonicalization_version, generated_at
        FROM export_snapshots
        WHERE manifest_hash = #{manifestHash}
        ORDER BY generated_at DESC, id DESC
        """)
    @ResultMap("exportSnapshotResultMap")
    List<ExportSnapshotEntity> selectByManifestHash(@Param("manifestHash") String manifestHash);
}
