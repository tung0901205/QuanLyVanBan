package com.qlda.authservice.service;

import com.qlda.authservice.common.ErrorCode;
import com.qlda.authservice.config.AuthProperties;
import com.qlda.authservice.dto.auth.AuthTokenResponse;
import com.qlda.authservice.dto.auth.AuthUserResponse;
import com.qlda.authservice.dto.auth.AzureLoginRequest;
import com.qlda.authservice.dto.auth.CurrentUserResponse;
import com.qlda.authservice.dto.auth.CurrentUserPermissionResponse;
import com.qlda.authservice.dto.auth.CurrentUserUpdateRequest;
import com.qlda.authservice.dto.auth.DevLoginRequest;
import com.qlda.authservice.dto.auth.LocalLoginRequest;
import com.qlda.authservice.dto.auth.LogoutRequest;
import com.qlda.authservice.dto.auth.RefreshTokenRequest;
import com.qlda.authservice.dto.auth.RefreshTokenResponse;
import com.qlda.authservice.entity.NguoiDung;
import com.qlda.authservice.exception.ApiException;
import com.qlda.authservice.repository.NguoiDungRepository;
import com.qlda.authservice.repository.PhanQuyenRepository;
import com.qlda.authservice.security.CurrentUserPrincipal;
import com.qlda.authservice.security.JwtService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    private final UserService userService;
    private final NguoiDungRepository nguoiDungRepository;
    private final JwtService jwtService;
    private final RefreshTokenStoreService refreshTokenStoreService;
    private final AzureAuthService azureAuthService;
    private final AuthProperties authProperties;
    private final AuditLogService auditLogService;
    private final AuthenticationManager authenticationManager;
    private final PhanQuyenRepository phanQuyenRepository;

    public AuthService(
            UserService userService,
            NguoiDungRepository nguoiDungRepository,
            JwtService jwtService,
            RefreshTokenStoreService refreshTokenStoreService,
            AzureAuthService azureAuthService,
            AuthProperties authProperties,
            AuditLogService auditLogService,
            AuthenticationManager authenticationManager,
            PhanQuyenRepository phanQuyenRepository
    ) {
        this.userService = userService;
        this.nguoiDungRepository = nguoiDungRepository;
        this.jwtService = jwtService;
        this.refreshTokenStoreService = refreshTokenStoreService;
        this.azureAuthService = azureAuthService;
        this.authProperties = authProperties;
        this.auditLogService = auditLogService;
        this.authenticationManager = authenticationManager;
        this.phanQuyenRepository = phanQuyenRepository;
    }

    public AuthTokenResponse loginLocal(LocalLoginRequest request, String ipAddress) {
        String loginValue = request.username().trim();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginValue, request.password())
            );
        } catch (BadCredentialsException exception) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.INVALID_LOGIN,
                    "Tài khoản hoặc mật khẩu không chính xác"
            );
        } catch (AuthenticationException exception) {
            throw new ApiException(
                    HttpStatus.UNAUTHORIZED,
                    ErrorCode.INVALID_LOGIN,
                    "Không thể xác thực tài khoản"
            );
        }

        NguoiDung user = nguoiDungRepository
                .findByUserNameIgnoreCaseOrEmailIgnoreCase(loginValue, loginValue)
                .filter(candidate -> candidate.getTrangThai() != null && candidate.getTrangThai() == 1)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.INVALID_LOGIN,
                        "Tài khoản hoặc mật khẩu không chính xác"
                ));

        user.setLanDangNhapCuoi(LocalDateTime.now());
        user.setNgayCapNhat(LocalDateTime.now());
        nguoiDungRepository.save(user);

        AuthTokenResponse response = issueAuthTokenResponse(user);
        auditLogService.log(
                user.getId(),
                user.getHoTen(),
                "LOCAL_LOGIN",
                "NguoiDung",
                user.getId(),
                "Đăng nhập bằng username/password",
                ipAddress,
                1
        );
        return response;
    }

    public AuthTokenResponse loginDev(DevLoginRequest request, String ipAddress) {
        if (!authProperties.getDevPassword().isEnabled()) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN, "Dev login is disabled");
        }
        String devPassword = authProperties.getDevPassword().getValue();
        if (devPassword == null || !devPassword.equals(request.password())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Invalid credentials");
        }
        NguoiDung user = userService.findActiveUserByUsername(request.username());
        user.setLanDangNhapCuoi(LocalDateTime.now());
        user.setNgayCapNhat(LocalDateTime.now());
        nguoiDungRepository.save(user);
        AuthTokenResponse response = issueAuthTokenResponse(user);
        auditLogService.log(user.getId(), user.getHoTen(), "DEV_LOGIN", "NguoiDung",
                user.getId(), "Dev login", ipAddress, 1);
        return response;
    }

    public AuthTokenResponse loginAzure(AzureLoginRequest request, String ipAddress) {
        AzureAuthService.AzureAuthResult authResult = azureAuthService.exchangeCodeForUser(request);
        if (authResult == null || !StringUtils.hasText(authResult.userInfo().azureAdId())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.AZURE_AUTH_FAILED, "Azure login failed");
        }

        AzureAuthService.AzureUserInfo azureUserInfo = authResult.userInfo();

        Optional<NguoiDung> byAzureId = nguoiDungRepository.findByAzureAdId(azureUserInfo.azureAdId());
        Optional<NguoiDung> byEmail = StringUtils.hasText(azureUserInfo.email())
                ? nguoiDungRepository.findByEmail(azureUserInfo.email())
                : Optional.empty();

        NguoiDung user = byAzureId.or(() -> byEmail)
                .filter(candidate -> candidate.getTrangThai() != null && candidate.getTrangThai() == 1)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        ErrorCode.AZURE_AUTH_FAILED,
                        "Azure account is not linked with an active user"
                ));

        user.setAzureAdId(azureUserInfo.azureAdId());
        user.setLanDangNhapCuoi(LocalDateTime.now());
        user.setNgayCapNhat(LocalDateTime.now());
        if (StringUtils.hasText(authResult.microsoftRefreshToken())) {
            user.setMicrosoftRefreshToken(authResult.microsoftRefreshToken());
            user.setMicrosoftTokenExpiry(LocalDateTime.now().plusDays(60));
        }
        nguoiDungRepository.save(user);

        AuthTokenResponse response = issueAuthTokenResponse(user);
        auditLogService.log(user.getId(), user.getHoTen(), "AZURE_LOGIN", "NguoiDung",
                user.getId(), "Azure login", ipAddress, 1);
        return response;
    }

    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.refreshToken().trim();
        if (!jwtService.isRefreshTokenValid(refreshToken) || !refreshTokenStoreService.isValid(refreshToken)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Invalid refresh token");
        }

        String username = jwtService.extractUsername(refreshToken);
        NguoiDung user = userService.findActiveUserByUsername(username);
        String newAccessToken = jwtService.generateAccessToken(user);
        String newRefreshToken = jwtService.generateRefreshToken(user);
        refreshTokenStoreService.replace(
                refreshToken,
                newRefreshToken,
                user.getId(),
                user.getUserName(),
                Instant.now().plusSeconds(authProperties.getJwt().getRefreshTokenSeconds())
        );
        return new RefreshTokenResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtService.getAccessTokenSeconds()
        );
    }

    public void logout(LogoutRequest request, String ipAddress) {
        String refreshToken = request.refreshToken().trim();
        refreshTokenStoreService.revoke(refreshToken);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof CurrentUserPrincipal principal) {
            NguoiDung user = userService.findActiveUserById(principal.getUserId());
            auditLogService.log(user.getId(), user.getHoTen(), "LOGOUT", "NguoiDung",
                    user.getId(), "User logout", ipAddress, 1);
        }
    }

    public CurrentUserResponse getCurrentUser() {
        return toCurrentUserResponse(getAuthenticatedUser());
    }

    public CurrentUserResponse updateCurrentUser(CurrentUserUpdateRequest request) {
        NguoiDung user = getAuthenticatedUser();
        String email = request.email().trim();
        if (nguoiDungRepository.existsByEmailAndIdNot(email, user.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE_EMAIL, "Email already exists");
        }

        user.setHoTen(request.hoTen().trim());
        user.setEmail(email);
        user.setNgayCapNhat(LocalDateTime.now());
        NguoiDung saved = nguoiDungRepository.save(user);
        return toCurrentUserResponse(saved);
    }

    private NguoiDung getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUserPrincipal principal)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Unauthorized");
        }
        return userService.findActiveUserById(principal.getUserId());
    }

    private CurrentUserResponse toCurrentUserResponse(NguoiDung user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getUserName(),
                user.getHoTen(),
                user.getEmail(),
                user.getDonVi() == null ? null : user.getDonVi().getId(),
                user.getNhomQuyen() == null ? null : user.getNhomQuyen().getId(),
                resolveRoles(user),
                resolvePermissions(user)
        );
    }

    private AuthTokenResponse issueAuthTokenResponse(NguoiDung user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = jwtService.generateRefreshToken(user);
        refreshTokenStoreService.save(
                refreshToken,
                user.getId(),
                user.getUserName(),
                Instant.now().plusSeconds(authProperties.getJwt().getRefreshTokenSeconds())
        );
        return new AuthTokenResponse(
                accessToken,
                refreshToken,
                "Bearer",
                jwtService.getAccessTokenSeconds(),
                new AuthUserResponse(
                        user.getId(),
                        user.getUserName(),
                        user.getHoTen(),
                        user.getEmail(),
                        resolveRoles(user)
                )
        );
    }

    private List<CurrentUserPermissionResponse> resolvePermissions(NguoiDung user) {
        if (user.getNhomQuyen() == null) {
            return List.of();
        }
        return phanQuyenRepository.findByNhomQuyen_Id(user.getNhomQuyen().getId()).stream()
                .filter(permission -> permission.getChucNang() != null)
                .map(permission -> new CurrentUserPermissionResponse(
                        permission.getChucNang().getMaChucNang(),
                        Boolean.TRUE.equals(permission.getIsView()),
                        Boolean.TRUE.equals(permission.getIsCreate()),
                        Boolean.TRUE.equals(permission.getIsEdit()),
                        Boolean.TRUE.equals(permission.getIsDelete()),
                        Boolean.TRUE.equals(permission.getIsApprove())
                ))
                .toList();
    }

    private List<String> resolveRoles(NguoiDung user) {
        if (user.getNhomQuyen() == null) {
            return List.of("USER");
        }
        if (StringUtils.hasText(user.getNhomQuyen().getMaNhomQuyen())) {
            return List.of(user.getNhomQuyen().getMaNhomQuyen().toUpperCase(Locale.ROOT));
        }
        return List.of("USER");
    }
}
