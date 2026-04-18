package com.archivage.auth;

import com.archivage.auth.dto.AuthResponse;
import com.archivage.auth.dto.LoginRequest;
import com.archivage.auth.dto.RefreshRequest;
import com.archivage.auth.entity.RefreshToken;
import com.archivage.auth.mapper.UserMapper;
import com.archivage.auth.repository.RefreshTokenRepository;
import com.archivage.auth.security.UserPrincipal;
import com.archivage.audit.AuditService;
import com.archivage.common.exception.ApiException;
import com.archivage.config.AppJwtProperties;
import com.archivage.user.entity.User;
import com.archivage.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final UserMapper userMapper;
    private final RefreshTokenRepository refreshTokenRepository;
    private final AppJwtProperties jwtProperties;
    private final AuditService auditService;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password())
            );
        } catch (BadCredentialsException e) {
            auditService.logLoginFailure(request.username());
            throw e;
        }
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Identifiants invalides"));
        auditService.logLoginSuccess(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken existing = refreshTokenRepository.findByTokenId(request.refreshToken())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "Refresh token invalide"));
        if (Boolean.TRUE.equals(existing.getRevoked()) || existing.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH", "Refresh token expiré ou révoqué");
        }
        User user = existing.getUser();
        existing.setRevoked(true);
        refreshTokenRepository.save(existing);
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByTokenId(refreshToken).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
        });
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal principal) {
            auditService.logLogout(principal.getUser());
        }
    }

    private AuthResponse buildAuthResponse(User user) {
        UserPrincipal principal = new UserPrincipal(user);
        String accessToken = jwtService.generateAccessToken(principal, user.getId(), user.getRole().name());
        String refreshToken = createRefreshToken(user);
        long expiresIn = jwtProperties.accessTokenMinutes() * 60L;
        return new AuthResponse(accessToken, refreshToken, expiresIn, userMapper.toSummary(user));
    }

    private String createRefreshToken(User user) {
        String tokenId = UUID.randomUUID().toString().replace("-", "");
        RefreshToken entity = RefreshToken.builder()
                .tokenId(tokenId)
                .user(user)
                .expiresAt(Instant.now().plus(jwtProperties.refreshTokenDays(), ChronoUnit.DAYS))
                .revoked(false)
                .build();
        refreshTokenRepository.save(entity);
        return tokenId;
    }
}
