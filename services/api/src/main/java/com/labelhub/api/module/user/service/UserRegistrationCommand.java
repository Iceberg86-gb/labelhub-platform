package com.labelhub.api.module.user.service;

public record UserRegistrationCommand(
    String username,
    String displayName,
    String email,
    String password
) {
}
