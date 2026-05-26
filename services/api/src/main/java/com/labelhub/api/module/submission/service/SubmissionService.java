package com.labelhub.api.module.submission.service;

import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.schema.mapper.SubmissionMapper;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.service.PagedResult;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class SubmissionService {

    private final TaskMapper taskMapper;
    private final SubmissionMapper submissionMapper;

    public SubmissionService(TaskMapper taskMapper, SubmissionMapper submissionMapper) {
        this.taskMapper = taskMapper;
        this.submissionMapper = submissionMapper;
    }

    public PagedResult<SubmissionEntity> listByTaskForOwner(Long taskId, Long ownerId, long page, long size) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getOwnerId(), ownerId)) {
            throw new TaskNotFoundException(taskId);
        }

        long offset = (page - 1) * size;
        List<SubmissionEntity> items = submissionMapper.selectPageByTaskId(taskId, offset, size);
        Long total = submissionMapper.selectCountByTaskId(taskId);
        return new PagedResult<>(items, total == null ? 0 : total, page, size);
    }
}
