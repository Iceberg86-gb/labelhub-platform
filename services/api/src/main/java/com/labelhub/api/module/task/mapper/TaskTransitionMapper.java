package com.labelhub.api.module.task.mapper;

import com.labelhub.api.module.task.entity.TaskTransitionEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TaskTransitionMapper {

    @Insert("""
        INSERT INTO task_transitions (task_id, from_status, to_status, actor_id, reason)
        VALUES (#{taskId}, #{fromStatusCode}, #{toStatusCode}, #{actorId}, #{reason})
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(TaskTransitionEntity transition);

    @Select("""
        SELECT id, task_id, from_status AS from_status_code, to_status AS to_status_code, actor_id, reason, created_at
        FROM task_transitions
        WHERE task_id = #{taskId}
        ORDER BY created_at ASC, id ASC
        """)
    List<TaskTransitionEntity> selectByTaskId(Long taskId);
}
