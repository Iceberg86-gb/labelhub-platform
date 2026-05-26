package com.labelhub.api.module.task.entity;

import com.labelhub.api.generated.model.TaskStatus;
import java.time.LocalDateTime;

public class TaskTransitionEntity {

    private Long id;
    private Long taskId;
    private String fromStatus;
    private String toStatus;
    private Long actorId;
    private String reason;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTaskId() { return taskId; }
    public void setTaskId(Long taskId) { this.taskId = taskId; }
    public TaskStatus getFromStatus() { return fromStatus == null ? null : TaskStatus.fromValue(fromStatus); }
    public void setFromStatus(TaskStatus fromStatus) { this.fromStatus = fromStatus == null ? null : fromStatus.getValue(); }
    public String getFromStatusCode() { return fromStatus; }
    public void setFromStatusCode(String fromStatus) { this.fromStatus = fromStatus; }
    public TaskStatus getToStatus() { return TaskStatus.fromValue(toStatus); }
    public void setToStatus(TaskStatus toStatus) { this.toStatus = toStatus == null ? null : toStatus.getValue(); }
    public String getToStatusCode() { return toStatus; }
    public void setToStatusCode(String toStatus) { this.toStatus = toStatus; }
    public Long getActorId() { return actorId; }
    public void setActorId(Long actorId) { this.actorId = actorId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
