package com.labelhub.api.module.schema.mapper;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.schema.entity.SchemaVersionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SchemaVersionMapper {

    @Insert("""
        INSERT INTO schema_versions
        (schema_id, version_no, schema_json, field_stable_ids, content_hash, status, published_at, created_at)
        VALUES
        (#{schemaId}, #{versionNumber},
         #{schemaJson, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{fieldStableIds, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{contentHash}, #{statusCode}, #{publishedAt}, NOW(3))
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SchemaVersionEntity entity);

    @Select("""
        SELECT id, schema_id, version_no AS version_number, schema_json, field_stable_ids,
               content_hash, status AS status_code, published_at, created_at
        FROM schema_versions
        WHERE id = #{id}
        """)
    @Results(id = "schemaVersionResultMap", value = {
        @Result(column = "schema_json", property = "schemaJson", typeHandler = JacksonTypeHandler.class),
        @Result(column = "field_stable_ids", property = "fieldStableIds", typeHandler = JacksonTypeHandler.class)
    })
    SchemaVersionEntity selectById(@Param("id") Long id);

    @Select("""
        SELECT id, schema_id, version_no AS version_number, schema_json, field_stable_ids,
               content_hash, status AS status_code, published_at, created_at
        FROM schema_versions
        WHERE schema_id = #{schemaId}
        ORDER BY version_no ASC
        """)
    @Results(id = "schemaVersionsResultMap", value = {
        @Result(column = "schema_json", property = "schemaJson", typeHandler = JacksonTypeHandler.class),
        @Result(column = "field_stable_ids", property = "fieldStableIds", typeHandler = JacksonTypeHandler.class)
    })
    List<SchemaVersionEntity> selectBySchemaId(@Param("schemaId") Long schemaId);

    @Select("""
        SELECT MAX(version_no) FROM schema_versions WHERE schema_id = #{schemaId}
        """)
    Integer selectMaxVersionNumber(@Param("schemaId") Long schemaId);

    @Select("""
        <script>
        SELECT id, schema_id, version_no AS version_number, schema_json, field_stable_ids,
               content_hash, status AS status_code, published_at, created_at
        FROM schema_versions
        WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
        ORDER BY id ASC
        </script>
        """)
    @ResultMap("schemaVersionResultMap")
    List<SchemaVersionEntity> selectByIdsOrdered(@Param("ids") List<Long> ids);
}
