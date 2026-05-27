package com.labelhub.api.module.task.web;

import com.labelhub.api.generated.model.CreateTaskRequest;
import com.labelhub.api.generated.model.MarketplaceTask;
import com.labelhub.api.generated.model.PagedOwnerSubmissions;
import com.labelhub.api.generated.model.PagedMarketplaceTasks;
import com.labelhub.api.generated.model.PagedTasks;
import com.labelhub.api.generated.model.Session;
import com.labelhub.api.generated.model.SessionStatus;
import com.labelhub.api.generated.model.Task;
import com.labelhub.api.generated.model.TaskStatus;
import com.labelhub.api.generated.model.TaskTransition;
import com.labelhub.api.generated.model.TaskTransitionRequest;
import com.labelhub.api.generated.model.UpdateTaskCurrentDatasetRequest;
import com.labelhub.api.generated.web.TasksApi;
import com.labelhub.api.module.session.entity.SessionEntity;
import com.labelhub.api.module.session.service.SessionService;
import com.labelhub.api.module.submission.service.SubmissionService;
import com.labelhub.api.module.submission.web.SubmissionDtoMapper;
import com.labelhub.api.module.session.service.view.MarketplaceTaskView;
import com.labelhub.api.module.task.entity.TaskEntity;
import com.labelhub.api.module.task.entity.TaskTransitionEntity;
import com.labelhub.api.module.task.service.PagedResult;
import com.labelhub.api.module.task.service.TaskCreateCommand;
import com.labelhub.api.module.task.service.TaskService;
import com.labelhub.api.security.JwtPrincipal;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@RequestMapping("/tasks")
public class TasksController implements TasksApi {

    private final TaskService taskService;
    private final SessionService sessionService;
    private final SubmissionService submissionService;
    private final SubmissionDtoMapper submissionDtoMapper;

