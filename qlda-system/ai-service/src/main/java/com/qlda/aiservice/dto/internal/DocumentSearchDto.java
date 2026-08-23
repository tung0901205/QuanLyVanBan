package com.qlda.aiservice.dto.internal;

public record DocumentSearchDto(
    Long id,
    String soKyHieu,
    String trichYeu,
    String tenLoaiVanBan,
    Integer phanLoaiVanBan,
    Integer trangThai
) {
}
