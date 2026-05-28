package com.labelhub.api.integration;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class PromptVersionsApiIntegrationTest {

    private static final String JWT_SECRET = "dev-only-32-bytes-minimum-secret-please-change-me";
    private static final String INTERNAL_TOKEN = "dev-internal-token";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("labelhub")
        .withUsername("labelhub")
        .withPassword("labelhub");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("labelhub.security.jwt-secret", () -> JWT_SECRET);
        registry.add("labelhub.security.internal-token", () -> INTERNAL_TOKEN);
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void default_prompt_version_requires_authentication() throws Exception {
        mockMvc.perform(get("/prompt-versions/default"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticated_user_can_read_seeded_default_prompt_version() throws Exception {
        mockMvc.perform(get("/prompt-versions/default")
                .header("Authorization", bearer(tokenForUser(1001L, "owner_demo", "OWNER"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.versionNo").value(1))
            .andExpect(jsonPath("$.content").value("m3-owner-review-v1"))
            .andExpect(jsonPath("$.contentHash").value("fa76977fd0bdc3f0cc7336855006669f2950381f1a0dc4f0803458bb6f06d456"))
            .andExpect(jsonPath("$.status").value("published"));
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String tokenForUser(Long userId, String username, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
            .subject(username)
            .claim("uid", userId)
            .claim("roles", java.util.List.of(role))
            .issuedAt(Date.from(Instant.parse("2026-05-25T12:00:00Z")))
            .expiration(Date.from(Instant.parse("2030-01-01T00:00:00Z")))
            .signWith(key)
            .compact();
    }
}
