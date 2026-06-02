package com.labelhub.api.config;

import com.labelhub.api.generated.model.ReviewLevel;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ReviewLevelRequestParamConverter implements Converter<String, ReviewLevel> {
    @Override
    public ReviewLevel convert(String source) {
        return ReviewLevel.fromValue(source);
    }
}
