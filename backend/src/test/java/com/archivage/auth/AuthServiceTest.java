package com.archivage.auth;

import com.archivage.auth.dto.AuthResponse;
import com.archivage.auth.dto.LoginRequest;
import com.archivage.auth.dto.RefreshRequest;
import com.archivage.auth.dto.UserSummaryDto;
import com.archivage.auth.entity.RefreshToken;
import com.archivage.auth.mapper.UserMapper;
import com.archivage.auth.repository.RefreshTokenRepository;
import com.archivage.auth.security.UserPrincipal;
import com.archivage.audit.AuditService;
import com.archivage.common.domain.Role;
import com.archivage.common.exception.ApiException;
import com.archivage.config.AppJwtProperties;
import com.archivage.user.entity.User;
import com.archivage.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private AuditService auditService;

    private AppJwtProperties jwtProperties = new AppJwtProperties(
            "test-secret-key-must-be-long-enough-for-hs256-algorithm-minimum-256-bits!!",
            15,
            7
    );

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                authenticationManager,
                userRepository,
                jwtService,
                userMapper,
                refreshTokenRepository,
                jwtProperties,
                auditService
        );
    }

    @Test
    void login_success_returnsTokensAndAudits() {
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class))).thenReturn(auth);

        User user = User.builder()
                .username("alice")
                .passwordHash("x")
                .role(Role.LECTEUR)
                .active(true)
                .build();
        user.setId(5L);
        user.setUuid(UUID.randomUUID());

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(any(UserPrincipal.class), eq(5L), eq("LECTEUR"))).thenReturn("access-jwt");
        when(userMapper.toSummary(user)).thenReturn(new UserSummaryDto(
                5L, user.getUuid(), "alice", null, null, Role.LECTEUR
        ));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.login(new LoginRequest("alice", "secret"));

        assertThat(response.accessToken()).isEqualTo("access-jwt");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(response.expiresIn()).isEqualTo(15 * 60L);
        verify(auditService).logLoginSuccess(user);
    }

    @Test
    void login_badCredentials_auditsFailureAndRethrows() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> authService.login(new LoginRequest("x", "y")))
                .isInstanceOf(BadCredentialsException.class);

        verify(auditService).logLoginFailure("x");
    }

    @Test
    void refresh_validToken_revokesOldAndReturnsNewPair() {
        User user = User.builder()
                .username("bob")
                .passwordHash("x")
                .role(Role.ADMIN)
                .active(true)
                .build();
        user.setId(2L);
        user.setUuid(UUID.randomUUID());

        RefreshToken existing = RefreshToken.builder()
                .tokenId("oldtoken")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenId("oldtoken")).thenReturn(Optional.of(existing));
        when(jwtService.generateAccessToken(any(UserPrincipal.class), eq(2L), eq("ADMIN"))).thenReturn("new-access");
        when(userMapper.toSummary(user)).thenReturn(new UserSummaryDto(
                2L, user.getUuid(), "bob", null, null, Role.ADMIN
        ));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse response = authService.refresh(new RefreshRequest("oldtoken"));

        assertThat(response.accessToken()).isEqualTo("new-access");
        assertThat(existing.getRevoked()).isTrue();
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
    }

    @Test
    void refresh_unknownToken_throwsUnauthorized() {
        when(refreshTokenRepository.findByTokenId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("missing")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    var e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getCode()).isEqualTo("INVALID_REFRESH");
                });
    }

    @Test
    void refresh_revokedToken_throwsUnauthorized() {
        User user = User.builder()
                .username("u2")
                .passwordHash("x")
                .role(Role.LECTEUR)
                .active(true)
                .build();
        user.setId(2L);

        RefreshToken revoked = RefreshToken.builder()
                .tokenId("revoked")
                .user(user)
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .revoked(true)
                .build();
        when(refreshTokenRepository.findByTokenId("revoked")).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("revoked")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getCode()).isEqualTo("INVALID_REFRESH");
                });
    }

    @Test
    void refresh_expiredToken_throwsUnauthorized() {
        User user = User.builder()
                .username("u")
                .passwordHash("x")
                .role(Role.LECTEUR)
                .active(true)
                .build();
        user.setId(1L);

        RefreshToken expired = RefreshToken.builder()
                .tokenId("exp")
                .user(user)
                .expiresAt(Instant.now().minusSeconds(60))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenId("exp")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("exp")))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    var e = (ApiException) ex;
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getCode()).isEqualTo("INVALID_REFRESH");
                });
    }

    @Test
    void logout_revokesToken_andAuditsWhenUserPrincipal() {
        User user = User.builder()
                .username("u")
                .passwordHash("x")
                .role(Role.LECTEUR)
                .active(true)
                .build();
        user.setId(7L);
        RefreshToken rt = RefreshToken.builder()
                .tokenId("rtok")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(3600))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenId("rtok")).thenReturn(Optional.of(rt));
        when(refreshTokenRepository.save(rt)).thenReturn(rt);

        UserPrincipal principal = new UserPrincipal(user);
        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        SecurityContext ctx = org.mockito.Mockito.mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);

        try (MockedStatic<SecurityContextHolder> holder = Mockito.mockStatic(SecurityContextHolder.class)) {
            holder.when(SecurityContextHolder::getContext).thenReturn(ctx);
            authService.logout("rtok");
        }

        assertThat(rt.getRevoked()).isTrue();
        verify(auditService).logLogout(user);
    }

    @Test
    void logout_noAuthentication_skipsLogoutAudit() {
        RefreshToken rt = RefreshToken.builder()
                .tokenId("x")
                .user(User.builder().username("u").passwordHash("h").role(Role.LECTEUR).active(true).build())
                .expiresAt(Instant.now().plusSeconds(10))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenId("x")).thenReturn(Optional.of(rt));
        when(refreshTokenRepository.save(rt)).thenReturn(rt);

        SecurityContext ctx = org.mockito.Mockito.mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(null);

        try (MockedStatic<SecurityContextHolder> holder = Mockito.mockStatic(SecurityContextHolder.class)) {
            holder.when(SecurityContextHolder::getContext).thenReturn(ctx);
            authService.logout("x");
        }

        verify(auditService, never()).logLogout(any());
    }

    @Test
    void logout_unknownRefreshToken_doesNotSave() {
        when(refreshTokenRepository.findByTokenId("missing")).thenReturn(Optional.empty());
        SecurityContext ctx = org.mockito.Mockito.mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(null);
        try (MockedStatic<SecurityContextHolder> holder = Mockito.mockStatic(SecurityContextHolder.class)) {
            holder.when(SecurityContextHolder::getContext).thenReturn(ctx);
            authService.logout("missing");
        }
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void logout_nonUserPrincipal_skipsLogoutAudit() {
        User user = User.builder()
                .username("u")
                .passwordHash("x")
                .role(Role.LECTEUR)
                .active(true)
                .build();
        RefreshToken rt = RefreshToken.builder()
                .tokenId("tid")
                .user(user)
                .expiresAt(Instant.now().plusSeconds(60))
                .revoked(false)
                .build();
        when(refreshTokenRepository.findByTokenId("tid")).thenReturn(Optional.of(rt));
        when(refreshTokenRepository.save(rt)).thenReturn(rt);

        Authentication auth = org.mockito.Mockito.mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn("anonymous");
        SecurityContext ctx = org.mockito.Mockito.mock(SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);

        try (MockedStatic<SecurityContextHolder> holder = Mockito.mockStatic(SecurityContextHolder.class)) {
            holder.when(SecurityContextHolder::getContext).thenReturn(ctx);
            authService.logout("tid");
        }

        verify(auditService, never()).logLogout(any());
    }
}
