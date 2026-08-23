package com.qlda.authservice.dto.auth;

public record CurrentUserPermissionResponse(
        String maChucNang,
        Boolean isView,
        Boolean isCreate,
        Boolean isEdit,
        Boolean isDelete,
        Boolean isApprove
) {
}
