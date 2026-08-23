package com.qlda.authservice.security;

import com.qlda.authservice.entity.NguoiDung;
import com.qlda.authservice.repository.NguoiDungRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final NguoiDungRepository nguoiDungRepository;

    public DatabaseUserDetailsService(NguoiDungRepository nguoiDungRepository) {
        this.nguoiDungRepository = nguoiDungRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String loginValue) throws UsernameNotFoundException {
        String normalized = loginValue == null ? "" : loginValue.trim();

        NguoiDung user = nguoiDungRepository
                .findByUserNameIgnoreCaseOrEmailIgnoreCase(normalized, normalized)
                .orElseThrow(() -> new UsernameNotFoundException("Tài khoản không tồn tại"));

        boolean enabled = user.getTrangThai() != null && user.getTrangThai() == 1;
        if (!enabled || !StringUtils.hasText(user.getPassword())) {
            throw new UsernameNotFoundException("Tài khoản không hoạt động hoặc chưa có mật khẩu");
        }

        String role = user.getNhomQuyen() == null
                ? "USER"
                : user.getNhomQuyen().getMaNhomQuyen();
        String normalizedRole = StringUtils.hasText(role)
                ? role.trim().toUpperCase(Locale.ROOT)
                : "USER";

        return new CurrentUserPrincipal(
                user.getId(),
                user.getUserName(),
                user.getPassword(),
                true,
                List.of(new SimpleGrantedAuthority("ROLE_" + normalizedRole))
        );
    }
}
