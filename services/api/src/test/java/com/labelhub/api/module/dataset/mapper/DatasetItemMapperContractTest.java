package com.labelhub.api.module.dataset.mapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DatasetItemMapperContractTest {

    @Test
    void bulkPayloadUpdate_is_guarded_by_available_status() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/labelhub/api/module/dataset/mapper/DatasetItemMapper.java"));

        assertThat(source).contains("updatePayloadIfAvailable");
        assertThat(source).contains("status = 'available'");
    }
}
