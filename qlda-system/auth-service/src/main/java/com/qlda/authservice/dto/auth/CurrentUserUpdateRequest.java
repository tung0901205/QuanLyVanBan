package com.qlda.authservice.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CurrentUserUpdateRequest(
        @NotBlank(message = "hoTen is required")
        @Size(max = 255, message = "hoTen must be <= 255 chars")
        String hoTen,
        @NotBlank(message = "email is required")
        @Email(message = "email is invalid")
        @Size(max = 150, message = "email must be <= 150 chars")
        String email
) {
}
