package com.labelhub.api.module.session.web;

import com.labelhub.api.generated.model.Draft;
import com.labelhub.api.generated.model.PagedSessions;
import com.labelhub.api.generated.model.SaveDraftRequest;
import com.labelhub.api.generated.model.SessionDetail;
import com.labelhub.api.generated.model.SessionStatus;
import com.labelhub.api.generated.model.Submission;
import com.labelhub.api.generated.model.SubmitSessionRequest;
import com.labelhub.api.generated.model.UploadedFile;
import com.labelhub.api.generated.web.SessionsApi;
import com.labelhub.api.module.schema.entity.SubmissionEntity;
import com.labelhub.api.module.session.entity.DraftEntity;
import com.labelhub.api.module.session.service.SessionAttachmentService;
import com.labelhub.api.module.session.service.SessionService;
import com.labelhub.api.module.submission.web.SubmissionDtoMapper;
import com.labelhub.api.security.JwtPrincipal;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;

@RestController
@RequestMapping
public class SessionsController implements SessionsApi {

    private final SessionService sessionService;
    private final SessionAttachmentService sessionAttachmentService;
    private final SessionDtoMapper dtoMapper;
    private final SubmissionDtoMapper submissionDtoMapper;

    public SessionsController(
        SessionService sessionService,
        SessionAttachmentService sessionAttachmentService,
        SessionDtoMapper dtoMapper,
        SubmissionDtoMapper submissionDtoMapper
    ) {
        this.sessionService = sessionService;
        this.sessionAttachmentService = sessionAttachmentService;
        this.dtoMapper = dtoMapper;
        this.submissionDtoMapper = submissionDtoMapper;
    }

    @Override
    @PreAuthorize("hasRole('LABELER')")
    @GetMapping(path = "/sessions/{sessionId}", produces = "application/json")
    public ResponseEntity<SessionDetail> getSession(@PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(dtoMapper.toSessionDetail(sessionService.getDetail(sessionId, currentUserId())));
    }

    @Override
    @PreAuthorize("hasRole('LABELER')")
    @GetMapping(path = "/my/sessions", produces = "application/json")
    public ResponseEntity<PagedSessions> listMySessions(
        @RequestParam(value = "page", required = false, defaultValue = "1") Integer page,
        @RequestParam(value = "size", required = false, defaultValue = "20") Integer size,
        @RequestParam(value = "status", required = false) SessionStatus status
    ) {
        String statusFilter = status == null ? null : status.getValue();
        return ResponseEntity.ok(dtoMapper.toPagedSessions(
            sessionService.listMySessions(currentUserId(), statusFilter, page, size)
        ));
    }

    @Override
    @PreAuthorize("hasRole('LABELER')")
    @GetMapping(path = "/sessions/{sessionId}/draft", produces = "application/json")
    public ResponseEntity<Draft> getSessionDraft(@PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(dtoMapper.toDraft(sessionService.getLatestDraft(sessionId, currentUserId())));
    }

    @Override
    @PreAuthorize("hasRole('LABELER')")
    @PutMapping(path = "/sessions/{sessionId}/draft", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Draft> saveDraft(
        @PathVariable("sessionId") Long sessionId,
        @Valid @RequestBody SaveDraftRequest saveDraftRequest
    ) {
        DraftEntity saved = sessionService.saveDraft(sessionId, currentUserId(), saveDraftRequest.getPayload());
        return ResponseEntity.status(HttpStatus.CREATED).body(dtoMapper.toDraft(saved));
    }

    @Override
    @PreAuthorize("hasRole('LABELER')")
    @PostMapping(path = "/sessions/{sessionId}/submit", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Submission> submitSession(
        @PathVariable("sessionId") Long sessionId,
        @Valid @RequestBody SubmitSessionRequest submitSessionRequest
    ) {
        SubmissionEntity submission = sessionService.submit(sessionId, currentUserId(), submitSessionRequest.getAnswerPayload());
        return ResponseEntity.status(HttpStatus.CREATED).body(submissionDtoMapper.toSubmission(submission));
    }

    @Override
    @PreAuthorize("hasRole('LABELER')")
    @PostMapping(path = "/sessions/{sessionId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "application/json")
    public ResponseEntity<UploadedFile> uploadSessionAttachment(
        @PathVariable("sessionId") Long sessionId,
        @RequestPart("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(sessionAttachmentService.upload(sessionId, currentUserId(), file));
    }

    private Long currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal) {
            return jwtPrincipal.userId();
        }
        throw new IllegalStateException("Authenticated principal is not a JwtPrincipal");
    }
}
