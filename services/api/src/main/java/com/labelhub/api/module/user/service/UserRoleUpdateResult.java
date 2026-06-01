package com.labelhub.api.module.user.service;

import com.labelhub.api.module.user.entity.UserEntity;
import java.util.List;

public record UserRoleUpdateResult(UserEntity user, List<String> roles) {
}
