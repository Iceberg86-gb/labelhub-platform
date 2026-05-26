package com.labelhub.api.security;

import java.util.List;

public record JwtPrincipal(Long userId, String username, List<String> roles) {
}
