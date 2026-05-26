package com.labelhub.api.module.ai.web;

import com.labelhub.api.generated.model.SubmissionAiProvenance;
import com.labelhub.api.module.ai.service.AiReviewService;
import com.labelhub.api.module.ai.service.view.SubmissionAiProvenanceView;
import com.labelhub.api.security.JwtPrincipal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiReviewControllerTest {

    private final AiReviewService aiReviewService = mock(AiReviewService.class);
    private final AiReviewDtoMapper aiReviewDtoMapper = mock(AiReviewDtoMapper.class);
    private final AiReviewController controller = new AiReviewController(aiReviewService, aiReviewDtoMapper);

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
}
