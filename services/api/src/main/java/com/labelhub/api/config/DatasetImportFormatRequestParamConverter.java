package com.labelhub.api.config;

import com.labelhub.api.generated.model.DatasetImportFormat;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class DatasetImportFormatRequestParamConverter implements Converter<String, DatasetImportFormat> {
    @Override
    public DatasetImportFormat convert(String source) {
        return DatasetImportFormat.fromValue(source);
    }
}
