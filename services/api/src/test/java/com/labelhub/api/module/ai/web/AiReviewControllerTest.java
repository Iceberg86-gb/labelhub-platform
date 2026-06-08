package com.labelhub.api.module.ai.web;

import com.labelhub.api.generated.model.SubmissionAiProvenance;
import com.labelhub.api.generated.model.AiReviewRule;
import com.labelhub.api.generated.model.AiReviewRuleRequest;
import com.labelhub.api.generated.model.FieldAssistRequest;
import com.labelhub.api.generated.model.FieldAssistResponse;
import com.labelhub.api.module.ai.entity.AiReviewRuleEntity;
import com.labelhub.api.module.ai.entity.PromptVersionEntity;
import com.labelhub.api.module.ai.service.AiReviewService;
import com.labelhub.api.module.ai.service.AiReviewRuleService;
import com.labelhub.api.module.ai.service.FieldAssistService;
import com.labelhub.api.module.ai.service.view.AiReviewRuleView;
import com.labelhub.api.module.ai.service.view.SubmissionAiProvenanceView;
import com.labelhub.api.module.task.service.TaskAiPrereviewEnqueueResultView;
import com.labelhub.api.module.task.service.TaskAiPrereviewService;
import com.labelhub.api.module.task.service.TaskAiPrereviewSummaryView;
import com.labelhub.api.security.JwtPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReviewControllerTest {

    private final AiReviewService aiReviewService = mock(AiReviewService.class);
    private final FieldAssistService fieldAssistService = mock(FieldAssistService.class);
    private final AiReviewRuleService aiReviewRuleService = mock(AiReviewRuleService.class);
    private final TaskAiPrereviewService taskAiPrereviewService = mock(TaskAiPrereviewService.class);
    private final AiReviewDtoMapper aiReviewDtoMapper = mock(AiReviewDtoMapper.class);
    private final AiReviewRuleDtoMapper aiReviewRuleDtoMapper = mock(AiReviewRuleDtoMapper.class);
    private final AiReviewController controller = new AiReviewController(
        aiReviewService,
        fieldAssistService,
        aiReviewDtoMapper,
        aiReviewRuleService,
        aiReviewRuleDtoMapper,
        taskAiPrereviewService
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getSubmissionAiProvenance_passes_raw_jwt_roles_to_service() {
        SubmissionAiProvenanceView view = new SubmissionAiProvenanceView(44L, List.of(), List.of());
        when(aiReviewService.getProvenance(44L, 3003L, Set.of("REVIEWER"))).thenReturn(view);
        when(aiReviewDtoMapper.toProvenance(view)).thenReturn(new SubmissionAiProvenance());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(3003L, "reviewer_demo", List.of("REVIEWER")),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_REVIEWER"))
        ));

        controller.getSubmissionAiProvenance(44L);

        verify(aiReviewService).getProvenance(44L, 3003L, Set.of("REVIEWER"));
    }

    @Test
    void saveAiReviewRule_delegates_to_service_with_current_owner() {
        AiReviewRuleRequest request = new AiReviewRuleRequest(
            44L,
            "Review prompt",
            List.of("accuracy"),
            new BigDecimal("0.8"),
            new BigDecimal("0.2")
        );
        request.setThreshold(new BigDecimal("0.8"));
        AiReviewRuleView view = view();
        AiReviewRule dto = new AiReviewRule();
        when(aiReviewRuleService.saveRule(request, 1001L)).thenReturn(view);
        when(aiReviewRuleDtoMapper.toRule(view)).thenReturn(dto);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(1001L, "owner_demo", List.of("OWNER")),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
        ));

        var response = controller.saveAiReviewRule(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(dto);
        verify(aiReviewRuleService).saveRule(request, 1001L);
    }

    @Test
    void createFieldAssistCall_delegates_to_service_with_current_labeler() {
        FieldAssistRequest request = new FieldAssistRequest(55L, "summary", java.util.Map.of("value", "draft"));
        FieldAssistResponse dto = new FieldAssistResponse();
        when(fieldAssistService.assist(request, 2002L)).thenReturn(dto);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(2002L, "labeler_demo", List.of("LABELER")),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_LABELER"))
        ));

        var response = controller.createFieldAssistCall(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isSameAs(dto);
        verify(fieldAssistService).assist(request, 2002L);
    }

    @Test
    void enqueueSubmissionAiPrereview_delegates_to_task_prereview_queue_with_current_owner() {
        TaskAiPrereviewSummaryView summary = new TaskAiPrereviewSummaryView(44L, 10L, 8L, 1L, 1L, 0L, 8L);
        when(taskAiPrereviewService.enqueueSubmission(55L, 1001L))
            .thenReturn(new TaskAiPrereviewEnqueueResultView(44L, 1L, 0L, summary));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(1001L, "owner_demo", List.of("OWNER")),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
        ));

        var response = controller.enqueueSubmissionAiPrereview(55L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getEnqueuedCount()).isEqualTo(1L);
        verify(taskAiPrereviewService).enqueueSubmission(55L, 1001L);
    }

    @Test
    void publishAiReviewRule_delegates_to_service_with_current_owner() {
        AiReviewRuleView view = view();
        AiReviewRule dto = new AiReviewRule();
        when(aiReviewRuleService.publishRule(19L, 1001L)).thenReturn(view);
        when(aiReviewRuleDtoMapper.toRule(view)).thenReturn(dto);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(1001L, "owner_demo", List.of("OWNER")),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
        ));

        var response = controller.publishAiReviewRule(19L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(dto);
        verify(aiReviewRuleService).publishRule(19L, 1001L);
    }

    @Test
    void listAiReviewRules_delegates_to_service_with_current_owner() {
        AiReviewRuleView view = view();
        AiReviewRule dto = new AiReviewRule();
        when(aiReviewRuleService.listRules(44L, 1001L)).thenReturn(List.of(view));
        when(aiReviewRuleDtoMapper.toRule(view)).thenReturn(dto);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(1001L, "owner_demo", List.of("OWNER")),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_OWNER"))
        ));

        var response = controller.listAiReviewRules(44L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsExactly(dto);
        verify(aiReviewRuleService).listRules(44L, 1001L);
    }

    private AiReviewRuleView view() {
        AiReviewRuleEntity rule = new AiReviewRuleEntity();
        rule.setId(19L);
        PromptVersionEntity promptVersion = new PromptVersionEntity();
        promptVersion.setId(7L);
        return new AiReviewRuleView(rule, promptVersion, false);
    }
}
