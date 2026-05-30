package com.labelhub.api.config;

import com.labelhub.api.generated.model.SessionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionStatusRequestParamConverterTest {

    private final SessionStatusRequestParamConverter converter = new SessionStatusRequestParamConverter();

    @Test
    void converts_openapi_lowercase_query_value_to_generated_enum() {
        assertThat(converter.convert("claimed")).isEqualTo(SessionStatus.CLAIMED);
        assertThat(converter.convert("submitted")).isEqualTo(SessionStatus.SUBMITTED);
        assertThat(converter.convert("abandoned")).isEqualTo(SessionStatus.ABANDONED);
    }

    @Test
    void rejects_non_contract_status_values() {
        assertThatThrownBy(() -> converter.convert("CLAIMED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unexpected value");
    }
}
