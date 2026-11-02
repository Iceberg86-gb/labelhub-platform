package com.labelhub.api.config;

import com.labelhub.api.generated.model.DatasetImportFormat;
import com.labelhub.api.generated.model.ExportSnapshotPackageType;
import com.labelhub.api.generated.model.LabelerSessionWorkStatus;
import com.labelhub.api.generated.model.ReviewLevel;
import com.labelhub.api.generated.model.SchemaListScope;
import com.labelhub.api.generated.model.SessionStatus;
import com.labelhub.api.generated.model.TaskStatus;
import java.beans.PropertyEditorSupport;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.InitBinder;

/**
 * Binds contract enums used as query parameters / path variables by their OpenAPI wire value only.
 *
 * <p>Generated enums are named {@code IN_PROGRESS} but travel as {@code in_progress}. Spring's default
 * String-to-enum binding uses {@code Enum.valueOf}, so without this the contract value was rejected
 * with 400 (e.g. {@code /my/sessions?workStatus=in_progress}), while the Java constant name was
 * accepted. A {@code Converter} bean alone does not fix the second half: when it throws, Spring's
 * {@code TypeConverterDelegate} falls back to {@code Enum.valueOf}. A registered property editor is
 * consulted first and its failure is final, surfacing as {@code MethodArgumentTypeMismatchException}.
 */
@ControllerAdvice
public class ContractEnumBinding {

    /** Every contract enum bound from a request parameter or path variable (guarded by ContractEnumBindingTest). */
    static final List<Class<? extends Enum<?>>> CONTRACT_ENUMS = List.of(
        TaskStatus.class,
        SessionStatus.class,
        LabelerSessionWorkStatus.class,
        ReviewLevel.class,
        SchemaListScope.class,
        DatasetImportFormat.class,
        ExportSnapshotPackageType.class
    );

    @InitBinder
    void registerContractEnumEditors(WebDataBinder binder) {
        for (Class<? extends Enum<?>> type : CONTRACT_ENUMS) {
            binder.registerCustomEditor(type, new WireValueEditor(type));
        }
    }

    static final class WireValueEditor extends PropertyEditorSupport {
        private final Method fromValue;

        WireValueEditor(Class<? extends Enum<?>> type) {
            try {
                this.fromValue = type.getMethod("fromValue", String.class);
            } catch (NoSuchMethodException exception) {
                throw new IllegalStateException(type.getName() + " has no static fromValue(String)", exception);
            }
        }

        @Override
        public void setAsText(String text) {
            if (text == null || text.isEmpty()) {
                setValue(null);
                return;
            }
            try {
                setValue(fromValue.invoke(null, text));
            } catch (InvocationTargetException exception) {
                Throwable cause = exception.getCause();
                throw cause instanceof IllegalArgumentException illegalArgument
                    ? illegalArgument
                    : new IllegalArgumentException("Unexpected value '" + text + "'", cause);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}
