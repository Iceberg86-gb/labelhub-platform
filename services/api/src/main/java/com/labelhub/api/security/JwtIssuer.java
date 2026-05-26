package com.labelhub.api.security;

import com.labelhub.api.config.SecurityProperties;
import com.labelhub.api.module.user.entity.UserEntity;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtIssuer {

    private final SecurityProperties properties;
    private final Clock clock;
    private final SecretKey key;

    public JwtIssuer(SecurityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = keyFrom(properties.getJwtSecret());
    }

    public IssuedToken issue(UserEntity user, List<String> roles) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.getJwtTtlHours(), ChronoUnit.HOURS);
        String token = Jwts.builder()
            .subject(user.getUsername())
            .claim("userId", user.getId())
            .claim("username", user.getUsername())
            .claim("roles", roles)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(key, Jwts.SIG.HS256)
            .compact();
        return new IssuedToken(token, expiresAt);
    }

    static SecretKey keyFrom(String secret) {
        byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes for HS256");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public record IssuedToken(String accessToken, Instant expiresAt) {
    }
}
