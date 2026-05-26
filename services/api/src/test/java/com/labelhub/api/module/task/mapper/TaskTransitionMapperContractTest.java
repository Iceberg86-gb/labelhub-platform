package com.labelhub.api.module.task.mapper;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskTransitionMapperContractTest {

    @Test
    void task_transition_mapper_exposes_only_append_only_methods() {
        assertThat(TaskTransitionMapper.class.getInterfaces()).isEmpty();

        for (Method method : TaskTransitionMapper.class.getDeclaredMethods()) {
            assertThat(method.getName()).doesNotStartWith("update");
            assertThat(method.getName()).doesNotStartWith("delete");
            assertThat(method.getName()).isIn("insert", "selectByTaskId");
        }
    }
}
