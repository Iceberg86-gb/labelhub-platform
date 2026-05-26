package com.labelhub.api.module.task.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.labelhub.api.generated.model.CreateTaskRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Method;
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
}
