package com.labelhub.api.module.task.service;

import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.mapper.TaskMapper;
import com.labelhub.api.module.task.mapper.TaskWorkflowProgressMapper;
import com.labelhub.api.module.task.mapper.TaskWorkflowProgressRow;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class TaskWorkflowProgressService {

    private final TaskMapper taskMapper;
    private final TaskWorkflowProgressMapper progressMapper;

    public TaskWorkflowProgressService(TaskMapper taskMapper, TaskWorkflowProgressMapper progressMapper) {
        this.taskMapper = taskMapper;
        this.progressMapper = progressMapper;
    }

    public TaskWorkflowProgressView getProgress(Long taskId, Long ownerId) {
        TaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !Objects.equals(task.getOwnerId(), ownerId)) {
            throw new TaskNotFoundException(taskId);
        }

        TaskWorkflowProgressRow row = progressMapper.selectByTaskId(taskId);
        Integer quotaTotal = row == null || row.getQuotaTotal() == null ? safeInt(task.getQuotaTotal()) : row.getQuotaTotal();
        Integer quotaClaimed = row == null || row.getQuotaClaimed() == null ? safeInt(task.getQuotaClaimed()) : row.getQuotaClaimed();
        return new TaskWorkflowProgressView(
            taskId,
            quotaTotal,
            quotaClaimed,
            valueOr(row == null ? null : row.getUnclaimedCount(), unclaimed(quotaTotal, quotaClaimed)),
            valueOr(row == null ? null : row.getLabelingCount(), 0L),
            valueOr(row == null ? null : row.getSubmittedCount(), 0L),
            valueOr(row == null ? null : row.getAiPrereviewCompletedCount(), 0L),
            valueOr(row == null ? null : row.getPendingReviewCount(), 0L),
            valueOr(row == null ? null : row.getPendingSeniorReviewCount(), 0L),
            valueOr(row == null ? null : row.getApprovedCount(), 0L),
            valueOr(row == null ? null : row.getRejectedCount(), 0L)
        );
    }

    private Integer safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private Long valueOr(Long value, Long fallback) {
        return value == null ? fallback : value;
    }

    private Long unclaimed(Integer quotaTotal, Integer quotaClaimed) {
        return Math.max(0L, (long) safeInt(quotaTotal) - safeInt(quotaClaimed));
    }
}
