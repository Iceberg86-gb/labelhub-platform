package com.labelhub.api.security;

import com.labelhub.api.config.SecurityProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtValidatorTest {

    private static final String SECRET = "dev-only-32-bytes-minimum-secret-please-change-me";

    private final JwtValidator validator = new JwtValidator(properties());

    @Test
    void valid_token_yields_principal() {
        JwtPrincipal principal = validator.validate(token("userId", 1001L));

        assertThat(principal.userId()).isEqualTo(1001L);
        assertThat(principal.username()).isEqualTo("owner_demo");
        assertThat(principal.roles()).containsExactly("OWNER");
    }

    // Security contract: a correctly signed token without a numeric userId is rejected as a JwtException,
    // which JwtAuthenticationFilter turns into 401 (it used to NPE and surface as a 500).
    @Test
    void signed_token_without_numeric_user_id_is_rejected_as_invalid_jwt() {
        assertThatThrownBy(() -> validator.validate(token("uid", 1001L))).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> validator.validate(token("userId", "1001"))).isInstanceOf(JwtException.class);
    }

    private static String token(String userIdClaim, Object userId) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject("owner_demo")
            .claim(userIdClaim, userId)
            .claim("username", "owner_demo")
            .claim("roles", List.of("OWNER"))
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusSeconds(600)))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
            .compact();
    }

    private static SecurityProperties properties() {
        SecurityProperties properties = new SecurityProperties();
        properties.setJwtSecret(SECRET);
        return properties;
    }
}
