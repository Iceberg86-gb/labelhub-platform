package com.labelhub.api.module.task.service;

import com.labelhub.api.generated.model.TaskStatus;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

public final class TaskStateTransitions {

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED = new EnumMap<>(TaskStatus.class);

    static {
        ALLOWED.put(TaskStatus.DRAFT, EnumSet.of(TaskStatus.PUBLISHED));
        ALLOWED.put(TaskStatus.PUBLISHED, EnumSet.of(TaskStatus.PAUSED, TaskStatus.ENDED));
        ALLOWED.put(TaskStatus.PAUSED, EnumSet.of(TaskStatus.PUBLISHED, TaskStatus.ENDED));
        ALLOWED.put(TaskStatus.ENDED, EnumSet.noneOf(TaskStatus.class));
    }

    private TaskStateTransitions() {
    }

    public static boolean isAllowed(TaskStatus from, TaskStatus to) {
        if (from == null || to == null) {
            return false;
        }
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
