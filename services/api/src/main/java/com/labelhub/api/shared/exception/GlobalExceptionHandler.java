package com.labelhub.api.shared.exception;

import com.labelhub.api.generated.model.ApiError;
import com.labelhub.api.generated.model.ApiFieldError;
import com.labelhub.api.module.admin.exception.PayloadTooLargeException;
import com.labelhub.api.module.ai.exception.AiInputHashMismatchException;
import com.labelhub.api.module.ai.exception.AiProviderFailureException;
import com.labelhub.api.module.ai.exception.AiReviewRuleNotFoundException;
import com.labelhub.api.module.ai.exception.InvalidAiReviewRuleException;
import com.labelhub.api.module.ai.exception.PromptVersionNotFoundException;
import com.labelhub.api.module.dataset.exception.EmptyDatasetException;
import com.labelhub.api.module.dataset.exception.InvalidDatasetFileException;
import com.labelhub.api.module.dataset.exception.InvalidDatasetForTaskException;
import com.labelhub.api.module.dataset.exception.TaskPublishedLockException;
import com.labelhub.api.module.export.exception.ExportFailureException;
import com.labelhub.api.module.export.exception.ExportSnapshotNotFoundException;
import com.labelhub.api.module.quality.exception.LedgerEntryPayloadInvalidException;
import com.labelhub.api.module.quality.exception.LedgerEntryTypeNotSupportedException;
import com.labelhub.api.module.quality.exception.SelfReviewNotAllowedException;
import com.labelhub.api.module.schema.exception.InvalidSchemaDocumentException;
import com.labelhub.api.module.schema.exception.SchemaAccessDeniedException;
import com.labelhub.api.module.schema.exception.SchemaNotFoundException;
import com.labelhub.api.module.schema.exception.SchemaVersionNotFoundException;
import com.labelhub.api.module.schema.exception.SubmissionNotFoundException;
import com.labelhub.api.module.session.exception.DraftNotFoundException;
import com.labelhub.api.module.session.exception.InvalidSessionAttachmentException;
import com.labelhub.api.module.session.exception.InvalidSubmissionPayloadException;
import com.labelhub.api.module.session.exception.NoAvailableDatasetItemException;
import com.labelhub.api.module.session.exception.SessionAccessDeniedException;
import com.labelhub.api.module.session.exception.SessionAlreadySubmittedException;
import com.labelhub.api.module.session.exception.SessionNotEditableException;
import com.labelhub.api.module.session.exception.SessionNotFoundException;
import com.labelhub.api.module.session.exception.TaskNotAvailableException;
import com.labelhub.api.module.submission.validation.AnswerValidationException;
import com.labelhub.api.module.task.service.IllegalStateTransitionException;
import com.labelhub.api.module.task.service.TaskAccessDeniedException;
import com.labelhub.api.module.task.service.TaskNotFoundException;
import com.labelhub.api.module.task.service.TaskPublishGuardException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
        List<ApiFieldError> fields = exception.getBindingResult().getFieldErrors().stream()
            .map(error -> {
                ApiFieldError field = new ApiFieldError();
                field.setField(error.getField());
                field.setMessage(error.getDefaultMessage());
                return field;
            })
            .toList();
        return ResponseEntity.badRequest().body(error("VALIDATION_FAILED", "Request validation failed", fields));
    }

    @ExceptionHandler(IllegalStateTransitionException.class)
    ResponseEntity<ApiError> conflict(IllegalStateTransitionException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("STATE_CONFLICT", exception.getMessage()));
    }

    @ExceptionHandler(TaskNotAvailableException.class)
    ResponseEntity<ApiError> taskNotAvailable(TaskNotAvailableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("TASK_NOT_AVAILABLE", exception.getMessage()));
    }

    @ExceptionHandler(NoAvailableDatasetItemException.class)
    ResponseEntity<ApiError> noAvailableDatasetItem(NoAvailableDatasetItemException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("NO_AVAILABLE_DATASET_ITEM", exception.getMessage()));
    }

    @ExceptionHandler(SessionNotEditableException.class)
    ResponseEntity<ApiError> sessionNotEditable(SessionNotEditableException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("SESSION_NOT_EDITABLE", exception.getMessage()));
    }

    @ExceptionHandler(SessionAlreadySubmittedException.class)
    ResponseEntity<ApiError> sessionAlreadySubmitted(SessionAlreadySubmittedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error("SESSION_ALREADY_SUBMITTED", exception.getMessage()));
    }

    @ExceptionHandler(InvalidSubmissionPayloadException.class)
    ResponseEntity<ApiError> invalidSubmissionPayload(InvalidSubmissionPayloadException exception) {
        return ResponseEntity.badRequest().body(error("INVALID_SUBMISSION_PAYLOAD", exception.getMessage()));
    }

    @ExceptionHandler(InvalidSessionAttachmentException.class)
    ResponseEntity<ApiError> invalidSessionAttachment(InvalidSessionAttachmentException exception) {
        return ResponseEntity.badRequest().body(error("INVALID_SESSION_ATTACHMENT", exception.getMessage()));
    }

    @ExceptionHandler(AnswerValidationException.class)
    ResponseEntity<ApiError> answerValidation(AnswerValidationException exception) {
        List<ApiFieldError> fields = exception.getErrors().stream()
            .map(error -> {
                ApiFieldError field = new ApiFieldError();
                field.setField(error.stableId());
                field.setMessage(error.reason());
                return field;
            })
            .toList();
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(error("VALIDATION_FAILED", "Answer payload validation failed", fields));
    }

    @ExceptionHandler(InvalidDatasetFileException.class)
    ResponseEntity<ApiError> invalidDatasetFile(InvalidDatasetFileException exception) {
        return ResponseEntity.badRequest().body(error("INVALID_DATASET_FILE", exception.getMessage()));
    }

    @ExceptionHandler(EmptyDatasetException.class)
    ResponseEntity<ApiError> emptyDataset(EmptyDatasetException exception) {
        return ResponseEntity.badRequest().body(error("EMPTY_DATASET", exception.getMessage()));
    }

    @ExceptionHandler(InvalidDatasetForTaskException.class)
    ResponseEntity<ApiError> invalidDatasetForTask(InvalidDatasetForTaskException exception) {
        return ResponseEntity.badRequest().body(error("INVALID_DATASET_FOR_TASK", exception.getMessage()));
    }

    @ExceptionHandler(TaskPublishedLockException.class)
    ResponseEntity<ApiError> taskPublishedLock(TaskPublishedLockException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error("TASK_PUBLISHED_LOCK", exception.getMessage()));
    }

    @ExceptionHandler(AiProviderFailureException.class)
    ResponseEntity<ApiError> aiProviderFailure(AiProviderFailureException exception) {
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
            .body(error("AI_PROVIDER_FAILURE", "AI provider unavailable"));
    }

    @ExceptionHandler(AiInputHashMismatchException.class)
    ResponseEntity<ApiError> aiInputHashMismatch(AiInputHashMismatchException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error("AI_PROVIDER_INPUT_HASH_MISMATCH", "Cannot reuse AI result, input changed"));
    }

    @ExceptionHandler(InvalidAiReviewRuleException.class)
    ResponseEntity<ApiError> invalidAiReviewRule(InvalidAiReviewRuleException exception) {
        ApiFieldError fieldError = new ApiFieldError();
        fieldError.setField(exception.getField());
        fieldError.setMessage(exception.getReason());
        return ResponseEntity.badRequest()
            .body(error("INVALID_AI_REVIEW_RULE", "AI review rule is invalid", List.of(fieldError)));
    }

    @ExceptionHandler(SelfReviewNotAllowedException.class)
    ResponseEntity<ApiError> selfReviewNotAllowed(SelfReviewNotAllowedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(error("SELF_REVIEW_NOT_ALLOWED", exception.getMessage()));
    }

    @ExceptionHandler(LedgerEntryTypeNotSupportedException.class)
    ResponseEntity<ApiError> ledgerEntryTypeNotSupported(LedgerEntryTypeNotSupportedException exception) {
        return ResponseEntity.badRequest()
            .body(error("LEDGER_ENTRY_TYPE_NOT_SUPPORTED", exception.getMessage()));
    }

    @ExceptionHandler(LedgerEntryPayloadInvalidException.class)
    ResponseEntity<ApiError> ledgerEntryPayloadInvalid(LedgerEntryPayloadInvalidException exception) {
        return ResponseEntity.badRequest()
            .body(error("LEDGER_ENTRY_PAYLOAD_INVALID", exception.getMessage()));
    }

    @ExceptionHandler(ExportSnapshotNotFoundException.class)
    ResponseEntity<ApiError> exportSnapshotNotFound(ExportSnapshotNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(error("EXPORT_SNAPSHOT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(ExportFailureException.class)
    ResponseEntity<ApiError> exportFailure(ExportFailureException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error("EXPORT_FAILED", exception.getMessage()));
    }

    @ExceptionHandler(DraftNotFoundException.class)
    ResponseEntity<ApiError> draftNotFound(DraftNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("DRAFT_NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiError> duplicateKey(DuplicateKeyException exception) {
        String message = exception.getMessage();
        if (message != null && message.contains("uk_schema_versions_hash")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(error("DUPLICATE_SCHEMA_VERSION_CONTENT",
                    "Schema content is identical to an existing version"));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("DATA_CONFLICT", "Data conflict"));
    }

    @ExceptionHandler(TaskPublishGuardException.class)
    ResponseEntity<ApiError> publishGuard(TaskPublishGuardException exception) {
        ApiFieldError fieldError = new ApiFieldError();
        fieldError.setField(guardFieldOf(exception.getGuardName()));
        fieldError.setMessage(guardMessageOf(exception.getGuardName()));
        return ResponseEntity.badRequest()
            .body(error("PUBLISH_GUARD_FAILED", "Publish prerequisites not met", List.of(fieldError)));
    }

    @ExceptionHandler(InvalidSchemaDocumentException.class)
    ResponseEntity<ApiError> invalidSchemaDocument(InvalidSchemaDocumentException exception) {
        ApiFieldError fieldError = new ApiFieldError();
        fieldError.setField(exception.getField());
        fieldError.setMessage(exception.getReason());
        return ResponseEntity.badRequest()
            .body(error("INVALID_SCHEMA_DOCUMENT", "Schema document is invalid", List.of(fieldError)));
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    ResponseEntity<ApiError> payloadTooLarge(PayloadTooLargeException exception) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(error("PAYLOAD_TOO_LARGE", exception.getMessage()));
    }

    @ExceptionHandler({TaskNotFoundException.class, SchemaNotFoundException.class, SchemaVersionNotFoundException.class,
        SchemaAccessDeniedException.class, SubmissionNotFoundException.class, SessionNotFoundException.class,
        SessionAccessDeniedException.class, PromptVersionNotFoundException.class, AiReviewRuleNotFoundException.class,
        NoResourceFoundException.class})
    ResponseEntity<ApiError> notFound(Exception exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("NOT_FOUND", exception.getMessage()));
    }

    @ExceptionHandler({TaskAccessDeniedException.class, AccessDeniedException.class})
    ResponseEntity<ApiError> forbidden(RuntimeException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error("FORBIDDEN", exception.getMessage()));
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiError> unauthorized(AuthenticationException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error("UNAUTHORIZED", exception.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> internal(Exception exception) {
        log.error("Unhandled API exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error("INTERNAL_ERROR", "Internal server error"));
    }

    private ApiError error(String code, String message) {
        return error(code, message, List.of());
    }

    private ApiError error(String code, String message, List<ApiFieldError> fieldErrors) {
        ApiError error = new ApiError();
        error.setCode(code);
        error.setMessage(message);
        error.setFieldErrors(fieldErrors);
        return error;
    }

    private String guardFieldOf(String guardName) {
        return switch (guardName) {
            case "quota_total" -> "quotaTotal";
            case "deadline_at" -> "deadlineAt";
            case "current_schema_version_id" -> "currentSchemaVersionId";
            case "current_dataset_id" -> "currentDatasetId";
            default -> guardName;
        };
    }

    private String guardMessageOf(String guardName) {
        return switch (guardName) {
            case "quota_total" -> "Task quota must be greater than zero";
            case "deadline_at" -> "Task deadline must be in the future";
            case "current_schema_version_id" -> "Task must have a current schema version before publishing";
            case "current_dataset_id" -> "Task must have a current dataset before publishing";
            default -> "Publish guard failed: " + guardName;
        };
    }
}
