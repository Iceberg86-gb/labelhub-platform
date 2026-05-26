package com.labelhub.api.security;

import com.labelhub.api.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtValidator {

    private final SecretKey key;

    public JwtValidator(SecurityProperties properties) {
        this.key = JwtIssuer.keyFrom(properties.getJwtSecret());
    }

    @SuppressWarnings("unchecked")
    public JwtPrincipal validate(String token) {
        Claims claims = Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
        Object userId = claims.get("userId");
        List<String> roles = claims.get("roles", List.class);
        return new JwtPrincipal(((Number) userId).longValue(), claims.get("username", String.class), roles == null ? List.of() : roles);
    }
}
