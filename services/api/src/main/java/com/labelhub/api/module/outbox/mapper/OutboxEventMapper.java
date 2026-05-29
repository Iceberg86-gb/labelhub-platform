package com.labelhub.api.module.outbox.mapper;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.labelhub.api.module.outbox.entity.OutboxEventEntity;
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
public interface OutboxEventMapper {

    @Insert("""
        INSERT INTO outbox
        (aggregate_type, aggregate_id, event_type, payload, status, retry_count, next_retry_at, locked_by, locked_at, created_at, processed_at)
        VALUES
        (#{aggregateType}, #{aggregateId}, #{eventType}, #{payload, typeHandler=com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler},
         #{status}, #{retryCount}, #{nextRetryAt}, #{lockedBy}, #{lockedAt}, #{createdAt}, #{processedAt})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(OutboxEventEntity entity);

    @Select("""
        SELECT id, aggregate_type, aggregate_id, event_type, payload, status, retry_count, next_retry_at,
               locked_by, locked_at, created_at, processed_at
        FROM outbox
        WHERE id = #{id}
        """)
    @Results(id = "outboxEventResultMap", value = {
        @Result(column = "aggregate_type", property = "aggregateType"),
        @Result(column = "aggregate_id", property = "aggregateId"),
        @Result(column = "event_type", property = "eventType"),
        @Result(column = "payload", property = "payload", typeHandler = JacksonTypeHandler.class),
        @Result(column = "retry_count", property = "retryCount"),
        @Result(column = "next_retry_at", property = "nextRetryAt"),
        @Result(column = "locked_by", property = "lockedBy"),
        @Result(column = "locked_at", property = "lockedAt"),
        @Result(column = "created_at", property = "createdAt"),
        @Result(column = "processed_at", property = "processedAt")
    })
    OutboxEventEntity selectById(@Param("id") Long id);

    @Select("""
        SELECT id, aggregate_type, aggregate_id, event_type, payload, status, retry_count, next_retry_at,
               locked_by, locked_at, created_at, processed_at
        FROM outbox
        WHERE aggregate_type = #{aggregateType}
          AND aggregate_id = #{aggregateId}
          AND event_type = #{eventType}
        ORDER BY id ASC
        """)
    @ResultMap("outboxEventResultMap")
    List<OutboxEventEntity> selectByAggregateAndEvent(
        @Param("aggregateType") String aggregateType,
        @Param("aggregateId") Long aggregateId,
        @Param("eventType") String eventType
    );
}
