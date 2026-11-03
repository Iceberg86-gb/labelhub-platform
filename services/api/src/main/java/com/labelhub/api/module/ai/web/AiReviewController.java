package com.labelhub.api.module.ai.web;

import com.labelhub.api.generated.model.AiReviewAutoPolicyDisableRequest;
import com.labelhub.api.generated.model.AiReviewAutoPolicyEnableRequest;
import com.labelhub.api.generated.model.AiReviewAutoPolicyEvent;
import com.labelhub.api.generated.model.AiReviewAutoPolicySampleStats;
import com.labelhub.api.generated.model.AiReviewAutoPolicyState;
import com.labelhub.api.module.ai.service.AutoPolicyService;
import com.labelhub.api.generated.model.AiReviewRuleShadowStats;
import com.labelhub.api.generated.model.AiReviewRuleShadowStatsGroup;
import com.labelhub.api.module.ai.service.AiReviewShadowStatsService;
import com.labelhub.api.generated.model.AiReviewResult;
import com.labelhub.api.generated.model.AiReviewRule;
import com.labelhub.api.generated.model.AiReviewRuleRequest;
import com.labelhub.api.generated.model.FieldAssistRequest;
import com.labelhub.api.generated.model.FieldAssistResponse;
import com.labelhub.api.generated.model.SubmissionAiProvenance;
import com.labelhub.api.generated.model.TaskAiPrereviewEnqueueResult;
import com.labelhub.api.generated.model.TaskAiPrereviewSummary;
import com.labelhub.api.generated.model.TriggerAiReviewRequest;
import com.labelhub.api.generated.web.AiReviewApi;
import com.labelhub.api.module.ai.service.AiReviewService;
import com.labelhub.api.module.ai.service.AiReviewRuleService;
import com.labelhub.api.module.ai.service.FieldAssistService;
import com.labelhub.api.module.task.service.TaskAiPrereviewEnqueueResultView;
import com.labelhub.api.module.task.service.TaskAiPrereviewService;
import com.labelhub.api.module.task.service.TaskAiPrereviewSummaryView;
import com.labelhub.api.security.JwtPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class AiReviewController implements AiReviewApi {

    private final AiReviewService aiReviewService;
    private final FieldAssistService fieldAssistService;
    private final AiReviewDtoMapper aiReviewDtoMapper;
    private final AiReviewRuleService aiReviewRuleService;
    private final AiReviewRuleDtoMapper aiReviewRuleDtoMapper;
    private final TaskAiPrereviewService taskAiPrereviewService;
    private final AiReviewShadowStatsService shadowStatsService;
    private final AutoPolicyService autoPolicyService;

    public AiReviewController(
        AiReviewService aiReviewService,
        FieldAssistService fieldAssistService,
        AiReviewDtoMapper aiReviewDtoMapper,
        AiReviewRuleService aiReviewRuleService,
        AiReviewRuleDtoMapper aiReviewRuleDtoMapper,
        TaskAiPrereviewService taskAiPrereviewService,
        AiReviewShadowStatsService shadowStatsService,
        AutoPolicyService autoPolicyService
    ) {
        this.aiReviewService = aiReviewService;
        this.fieldAssistService = fieldAssistService;
        this.aiReviewDtoMapper = aiReviewDtoMapper;
        this.aiReviewRuleService = aiReviewRuleService;
        this.aiReviewRuleDtoMapper = aiReviewRuleDtoMapper;
        this.taskAiPrereviewService = taskAiPrereviewService;
        this.shadowStatsService = shadowStatsService;
        this.autoPolicyService = autoPolicyService;
    }

    @Override
    public ResponseEntity<AiReviewAutoPolicyState> getAiReviewAutoPolicy(@PathVariable("ruleId") Long ruleId) {
        return ResponseEntity.ok(toAutoPolicyState(autoPolicyService.state(ruleId, currentUserId())));
    }

    @Override
    public ResponseEntity<AiReviewAutoPolicyState> enableAiReviewAutoPolicy(
        @PathVariable("ruleId") Long ruleId,
        @Valid @RequestBody AiReviewAutoPolicyEnableRequest request
    ) {
        AutoPolicyService.EnableCommand command = new AutoPolicyService.EnableCommand(
            request.getPromptVersionId(), request.getModelName(), request.getMaxError(), request.getMinSamples(), request.getSampleRate());
        return ResponseEntity.ok(toAutoPolicyState(autoPolicyService.enable(ruleId, currentUserId(), command)));
    }

    @Override
    public ResponseEntity<AiReviewAutoPolicyState> disableAiReviewAutoPolicy(
        @PathVariable("ruleId") Long ruleId,
        @Valid @RequestBody(required = false) AiReviewAutoPolicyDisableRequest request
    ) {
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(toAutoPolicyState(autoPolicyService.disable(ruleId, currentUserId(), reason)));
    }

    private AiReviewAutoPolicyState toAutoPolicyState(AutoPolicyService.State state) {
        AiReviewAutoPolicyState dto = new AiReviewAutoPolicyState();
        dto.setRuleId(state.ruleId());
        dto.setMode(AiReviewAutoPolicyState.ModeEnum.fromValue(state.mode()));
        dto.setCurrent(state.current() == null ? null : toAutoPolicyEvent(state.current()));
        dto.setEvents(state.events().stream().map(this::toAutoPolicyEvent).toList());
        if (state.samples() != null) {
            AiReviewAutoPolicySampleStats samples = new AiReviewAutoPolicySampleStats();
            samples.setSampled(Math.toIntExact(state.samples().sampled()));
            samples.setJudged(Math.toIntExact(state.samples().judged()));
            samples.setHumanRejected(Math.toIntExact(state.samples().humanRejected()));
            samples.setWilsonLower95(state.samples().wilsonLower95());
            samples.setWilsonUpper95(state.samples().wilsonUpper95());
            samples.setAutoAccepted(Math.toIntExact(state.samples().autoAccepted()));
            dto.setSamples(samples);
        }
        return dto;
    }

    private AiReviewAutoPolicyEvent toAutoPolicyEvent(com.labelhub.api.module.ai.entity.AiReviewAutoPolicyEventEntity entity) {
        AiReviewAutoPolicyEvent dto = new AiReviewAutoPolicyEvent();
        dto.setId(entity.getId());
        dto.setAction(AiReviewAutoPolicyEvent.ActionEnum.fromValue(entity.getAction()));
        dto.setPromptVersionId(entity.getPromptVersionId());
        dto.setModelName(entity.getModelName());
        dto.setMaxError(entity.getMaxError());
        dto.setMinSamples(entity.getMinSamples());
        dto.setSampleRate(entity.getSampleRate());
        dto.setEvidence(entity.getEvidence() == null ? java.util.Map.of() : entity.getEvidence());
        dto.setActorType(AiReviewAutoPolicyEvent.ActorTypeEnum.fromValue(entity.getActorType()));
        dto.setActorId(entity.getActorId());
        dto.setReason(entity.getReason());
        dto.setCreatedAt(entity.getCreatedAt() == null ? null : entity.getCreatedAt().atOffset(java.time.ZoneOffset.UTC));
        return dto;
    }

    @Override
    public ResponseEntity<AiReviewRuleShadowStats> getAiReviewRuleShadowStats(@PathVariable("ruleId") Long ruleId) {
        AiReviewShadowStatsService.View view = shadowStatsService.shadowStats(ruleId, currentUserId());
        AiReviewRuleShadowStats dto = new AiReviewRuleShadowStats();
        dto.setRuleId(view.ruleId());
        dto.setGroups(view.groups().stream().map(group -> {
            AiReviewRuleShadowStatsGroup item = new AiReviewRuleShadowStatsGroup();
            item.setPromptVersionId(group.promptVersionId());
            item.setModelName(group.modelName());
            item.setAiPassJudged(Math.toIntExact(group.aiPassJudged()));
            item.setHumanRejected(Math.toIntExact(group.humanRejected()));
            item.setErrorRate(group.errorRate());
            item.setWilsonUpper95(group.wilsonUpper95());
            return item;
        }).toList());
        return ResponseEntity.ok(dto);
    }

    @Override
    @Deprecated(since = "P-A", forRemoval = false)
    public ResponseEntity<AiReviewResult> triggerSubmissionAiReview(
        @PathVariable("submissionId") Long submissionId,
        @Valid @RequestBody TriggerAiReviewRequest triggerAiReviewRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .header("X-LabelHub-Debug-Only", "manual-ai-review")
            .body(aiReviewDtoMapper.toResult(
                aiReviewService.review(submissionId, currentUserId(), triggerAiReviewRequest.getPromptVersionId())
            ));
    }

    @Override
    public ResponseEntity<SubmissionAiProvenance> getSubmissionAiProvenance(
        @PathVariable("submissionId") Long submissionId
    ) {
        return ResponseEntity.ok(aiReviewDtoMapper.toProvenance(
            aiReviewService.getProvenance(submissionId, currentUserId(), currentUserRoles())
        ));
    }

    @Override
    public ResponseEntity<FieldAssistResponse> createFieldAssistCall(
        @Valid @RequestBody FieldAssistRequest fieldAssistRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(fieldAssistService.assist(fieldAssistRequest, currentUserId()));
    }

    @Override
    public ResponseEntity<TaskAiPrereviewEnqueueResult> enqueueSubmissionAiPrereview(
        @PathVariable("submissionId") Long submissionId
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
            .body(toDto(taskAiPrereviewService.enqueueSubmission(submissionId, currentUserId())));
    }

    @Override
    public ResponseEntity<SubmissionAiProvenance> getSubmissionAiTrace(
        @PathVariable("submissionId") Long submissionId
    ) {
        throw notImplemented();
    }

    @Override
    public ResponseEntity<AiReviewRule> saveAiReviewRule(
        @Valid @RequestBody AiReviewRuleRequest aiReviewRuleRequest
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(aiReviewRuleDtoMapper.toRule(aiReviewRuleService.saveRule(aiReviewRuleRequest, currentUserId())));
    }

    @Override
    public ResponseEntity<AiReviewRule> publishAiReviewRule(@PathVariable("ruleId") Long ruleId) {
        return ResponseEntity.ok(aiReviewRuleDtoMapper.toRule(aiReviewRuleService.publishRule(ruleId, currentUserId())));
    }

    @Override
    public ResponseEntity<List<AiReviewRule>> listAiReviewRules(@RequestParam("taskId") Long taskId) {
        return ResponseEntity.ok(aiReviewRuleService.listRules(taskId, currentUserId()).stream()
            .map(aiReviewRuleDtoMapper::toRule)
            .toList());
    }

    private ResponseStatusException notImplemented() {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "AI review draft endpoint is not implemented in M3");
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }
        throw new IllegalStateException("Authenticated principal is not a JwtPrincipal");
    }

    private Set<String> currentUserRoles() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Set.of();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return Set.copyOf(jwtPrincipal.roles());
        }
        return Set.of();
    }

    private TaskAiPrereviewEnqueueResult toDto(TaskAiPrereviewEnqueueResultView view) {
        return new TaskAiPrereviewEnqueueResult()
            .taskId(view.taskId())
            .enqueuedCount(view.enqueuedCount())
            .skippedCount(view.skippedCount())
            .summary(toDto(view.summary()));
    }

    private TaskAiPrereviewSummary toDto(TaskAiPrereviewSummaryView view) {
        return new TaskAiPrereviewSummary()
            .taskId(view.taskId())
            .totalCount(view.totalCount())
            .pendingCount(view.pendingCount())
            .processingCount(view.processingCount())
            .completedCount(view.completedCount())
            .failedCount(view.failedCount())
            .enqueueableCount(view.enqueueableCount());
    }
}
