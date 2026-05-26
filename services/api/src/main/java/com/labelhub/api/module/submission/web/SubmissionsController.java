package com.labelhub.api.module.submission.web;

import com.labelhub.api.generated.model.Submission;
import com.labelhub.api.generated.model.SubmissionRenderSchema;
import com.labelhub.api.generated.web.SubmissionsApi;
import com.labelhub.api.module.schema.service.SchemaService;
import com.labelhub.api.module.schema.web.SchemaDtoMapper;
import com.labelhub.api.module.session.service.SessionService;
import com.labelhub.api.security.JwtPrincipal;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/submissions")
public class SubmissionsController implements SubmissionsApi {

    private final SchemaService schemaService;
    private final SessionService sessionService;
    private final SchemaDtoMapper schemaDtoMapper;
    private final SubmissionDtoMapper submissionDtoMapper;

    public SubmissionsController(
        SchemaService schemaService,
        SessionService sessionService,
        SchemaDtoMapper schemaDtoMapper,
        SubmissionDtoMapper submissionDtoMapper
    ) {
        this.schemaService = schemaService;
        this.sessionService = sessionService;
        this.schemaDtoMapper = schemaDtoMapper;
        this.submissionDtoMapper = submissionDtoMapper;
    }

    @Override
    @GetMapping(path = "/{submissionId}", produces = "application/json")
    public ResponseEntity<Submission> getSubmission(@PathVariable("submissionId") Long submissionId) {
        return ResponseEntity.ok(submissionDtoMapper.toSubmission(
            sessionService.getSubmissionForLabeler(submissionId, currentUserId())
        ));
    }

    @Override
    @GetMapping(path = "/{submissionId}/render-schema", produces = "application/json")
    public ResponseEntity<SubmissionRenderSchema> getSubmissionRenderSchema(@PathVariable("submissionId") Long submissionId) {
        return ResponseEntity.ok(schemaDtoMapper.toSubmissionRenderSchema(
            schemaService.renderForSubmission(submissionId, currentUserId(), currentUserRoles())
        ));
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
}
