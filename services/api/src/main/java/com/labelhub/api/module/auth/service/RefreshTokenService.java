package com.labelhub.api.module.auth.service;

import com.labelhub.api.config.SecurityProperties;
import com.labelhub.api.module.auth.entity.RefreshTokenEntity;
import com.labelhub.api.module.auth.mapper.RefreshTokenMapper;
import com.labelhub.api.module.user.entity.UserEntity;
import com.labelhub.api.module.user.mapper.UserMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    public static final String COOKIE_NAME = "labelhub_refresh";
    public static final String COOKIE_PATH = "/api/auth";

    private static final int REFRESH_RANDOM_BYTES = 32;

    private final RefreshTokenMapper refreshTokenMapper;
    private final UserMapper userMapper;
    private final SecurityProperties securityProperties;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public RefreshTokenService(
        RefreshTokenMapper refreshTokenMapper,
        UserMapper userMapper,
        SecurityProperties securityProperties,
        Clock clock
    ) {
        this.refreshTokenMapper = refreshTokenMapper;
        this.userMapper = userMapper;
        this.securityProperties = securityProperties;
        this.clock = clock;
    }

    @Transactional
    public IssuedRefreshToken issueForUser(Long userId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(securityProperties.getRefreshTtlDays(), ChronoUnit.DAYS);
        String rawToken = generateRawToken();

        RefreshTokenEntity token = new RefreshTokenEntity();
        token.setUserId(userId);
        token.setTokenHash(hash(rawToken));
        token.setIssuedAt(toLocalDateTime(now));
        token.setExpiresAt(toLocalDateTime(expiresAt));
        token.setRevokedAt(null);
        refreshTokenMapper.insertRefreshToken(token);

        return new IssuedRefreshToken(rawToken, expiresAt, refreshMaxAgeSeconds());
    }

    @Transactional
    public RefreshSession rotate(String rawToken) {
        RefreshTokenEntity existing = requireValid(rawToken);
        UserEntity user = userMapper.selectUserById(existing.getUserId());
        if (user == null || !"active".equals(user.getStatus())) {
            refreshTokenMapper.revokeById(existing.getId(), nowLocalDateTime());
            throw new BadCredentialsException("Invalid refresh token");
        }

        List<String> roles = userMapper.selectRoleCodesByUserId(user.getId());
        refreshTokenMapper.revokeById(existing.getId(), nowLocalDateTime());
        IssuedRefreshToken replacement = issueForUser(user.getId());
        return new RefreshSession(user, roles, replacement);
    }

    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenMapper.revokeByTokenHash(hash(rawToken), nowLocalDateTime());
    }

    @Transactional
    public void revokeActiveForUser(Long userId) {
        refreshTokenMapper.revokeActiveByUserId(userId, nowLocalDateTime());
    }

    private RefreshTokenEntity requireValid(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        RefreshTokenEntity token = refreshTokenMapper.selectByTokenHash(hash(rawToken));
        if (token == null || token.getRevokedAt() != null || !token.getExpiresAt().isAfter(nowLocalDateTime())) {
            throw new BadCredentialsException("Invalid refresh token");
        }
        return token;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[REFRESH_RANDOM_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private long refreshMaxAgeSeconds() {
        return securityProperties.getRefreshTtlDays() * 24L * 60L * 60L;
    }

    private LocalDateTime nowLocalDateTime() {
        return toLocalDateTime(clock.instant());
    }

    private static LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record IssuedRefreshToken(String token, Instant expiresAt, long maxAgeSeconds) {
    }

    public record RefreshSession(UserEntity user, List<String> roles, IssuedRefreshToken refreshToken) {
    }
}
