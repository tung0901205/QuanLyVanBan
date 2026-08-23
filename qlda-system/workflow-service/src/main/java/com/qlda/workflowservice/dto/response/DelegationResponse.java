package com.qlda.workflowservice.dto.response;

import java.time.LocalDate;

public record DelegationResponse(
        Long id,
        Long nguoiUyQuyenId,
        Long nguoiDuocUyQuyenId,
        LocalDate tuNgay,
        LocalDate denNgay,
        String phamViUyQuyen,
        String ghiChu,
        Boolean active
) {
}
