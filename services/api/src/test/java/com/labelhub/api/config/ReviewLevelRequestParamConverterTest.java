package com.labelhub.api.config;

import com.labelhub.api.generated.model.ReviewLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReviewLevelRequestParamConverterTest {

    private final ReviewLevelRequestParamConverter converter = new ReviewLevelRequestParamConverter();

    @Test
    void converts_openapi_lowercase_query_value_to_generated_enum() {
        assertThat(converter.convert("reviewer")).isEqualTo(ReviewLevel.REVIEWER);
        assertThat(converter.convert("senior_reviewer")).isEqualTo(ReviewLevel.SENIOR_REVIEWER);
    }

    @Test
    void rejects_non_contract_review_level_values() {
        assertThatThrownBy(() -> converter.convert("REVIEWER"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unexpected value");
    }
}
