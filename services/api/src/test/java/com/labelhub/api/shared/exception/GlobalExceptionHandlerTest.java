package com.labelhub.api.shared.exception;

import com.labelhub.api.generated.model.ApiError;
import com.labelhub.api.module.dataset.exception.EmptyDatasetException;
import com.labelhub.api.module.dataset.exception.InvalidDatasetFileException;
import com.labelhub.api.module.dataset.exception.InvalidDatasetForTaskException;
import com.labelhub.api.module.dataset.exception.TaskPublishedLockException;
import com.labelhub.api.module.session.exception.DraftNotFoundException;
import com.labelhub.api.module.session.exception.InvalidSubmissionPayloadException;
import com.labelhub.api.module.session.exception.NoAvailableDatasetItemException;
import com.labelhub.api.module.session.exception.SessionAccessDeniedException;
import com.labelhub.api.module.session.exception.SessionAlreadySubmittedException;
import com.labelhub.api.module.session.exception.SessionNotEditableException;
import com.labelhub.api.module.session.exception.TaskNotAvailableException;
import com.labelhub.api.module.task.service.TaskPublishGuardException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void publish_guard_quota_failure_returns_bad_request_with_camel_case_field_error() {
        ResponseEntity<ApiError> response = handler.publishGuard(new TaskPublishGuardException("quota_total"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("PUBLISH_GUARD_FAILED");
        assertThat(response.getBody().getFieldErrors()).singleElement().satisfies(fieldError -> {
            assertThat(fieldError.getField()).isEqualTo("quotaTotal");
            assertThat(fieldError.getMessage()).isEqualTo("Task quota must be greater than zero");
        });
    }

    @Test
    void publish_guard_deadline_failure_returns_bad_request_with_camel_case_field_error() {
        ResponseEntity<ApiError> response = handler.publishGuard(new TaskPublishGuardException("deadline_at"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("PUBLISH_GUARD_FAILED");
        assertThat(response.getBody().getFieldErrors()).singleElement().satisfies(fieldError -> {
            assertThat(fieldError.getField()).isEqualTo("deadlineAt");
            assertThat(fieldError.getMessage()).isEqualTo("Task deadline must be in the future");
        });
    }

    @Test
    void publish_guard_schema_version_failure_returns_camel_case_field_error() {
        ResponseEntity<ApiError> response = handler.publishGuard(new TaskPublishGuardException("current_schema_version_id"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("PUBLISH_GUARD_FAILED");
        assertThat(response.getBody().getFieldErrors()).singleElement().satisfies(fieldError -> {
            assertThat(fieldError.getField()).isEqualTo("currentSchemaVersionId");
            assertThat(fieldError.getMessage()).isEqualTo("Task must have a current schema version before publishing");
        });
    }

    @Test
    void publish_guard_dataset_failure_returns_camel_case_field_error() {
        ResponseEntity<ApiError> response = handler.publishGuard(new TaskPublishGuardException("current_dataset_id"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("PUBLISH_GUARD_FAILED");
        assertThat(response.getBody().getFieldErrors()).singleElement().satisfies(fieldError -> {
            assertThat(fieldError.getField()).isEqualTo("currentDatasetId");
            assertThat(fieldError.getMessage()).isEqualTo("Task must have a current dataset before publishing");
        });
    }

    @Test
    void duplicate_key_for_schema_version_content_returns_conflict_with_specific_code() {
        DuplicateKeyException exception = new DuplicateKeyException(
            "Duplicate entry '5-abc' for key 'uk_schema_versions_hash'"
        );

        ResponseEntity<ApiError> response = handler.duplicateKey(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("DUPLICATE_SCHEMA_VERSION_CONTENT");
        assertThat(response.getBody().getMessage()).isEqualTo("Schema content is identical to an existing version");
    }

    @Test
    void duplicate_key_for_other_constraint_returns_conflict_with_generic_code() {
        DuplicateKeyException exception = new DuplicateKeyException(
            "Duplicate entry 'x' for key 'some_other_unique_key'"
        );

        ResponseEntity<ApiError> response = handler.duplicateKey(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("DATA_CONFLICT");
        assertThat(response.getBody().getMessage()).isEqualTo("Data conflict");
    }

    @Test
    void task_not_available_returns_409_with_claim_specific_code() {
        ResponseEntity<ApiError> response = handler.taskNotAvailable(new TaskNotAvailableException(10L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("TASK_NOT_AVAILABLE");
    }

    @Test
    void no_available_dataset_item_returns_409_with_claim_specific_code() {
        ResponseEntity<ApiError> response = handler.noAvailableDatasetItem(new NoAvailableDatasetItemException(10L, 500L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("NO_AVAILABLE_DATASET_ITEM");
    }

    @Test
    void session_access_denied_maps_to_not_found_to_avoid_enumeration() {
        ResponseEntity<ApiError> response = handler.notFound(new SessionAccessDeniedException(900L, 1002L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("NOT_FOUND");
    }

    @Test
    void draft_not_found_returns_404_with_specific_code() {
        ResponseEntity<ApiError> response = handler.draftNotFound(new DraftNotFoundException(900L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("DRAFT_NOT_FOUND");
    }

    @Test
    void session_not_editable_returns_409_with_specific_code() {
        ResponseEntity<ApiError> response = handler.sessionNotEditable(new SessionNotEditableException(900L, "submitted"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("SESSION_NOT_EDITABLE");
    }

    @Test
    void session_already_submitted_returns_409_with_specific_code() {
        ResponseEntity<ApiError> response = handler.sessionAlreadySubmitted(
            new SessionAlreadySubmittedException(900L, "submitted")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("SESSION_ALREADY_SUBMITTED");
    }

    @Test
    void invalid_submission_payload_returns_400_with_specific_code() {
        ResponseEntity<ApiError> response = handler.invalidSubmissionPayload(
            new InvalidSubmissionPayloadException("answerPayload is required")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_SUBMISSION_PAYLOAD");
    }

    @Test
    void invalid_dataset_file_returns_400_with_specific_code() {
        ResponseEntity<ApiError> response = handler.invalidDatasetFile(
            new InvalidDatasetFileException("Invalid JSONL at line 2", 2)
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_DATASET_FILE");
        assertThat(response.getBody().getMessage()).contains("line 2");
    }

    @Test
    void empty_dataset_returns_400_with_specific_code() {
        ResponseEntity<ApiError> response = handler.emptyDataset(new EmptyDatasetException());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("EMPTY_DATASET");
    }

    @Test
    void task_published_lock_returns_409_with_specific_code() {
        ResponseEntity<ApiError> response = handler.taskPublishedLock(new TaskPublishedLockException(10L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("TASK_PUBLISHED_LOCK");
    }

    @Test
    void invalid_dataset_for_task_returns_400_with_specific_code() {
        ResponseEntity<ApiError> response = handler.invalidDatasetForTask(new InvalidDatasetForTaskException(77L, 10L));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("INVALID_DATASET_FOR_TASK");
    }
}
