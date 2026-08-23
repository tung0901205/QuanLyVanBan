package com.qlda.aiservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.qlda.aiservice.exception.AppException;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserServiceTest {
    private final CurrentUserService service = new CurrentUserService();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void resolvesUidFromAuthenticatedJwt() {
        authenticateJwt(42L, List.of("CHUYEN_VIEN"));
        assertThat(service.resolveUserId(42L)).isEqualTo(42L);
        assertThat(service.requireUserId()).isEqualTo(42L);
    }

    @Test
    void rejectsClientSuppliedIdentityThatDoesNotMatchJwt() {
        authenticateJwt(42L, List.of("CHUYEN_VIEN"));
        assertThatThrownBy(() -> service.resolveUserId(99L))
            .isInstanceOfSatisfying(AppException.class,
                exception -> assertThat(exception.getStatus().value()).isEqualTo(403));
    }

    @Test
    void recognizesAdminRoleFromTokenClaims() {
        authenticateJwt(1L, List.of("ADMIN"));
        assertThat(service.isAdmin()).isTrue();
    }

    @Test
    void internalServiceMayUseExplicitBusinessUserId() {
        var auth = new UsernamePasswordAuthenticationToken(
            "document-service", null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL_SERVICE"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        assertThat(service.resolveUserId(77L)).isEqualTo(77L);
        assertThat(service.isInternalRequest()).isTrue();
    }

    private void authenticateJwt(Long userId, List<String> roles) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("test-token")
            .header("alg", "none")
            .subject("tester")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .claim("uid", userId)
            .claim("roles", roles)
            .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }
}
