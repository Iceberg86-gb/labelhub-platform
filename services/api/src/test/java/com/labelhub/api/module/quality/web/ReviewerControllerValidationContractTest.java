package com.labelhub.api.module.quality.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ReviewerControllerValidationContractTest {

    @Test
    void list_reviewer_queue_keeps_generated_query_parameter_constraints() throws Exception {
        Method method = ReviewerController.class.getMethod(
            "listReviewerQueue",
            Integer.class,
            Integer.class,
            String.class,
            String.class
        );

        assertThat(parameterAnnotation(method, 0, Min.class).value()).isEqualTo(1);
        assertThat(parameterAnnotation(method, 1, Min.class).value()).isEqualTo(1);
        assertThat(parameterAnnotation(method, 1, Max.class).value()).isEqualTo(100);
    }

    @Test
    void list_submission_ledger_keeps_generated_query_parameter_constraints() throws Exception {
        Method method = ReviewerController.class.getMethod(
            "listSubmissionLedger",
            Long.class,
            Integer.class,
            Integer.class
        );

        assertThat(parameterAnnotation(method, 1, Min.class).value()).isEqualTo(1);
        assertThat(parameterAnnotation(method, 2, Min.class).value()).isEqualTo(1);
        assertThat(parameterAnnotation(method, 2, Max.class).value()).isEqualTo(200);
    }

    private static <T extends Annotation> T parameterAnnotation(Method method, int parameterIndex, Class<T> annotationType) {
        T annotation = method.getParameters()[parameterIndex].getAnnotation(annotationType);
        assertThat(annotation)
            .as("%s parameter %d must keep generated validation parity", method.getName(), parameterIndex)
            .isNotNull();
        return annotation;
    }
}
