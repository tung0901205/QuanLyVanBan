package com.qlda.authservice.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LocalLoginRequest(
        @NotBlank(message = "username không được để trống")
        @Size(max = 150, message = "username không được vượt quá 150 ký tự")
        String username,

        @NotBlank(message = "password không được để trống")
        @Size(max = 100, message = "password không được vượt quá 100 ký tự")
        String password
) {
}
