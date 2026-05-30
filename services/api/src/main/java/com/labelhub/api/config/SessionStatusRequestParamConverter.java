package com.labelhub.api.config;

import com.labelhub.api.generated.model.SessionStatus;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class SessionStatusRequestParamConverter implements Converter<String, SessionStatus> {
    @Override
    public SessionStatus convert(String source) {
        return SessionStatus.fromValue(source);
    }
}
