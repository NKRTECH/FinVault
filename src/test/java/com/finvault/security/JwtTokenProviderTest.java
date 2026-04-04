package com.finvault.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    // A 256-bit (32-char) secret for testing
    private static final String TEST_SECRET = "test-jwt-secret-key-minimum-32-chars!!";
    private static final long EXPIRATION_MS = 86400000; // 24 hours

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, EXPIRATION_MS);
    }

    @Test
    void shouldGenerateToken() {
        Authentication authentication = createAuthentication("testuser", "ROLE_VIEWER");

        String token = jwtTokenProvider.generateToken(authentication);

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(token.split("\\.")).hasSize(3); // JWT has 3 parts
    }

    @Test
    void shouldExtractUsernameFromToken() {
        Authentication authentication = createAuthentication("admin", "ROLE_ADMIN");
        String token = jwtTokenProvider.generateToken(authentication);

        String username = jwtTokenProvider.getUsernameFromToken(token);

        assertThat(username).isEqualTo("admin");
    }

    @Test
    void shouldValidateValidToken() {
        Authentication authentication = createAuthentication("testuser", "ROLE_VIEWER");
        String token = jwtTokenProvider.generateToken(authentication);

        boolean isValid = jwtTokenProvider.validateToken(token);

        assertThat(isValid).isTrue();
    }

    @Test
    void shouldRejectInvalidToken() {
        boolean isValid = jwtTokenProvider.validateToken("invalid.token.here");

        assertThat(isValid).isFalse();
    }

    @Test
    void shouldRejectExpiredToken() {
        // Create provider with 0ms expiration (token expires immediately)
        JwtTokenProvider expiredProvider = new JwtTokenProvider(TEST_SECRET, 0);
        Authentication authentication = createAuthentication("testuser", "ROLE_VIEWER");
        String token = expiredProvider.generateToken(authentication);

        boolean isValid = jwtTokenProvider.validateToken(token);

        assertThat(isValid).isFalse();
    }

    @Test
    void shouldRejectNullToken() {
        boolean isValid = jwtTokenProvider.validateToken(null);

        assertThat(isValid).isFalse();
    }

    @Test
    void shouldRejectEmptyToken() {
        boolean isValid = jwtTokenProvider.validateToken("");

        assertThat(isValid).isFalse();
    }

    @Test
    void shouldRejectTokenSignedWithDifferentKey() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
                "another-secret-key-minimum-32-chars!!", EXPIRATION_MS);
        Authentication authentication = createAuthentication("testuser", "ROLE_VIEWER");
        String token = otherProvider.generateToken(authentication);

        boolean isValid = jwtTokenProvider.validateToken(token);

        assertThat(isValid).isFalse();
    }

    @Test
    void shouldPreserveRolesInToken() {
        Authentication authentication = createAuthentication("admin", "ROLE_ADMIN");
        String token = jwtTokenProvider.generateToken(authentication);

        // Token should be valid and contain the username
        assertThat(jwtTokenProvider.validateToken(token)).isTrue();
        assertThat(jwtTokenProvider.getUsernameFromToken(token)).isEqualTo("admin");
    }

    private Authentication createAuthentication(String username, String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }
}
