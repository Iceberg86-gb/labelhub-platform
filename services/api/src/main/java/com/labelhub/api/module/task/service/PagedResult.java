package com.labelhub.api.module.task.service;

import java.util.List;

public record PagedResult<T>(List<T> items, long total, long page, long size) {
}
