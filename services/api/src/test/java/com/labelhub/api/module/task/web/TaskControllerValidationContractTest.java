package com.labelhub.api.module.task.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.labelhub.api.generated.model.CreateTaskRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TaskControllerValidationContractTest {

    @Test
    void create_task_request_keeps_deadline_required_contract() throws Exception {
        Method getter = CreateTaskRequest.class.getMethod("getDeadlineAt");

        assertThat(getter.getAnnotation(NotNull.class))
            .as("deadlineAt must remain required so create-task validation returns 400 before controller logic")
            .isNotNull();
        assertThat(getter.getAnnotation(Schema.class).requiredMode())
            .isEqualTo(Schema.RequiredMode.REQUIRED);
    }

    @Test
    void openapi_exposes_owner_task_workflow_progress_endpoint() throws Exception {
        String contract = Files.readString(openapiContractPath());

        assertThat(contract).contains("/tasks/{taskId}/workflow-progress:");
        assertThat(contract).contains("operationId: getTaskWorkflowProgress");
        assertThat(contract).contains("$ref: '#/components/schemas/TaskWorkflowProgress'");
    }

    @Test
    void openapi_exposes_owner_task_ai_prereview_batch_endpoints() throws Exception {
        String contract = Files.readString(openapiContractPath());

        assertThat(contract).contains("/tasks/{taskId}/ai-prereview/summary:");
        assertThat(contract).contains("operationId: getTaskAiPrereviewSummary");
        assertThat(contract).contains("/tasks/{taskId}/ai-prereview/enqueue:");
        assertThat(contract).contains("operationId: enqueueTaskAiPrereviews");
        assertThat(contract).contains("$ref: '#/components/schemas/TaskAiPrereviewSummary'");
        assertThat(contract).contains("$ref: '#/components/schemas/TaskAiPrereviewEnqueueResult'");
    }

    @Test
    void openapi_exposes_submission_ai_prereview_enqueue_endpoint() throws Exception {
        String contract = Files.readString(openapiContractPath());

        assertThat(contract).contains("/submissions/{submissionId}/ai-prereview/enqueue:");
        assertThat(contract).contains("operationId: enqueueSubmissionAiPrereview");
        assertThat(contract).contains("$ref: '#/components/schemas/TaskAiPrereviewEnqueueResult'");
    }

    @Test
    void openapi_exposes_labeler_batch_claim_endpoint() throws Exception {
        String contract = Files.readString(openapiContractPath());

        assertThat(contract).contains("/tasks/{taskId}/claim-batch:");
        assertThat(contract).contains("operationId: claimTaskItems");
        assertThat(contract).contains("$ref: '#/components/schemas/ClaimTaskItemsRequest'");
        assertThat(contract).contains("$ref: '#/components/schemas/ClaimTaskItemsResult'");
    }

    @Test
    void openapi_exposes_labeler_batch_submit_drafts_endpoint() throws Exception {
        String contract = Files.readString(openapiContractPath());

        assertThat(contract).contains("/tasks/{taskId}/submit-drafts:");
        assertThat(contract).contains("operationId: submitTaskDrafts");
        assertThat(contract).contains("$ref: '#/components/schemas/SubmitTaskDraftsRequest'");
        assertThat(contract).contains("$ref: '#/components/schemas/SubmitTaskDraftsResult'");
    }

    @Test
    void openapi_exposes_readable_schema_metadata_on_reviewer_queue_summary() throws Exception {
        String contract = Files.readString(openapiContractPath());

        assertThat(contract).contains("ReviewerSubmissionSummary:");
        assertThat(contract).contains("schemaName:");
        assertThat(contract).contains("schemaVersionNumber:");
    }

    private Path openapiContractPath() {
        Path userDir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        Path fromModule = userDir.resolve("../../packages/contracts/openapi/labelhub.yaml").normalize();
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        return userDir.resolve("packages/contracts/openapi/labelhub.yaml").normalize();
    }
}
