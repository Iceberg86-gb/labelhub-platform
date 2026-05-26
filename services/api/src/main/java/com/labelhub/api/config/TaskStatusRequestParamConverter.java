package com.labelhub.api.config;

import com.labelhub.api.generated.model.TaskStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class TaskStatusRequestParamConverter implements Converter<String, TaskStatus> {
    @Override
    public TaskStatus convert(String source) {
        return TaskStatus.fromValue(source);
    }
}
