package com.labelhub.api.module.dataset.service;

import java.util.Map;

public record DatasetItemUpdateCommand(Long id, Map<String, Object> itemPayload) {
}