    public TasksController(
        TaskService taskService,
        SessionService sessionService,
        SubmissionService submissionService,
        SubmissionDtoMapper submissionDtoMapper
    ) {
        this.taskService = taskService;
        this.sessionService = sessionService;
        this.submissionService = submissionService;
        this.submissionDtoMapper = submissionDtoMapper;
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<Task> createTask(@Valid @RequestBody CreateTaskRequest request) {
        TaskEntity created = taskService.create(TaskCreateCommand.builder()
            .title(request.getTitle())
            .description(request.getDescription())
            .instructionRichText(request.getInstructionRichText())
            .tags(request.getTags())
            .rewardRule(request.getRewardRule())
            .deadlineAt(request.getDeadlineAt().toLocalDateTime())
            .quotaTotal(request.getQuotaTotal())
            .build(), currentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(created));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(produces = "application/json")
    public ResponseEntity<PagedTasks> listTasks(
        @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
        @RequestParam(value = "status", required = false) TaskStatus status
    ) {
        PagedResult<TaskEntity> result = taskService.list(currentUserId(), status, page, size);
        PagedTasks response = new PagedTasks();
        response.setItems(result.items().stream().map(this::toDto).toList());
        response.setTotal(result.total());
        response.setPage((int) result.page());
        response.setSize((int) result.size());
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(path = "/{taskId}", produces = "application/json")
    public ResponseEntity<Task> getTask(@PathVariable("taskId") Long taskId) {
        return ResponseEntity.ok(toDto(taskService.getById(currentUserId(), taskId)));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @DeleteMapping(path = "/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable("taskId") Long taskId) {
        taskService.deleteTask(taskId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(path = "/{taskId}/submissions", produces = "application/json")
    public ResponseEntity<PagedOwnerSubmissions> listOwnerTaskSubmissions(
        @PathVariable("taskId") Long taskId,
        @Min(1) @Valid @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @Min(1) @Max(100) @Valid @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    ) {
        return ResponseEntity.ok(submissionDtoMapper.toPagedOwnerSubmissions(
            submissionService.listByTaskForOwner(taskId, currentUserId(), page, size)
        ));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @GetMapping(path = "/{taskId}/transitions", produces = "application/json")
    public ResponseEntity<List<TaskTransition>> getTaskTransitions(@PathVariable("taskId") Long taskId) {
        return ResponseEntity.ok(taskService.listTransitions(taskId, currentUserId()).stream().map(this::toDto).toList());
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @PatchMapping(path = "/{taskId}/transition", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Task> transitionTask(@PathVariable("taskId") Long taskId, @Valid @RequestBody TaskTransitionRequest request) {
        TaskEntity transitioned = taskService.transition(taskId, request.getToStatus(), request.getReason(), currentUserId());
        return ResponseEntity.ok(toDto(transitioned));
    }

    @Override
    @PreAuthorize("hasRole('LABELER')")
    @PostMapping(path = "/{taskId}/claim", produces = "application/json")
    public ResponseEntity<Session> claimTaskItem(@PathVariable("taskId") Long taskId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(sessionService.claim(taskId, currentUserId())));
    }

    @Override
    @PreAuthorize("hasRole('OWNER')")
    @PatchMapping(path = "/{taskId}/current-dataset", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Task> updateTaskCurrentDataset(
        @PathVariable("taskId") Long taskId,
        @Valid @RequestBody UpdateTaskCurrentDatasetRequest request
    ) {
        TaskEntity updated = taskService.updateCurrentDataset(taskId, request.getDatasetId(), currentUserId());
        return ResponseEntity.ok(toDto(updated));
    }

    @Override
    @PreAuthorize("hasRole('LABELER')")
    @GetMapping(path = "/marketplace", produces = "application/json")
    public ResponseEntity<PagedMarketplaceTasks> listMarketplaceTasks(
        @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @RequestParam(value = "size", required = false, defaultValue = "20") Integer size
    ) {
        var result = sessionService.listMarketplace(currentUserId(), page, size);
        PagedMarketplaceTasks response = new PagedMarketplaceTasks();
        response.setItems(result.getRecords().stream().map(this::toDto).toList());
        response.setTotal(result.getTotal());
        response.setPage((int) result.getCurrent());
        response.setSize((int) result.getSize());
        return ResponseEntity.ok(response);
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }
        throw new IllegalStateException("Authenticated principal is not a JwtPrincipal");
    }

    private Task toDto(TaskEntity entity) {
        Task dto = new Task();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setInstructionRichText(entity.getInstructionRichText());
        dto.setTags(entity.getTags());
        dto.setRewardRule(entity.getRewardRule());
        dto.setDeadlineAt(entity.getDeadlineAt() == null ? null : entity.getDeadlineAt().atOffset(ZoneOffset.UTC));
        dto.setQuotaTotal(entity.getQuotaTotal());
        dto.setQuotaClaimed(entity.getQuotaClaimed());
        dto.setStatus(entity.getStatus());
        dto.setCurrentSchemaVersionId(entity.getCurrentSchemaVersionId());
        dto.setCurrentDatasetId(entity.getCurrentDatasetId());
        return dto;
    }

    private MarketplaceTask toDto(MarketplaceTaskView view) {
        TaskEntity entity = view.task();
        MarketplaceTask dto = new MarketplaceTask();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setDescription(entity.getDescription());
        dto.setTags(entity.getTags());
        dto.setStatus(entity.getStatus());
        dto.setDeadlineAt(entity.getDeadlineAt() == null ? null : entity.getDeadlineAt().atOffset(ZoneOffset.UTC));
        dto.setQuotaTotal(entity.getQuotaTotal());
        dto.setQuotaClaimed(entity.getQuotaClaimed());
        dto.setCurrentSchemaVersionId(entity.getCurrentSchemaVersionId());
        dto.setCurrentDatasetId(entity.getCurrentDatasetId());
        dto.setAvailableItemCount(view.availableItemCount());
        return dto;
    }

    private Session toDto(SessionEntity entity) {
        Session dto = new Session();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setDatasetItemId(entity.getDatasetItemId());
        dto.setLabelerId(entity.getLabelerId());
        dto.setSchemaVersionId(entity.getSchemaVersionId());
        dto.setStatus(SessionStatus.fromValue(entity.getStatus()));
        dto.setClaimSnapshot(entity.getClaimSnapshot());
        dto.setClaimedAt(entity.getClaimedAt() == null ? null : entity.getClaimedAt().atOffset(ZoneOffset.UTC));
        dto.setSubmittedAt(entity.getSubmittedAt() == null ? null : entity.getSubmittedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }

    private TaskTransition toDto(TaskTransitionEntity entity) {
        TaskTransition dto = new TaskTransition();
        dto.setId(entity.getId());
        dto.setTaskId(entity.getTaskId());
        dto.setFromStatus(entity.getFromStatus());
        dto.setToStatus(entity.getToStatus());
        dto.setActorId(entity.getActorId());
        dto.setReason(entity.getReason());
        dto.setCreatedAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().atOffset(ZoneOffset.UTC));
        return dto;
    }
}
