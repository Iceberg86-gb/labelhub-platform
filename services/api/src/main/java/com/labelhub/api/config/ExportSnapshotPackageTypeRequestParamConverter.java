package com.labelhub.api.config;

import com.labelhub.api.generated.model.ExportSnapshotPackageType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class ExportSnapshotPackageTypeRequestParamConverter implements Converter<String, ExportSnapshotPackageType> {
    @Override
    public ExportSnapshotPackageType convert(String source) {
        return ExportSnapshotPackageType.fromValue(source);
    }
}
