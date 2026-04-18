package com.archivage.auth;

import com.archivage.auth.security.UserPrincipal;
import com.archivage.common.domain.Role;
import com.archivage.config.AppJwtProperties;
import com.archivage.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppJwtProperties props = new AppJwtProperties(
                "test-secret-key-must-be-long-enough-for-hs256-algorithm-minimum-256-bits!!",
                15,
                7
        );
        jwtService = new JwtService(props);
    }

    @Test
    void generatesAndValidatesToken() {
        User user = User.builder()
                .username("alice")
                .passwordHash("x")
                .role(Role.LECTEUR)
                .active(true)
                .build();
        user.setId(42L);
        user.setUuid(java.util.UUID.randomUUID());
        UserDetails principal = new UserPrincipal(user);

        String token = jwtService.generateAccessToken(principal, 42L, Role.LECTEUR.name());

        assertThat(jwtService.extractUsername(token)).isEqualTo("alice");
        assertThat(jwtService.isTokenValid(token, principal)).isTrue();
    }

    @Test
    void rejectsMalformedToken() {
        assertThatThrownBy(() -> jwtService.parseClaims("not-a-jwt"))
                .isInstanceOf(JwtValidationException.class);
    }
}
