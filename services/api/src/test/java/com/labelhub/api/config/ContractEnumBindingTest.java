package com.labelhub.api.config;

import com.labelhub.api.generated.model.LabelerSessionWorkStatus;
import com.labelhub.api.generated.model.ReviewLevel;
import com.labelhub.api.generated.model.SchemaListScope;
import com.labelhub.api.generated.model.SessionStatus;
import com.labelhub.api.generated.model.TaskStatus;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractEnumBindingTest {

    // Contract: query/path enums bind from their OpenAPI wire value (snake_case lowercase).
    @Test
    void binds_contract_wire_values() {
        assertThat(bind(TaskStatus.class, "published")).isEqualTo(TaskStatus.PUBLISHED);
        assertThat(bind(SessionStatus.class, "claimed")).isEqualTo(SessionStatus.CLAIMED);
        assertThat(bind(LabelerSessionWorkStatus.class, "in_progress")).isEqualTo(LabelerSessionWorkStatus.IN_PROGRESS);
        assertThat(bind(LabelerSessionWorkStatus.class, "returned_for_revision"))
            .isEqualTo(LabelerSessionWorkStatus.RETURNED_FOR_REVISION);
        assertThat(bind(SchemaListScope.class, "library")).isEqualTo(SchemaListScope.LIBRARY);
        assertThat(bind(ReviewLevel.class, "senior_reviewer")).isEqualTo(ReviewLevel.SENIOR_REVIEWER);
    }

    // Contract: Java constant names and unknown values are not part of the API and must be rejected
    // (TypeMismatchException -> MethodArgumentTypeMismatchException -> 400 in GlobalExceptionHandler).
    @Test
    void rejects_java_constant_names_and_unknown_values() {
        assertThatThrownBy(() -> bind(SessionStatus.class, "CLAIMED")).isInstanceOf(TypeMismatchException.class);
        assertThatThrownBy(() -> bind(TaskStatus.class, "PUBLISHED")).isInstanceOf(TypeMismatchException.class);
        assertThatThrownBy(() -> bind(LabelerSessionWorkStatus.class, "IN_PROGRESS")).isInstanceOf(TypeMismatchException.class);
        assertThatThrownBy(() -> bind(SchemaListScope.class, "bogus")).isInstanceOf(TypeMismatchException.class);
    }

    @Test
    void empty_value_binds_to_null() {
        assertThat(bind(TaskStatus.class, "")).isNull();
    }

    // Invariant: every enum a controller binds from a request parameter or path variable is registered,
    // otherwise it silently falls back to Enum.valueOf and rejects its own contract values.
    @Test
    void every_controller_enum_parameter_is_registered() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        List<String> unregistered = new ArrayList<>();
        int enumParameters = 0;
        for (BeanDefinition candidate : scanner.findCandidateComponents("com.labelhub.api.module")) {
            for (Method method : Class.forName(candidate.getBeanClassName()).getDeclaredMethods()) {
                for (Parameter parameter : method.getParameters()) {
                    boolean bound = parameter.isAnnotationPresent(RequestParam.class)
                        || parameter.isAnnotationPresent(PathVariable.class);
                    if (bound && parameter.getType().isEnum()) {
                        enumParameters++;
                        if (!ContractEnumBinding.CONTRACT_ENUMS.contains(parameter.getType())) {
                            unregistered.add(method.getDeclaringClass().getSimpleName() + "#" + method.getName()
                                + "(" + parameter.getType().getSimpleName() + ")");
                        }
                    }
                }
            }
        }
        assertThat(enumParameters).as("scan found controller enum parameters").isPositive();
        assertThat(unregistered).as("enum parameters missing from ContractEnumBinding.CONTRACT_ENUMS").isEmpty();
    }

    private static Object bind(Class<?> type, String text) {
        WebDataBinder binder = new WebDataBinder(null);
        new ContractEnumBinding().registerContractEnumEditors(binder);
        return binder.convertIfNecessary(text, type);
    }
}
