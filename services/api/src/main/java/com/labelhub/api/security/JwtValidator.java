package com.labelhub.api.security;

import com.labelhub.api.config.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
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
        if (!(claims.get("userId") instanceof Number userId)) {
            // A signed token without a numeric userId is malformed: reject it as unauthenticated (401), not a 500.
            throw new MalformedJwtException("JWT is missing a numeric userId claim");
        }
        List<String> roles = claims.get("roles", List.class);
        return new JwtPrincipal(userId.longValue(), claims.get("username", String.class), roles == null ? List.of() : roles);
    }
}
