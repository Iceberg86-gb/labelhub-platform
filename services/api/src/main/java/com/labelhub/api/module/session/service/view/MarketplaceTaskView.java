package com.labelhub.api.module.session.service.view;

import com.labelhub.api.module.task.entity.TaskEntity;

public record MarketplaceTaskView(TaskEntity task, Integer availableItemCount) {
}
