package com.qlda.aiservice.dto.internal;

import java.time.LocalDateTime;

public record DocumentMetadataDto(
    Long id,
    String soKyHieu,
    String trichYeu,
    Integer loaiVanBanId,
    String tenLoaiVanBan,
    String documentType,
    Integer donViChuTriId,
    Long nguoiTaoId,
    LocalDateTime hanXuLy,
    Integer trangThai,
    Boolean daOCR,
    Boolean daKySo
) {
}
