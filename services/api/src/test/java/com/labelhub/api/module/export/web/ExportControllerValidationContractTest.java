package com.labelhub.api.module.export.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class ExportControllerValidationContractTest {

    @Test
    void list_task_exports_keeps_generated_query_parameter_constraints() throws Exception {
        Method method = ExportController.class.getMethod(
            "listTaskExports",
            Long.class,
            Integer.class,
            Integer.class,
            Boolean.class
        );

        assertThat(parameterAnnotation(method, 1, Min.class).value()).isEqualTo(1);
        assertThat(parameterAnnotation(method, 2, Min.class).value()).isEqualTo(1);
        assertThat(parameterAnnotation(method, 2, Max.class).value()).isEqualTo(100);
    }

    @Test
    void diff_export_snapshots_keeps_generated_compare_with_constraint() throws Exception {
        Method method = ExportController.class.getMethod(
            "diffExportSnapshots",
            Long.class,
            Long.class
        );

        assertThat(parameterAnnotation(method, 1, NotNull.class)).isNotNull();
    }

    private static <T extends Annotation> T parameterAnnotation(Method method, int parameterIndex, Class<T> annotationType) {
        T annotation = method.getParameters()[parameterIndex].getAnnotation(annotationType);
        assertThat(annotation)
            .as("%s parameter %d must keep generated validation parity", method.getName(), parameterIndex)
            .isNotNull();
        return annotation;
    }
}
