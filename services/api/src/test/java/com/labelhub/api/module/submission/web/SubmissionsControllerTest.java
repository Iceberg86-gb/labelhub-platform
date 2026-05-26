package com.labelhub.api.module.submission.web;

import com.labelhub.api.generated.model.SubmissionRenderSchema;
import com.labelhub.api.module.schema.service.SchemaService;
import com.labelhub.api.module.schema.service.view.SubmissionRenderSchemaView;
import com.labelhub.api.module.schema.web.SchemaDtoMapper;
import com.labelhub.api.module.session.service.SessionService;
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

class SubmissionsControllerTest {

    private final SchemaService schemaService = mock(SchemaService.class);
    private final SessionService sessionService = mock(SessionService.class);
    private final SchemaDtoMapper schemaDtoMapper = mock(SchemaDtoMapper.class);
    private final SubmissionDtoMapper submissionDtoMapper = mock(SubmissionDtoMapper.class);
    private final SubmissionsController controller = new SubmissionsController(
        schemaService,
        sessionService,
        schemaDtoMapper,
        submissionDtoMapper
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getSubmissionRenderSchema_passes_raw_jwt_roles_to_service() {
        SubmissionRenderSchemaView view = mock(SubmissionRenderSchemaView.class);
        when(schemaService.renderForSubmission(55L, 3003L, Set.of("REVIEWER"))).thenReturn(view);
        when(schemaDtoMapper.toSubmissionRenderSchema(view)).thenReturn(new SubmissionRenderSchema());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new JwtPrincipal(3003L, "reviewer_demo", List.of("REVIEWER")),
            null,
            List.of(new SimpleGrantedAuthority("ROLE_REVIEWER"))
        ));

        controller.getSubmissionRenderSchema(55L);

        verify(schemaService).renderForSubmission(55L, 3003L, Set.of("REVIEWER"));
    }
}
