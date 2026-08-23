package com.qlda.workflowservice.dto.internal.response;

import java.time.LocalDateTime;

public record InternalWorkflowProgressItemResponse(
        Long documentId,
        String soKyHieu,
        String trichYeu,
        Long nguoiXuLyId,
        Integer trangThaiXuLy,
        Integer tyLeHoanThanh,
        LocalDateTime hanXuLy
) {
}
