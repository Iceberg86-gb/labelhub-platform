package com.labelhub.api.module.dataset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.dataset.entity.DatasetItemEntity;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DatasetItemMapper extends BaseMapper<DatasetItemEntity> {

    @Select("""
        SELECT id, dataset_id, task_id, ordinal, item_payload, item_hash, status, created_at
        FROM dataset_items
        WHERE dataset_id = #{datasetId}
          AND task_id = #{taskId}
          AND status = 'available'
        ORDER BY ordinal ASC, id ASC
        LIMIT 1
        FOR UPDATE
        """)
    @Results(id = "datasetItemResultMap", value = {
        @Result(column = "id", property = "id"),
        @Result(column = "dataset_id", property = "datasetId"),
        @Result(column = "task_id", property = "taskId"),
        @Result(column = "ordinal", property = "ordinal"),
        @Result(column = "item_payload", property = "itemPayload", typeHandler = JacksonTypeHandler.class),
        @Result(column = "item_hash", property = "itemHash"),
        @Result(column = "status", property = "status"),
        @Result(column = "created_at", property = "createdAt")
    })
    DatasetItemEntity selectNextAvailableForUpdate(@Param("datasetId") Long datasetId, @Param("taskId") Long taskId);

    @Select("""
        SELECT COUNT(*)
        FROM dataset_items
        WHERE dataset_id = #{datasetId}
          AND task_id = #{taskId}
          AND status = 'available'
        """)
    int countAvailable(@Param("datasetId") Long datasetId, @Param("taskId") Long taskId);

    @Update("""
        UPDATE dataset_items
        SET status = #{status}
        WHERE id = #{id}
        """)
    int updateStatus(@Param("id") Long id, @Param("status") String status);

    @Select("""
        <script>
        SELECT id, dataset_id, task_id, ordinal, item_payload, item_hash, status, created_at
        FROM dataset_items
        WHERE id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">
            #{id}
        </foreach>
        ORDER BY id ASC
        </script>
        """)
    @ResultMap("datasetItemResultMap")
    List<DatasetItemEntity> selectByIdsOrdered(@Param("ids") List<Long> ids);
}
