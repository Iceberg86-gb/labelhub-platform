package com.labelhub.api.module.export.mapper;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.export.entity.ExportJobEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.ResultMap;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ExportJobMapper {

    @Insert("""
        INSERT INTO export_jobs
        (task_id, requested_by, format, status, parameters, created_at, started_at,
         completed_at, file_key, file_size, download_count)
        VALUES
        (#{taskId}, #{requestedBy}, #{format}, #{status},
         #{parameters, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{createdAt}, #{startedAt}, #{completedAt}, #{fileKey}, #{fileSize}, #{downloadCount})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ExportJobEntity entity);

    @Select("""
        SELECT id, task_id, requested_by, format, status, parameters, created_at, started_at,
               completed_at, file_key, file_size, download_count
        FROM export_jobs
        WHERE id = #{id}
        """)
    @Results(id = "exportJobResultMap", value = {
        @Result(column = "task_id", property = "taskId"),
        @Result(column = "requested_by", property = "requestedBy"),
        @Result(column = "parameters", property = "parameters", typeHandler = JacksonTypeHandler.class),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "started_at", property = "startedAt"),
        @Result(column = "completed_at", property = "completedAt"),
        @Result(column = "file_key", property = "fileKey"),
        @Result(column = "file_size", property = "fileSize"),
        @Result(column = "download_count", property = "downloadCount")
    })
    ExportJobEntity selectById(@Param("id") Long id);

    @Select("""
        SELECT id, task_id, requested_by, format, status, parameters, created_at, started_at,
               completed_at, file_key, file_size, download_count
        FROM export_jobs
        WHERE task_id = #{taskId}
        ORDER BY created_at DESC, id DESC
        LIMIT #{size} OFFSET #{offset}
        """)
    @ResultMap("exportJobResultMap")
    List<ExportJobEntity> selectByTaskId(
        @Param("taskId") Long taskId,
        @Param("offset") Long offset,
        @Param("size") Long size
    );

    @Select("SELECT COUNT(*) FROM export_jobs WHERE task_id = #{taskId}")
    Long selectCountByTaskId(@Param("taskId") Long taskId);

    @Update("""
        UPDATE export_jobs
        SET status = 'queued'
        WHERE id = #{jobId} AND status = 'created'
        """)
    int markQueued(@Param("jobId") Long jobId);

    @Update("""
        UPDATE export_jobs
        SET status = 'running', started_at = #{startedAt}
        WHERE id = #{jobId} AND status IN ('created', 'queued', 'failed')
        """)
    int markRunning(@Param("jobId") Long jobId, @Param("startedAt") java.time.LocalDateTime startedAt);

    @Update("""
        UPDATE export_jobs
        SET status = 'succeeded', completed_at = #{completedAt}, file_key = #{fileKey}, file_size = #{fileSize}
        WHERE id = #{jobId} AND status = 'running'
        """)
    int markSucceeded(
        @Param("jobId") Long jobId,
        @Param("completedAt") java.time.LocalDateTime completedAt,
        @Param("fileKey") String fileKey,
        @Param("fileSize") Long fileSize
    );

    @Update("""
        UPDATE export_jobs
        SET status = 'failed', completed_at = #{completedAt}
        WHERE id = #{jobId} AND status = 'running'
        """)
    int markFailed(@Param("jobId") Long jobId, @Param("completedAt") java.time.LocalDateTime completedAt);

    @Select("""
        SELECT id, task_id, requested_by, format, status, parameters, created_at, started_at,
               completed_at, file_key, file_size, download_count
        FROM export_jobs
        WHERE task_id = #{taskId}
        ORDER BY id ASC
        """)
    @ResultMap("exportJobResultMap")
    List<ExportJobEntity> selectAllByTaskId(@Param("taskId") Long taskId);
}
