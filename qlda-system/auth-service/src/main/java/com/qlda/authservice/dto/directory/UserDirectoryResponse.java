package com.qlda.authservice.dto.directory;

public record UserDirectoryResponse(
        Long id,
        String username,
        String hoTen,
        String email,
        Integer donViId,
        String tenDonVi,
        String chucVu,
        String maNhomQuyen,
        String tenNhomQuyen
) {
}
