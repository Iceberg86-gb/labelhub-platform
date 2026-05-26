package com.labelhub.api.config;

import com.labelhub.api.generated.model.TaskStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskStatusRequestParamConverterTest {

    private final TaskStatusRequestParamConverter converter = new TaskStatusRequestParamConverter();

    @Test
    void converts_openapi_lowercase_query_value_to_generated_enum() {
        assertThat(converter.convert("published")).isEqualTo(TaskStatus.PUBLISHED);
        assertThat(converter.convert("draft")).isEqualTo(TaskStatus.DRAFT);
        assertThat(converter.convert("paused")).isEqualTo(TaskStatus.PAUSED);
        assertThat(converter.convert("ended")).isEqualTo(TaskStatus.ENDED);
    }

    @Test
    void rejects_non_contract_status_values() {
        assertThatThrownBy(() -> converter.convert("PUBLISHED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unexpected value");
    }
}
