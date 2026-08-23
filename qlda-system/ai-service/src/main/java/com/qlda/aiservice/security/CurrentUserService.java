package com.qlda.aiservice.security;

import com.qlda.aiservice.exception.AppException;
import com.qlda.aiservice.exception.ErrorCode;
import java.util.Collection;
import java.util.Locale;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserService {
    public Long resolveUserId(Long requestedUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (isInternal(authentication)) {
            if (requestedUserId == null) {
                throw forbidden("Internal request must provide a userId");
            }
            return requestedUserId;
        }
        Long authenticatedUserId = extractJwtUserId(authentication);
        if (requestedUserId != null && !Objects.equals(requestedUserId, authenticatedUserId)) {
            throw forbidden("userId does not match the authenticated account");
        }
        return authenticatedUserId;
    }

    public Long requireUserId() {
        return resolveUserId(null);
    }

    public boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken token) {
            Object roles = token.getToken().getClaims().get("roles");
            if (roles instanceof Collection<?> collection) {
                return collection.stream().map(String::valueOf).anyMatch(this::isAdminRole);
            }
            return roles != null && isAdminRole(String.valueOf(roles));
        }
        return false;
    }

    public boolean isInternalRequest() {
        return isInternal(SecurityContextHolder.getContext().getAuthentication());
    }

    private Long extractJwtUserId(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken token)) {
            throw forbidden("Authenticated JWT is required");
        }
        Object value = token.getToken().getClaims().get("uid");
        if (value == null) value = token.getToken().getClaims().get("userId");
        if (value == null) throw forbidden("JWT does not contain a user identifier");
        try {
            return Long.valueOf(value.toString());
        } catch (NumberFormatException ex) {
            throw forbidden("JWT user identifier is invalid");
        }
    }

    private boolean isInternal(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_INTERNAL_SERVICE".equals(authority.getAuthority()));
    }

    private boolean isAdminRole(String role) {
        String normalized = role.toUpperCase(Locale.ROOT);
        return normalized.equals("ADMIN") || normalized.equals("ROLE_ADMIN") || normalized.contains("QUAN_TRI");
    }

    private AppException forbidden(String message) {
        return new AppException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN, message);
    }
}
