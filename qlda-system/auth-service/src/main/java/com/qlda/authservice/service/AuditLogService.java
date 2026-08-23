package com.qlda.authservice.service;

import com.qlda.authservice.dto.audit.AuditLogExportResponse;
import com.qlda.authservice.dto.audit.AuditLogResponse;
import com.qlda.authservice.dto.common.PageData;
import java.time.LocalDate;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AuditLogService {

    private final PersistentAuditLogStore store;

    public AuditLogService(PersistentAuditLogStore store) {
        this.store = store;
        log(1L, "System", "BOOT", "AuthService", 0L,
                "Service initialized", "127.0.0.1", 1);
    }

    public void log(
            Long userId,
            String hoTen,
            String action,
            String objectName,
            Long objectId,
            String detail,
            String ip,
            Integer status
    ) {
        store.insert(userId, hoTen, action, objectName, objectId, detail, ip, status);
    }

    public PageData<AuditLogResponse> getAuditLogs(
            Pageable pageable,
            Long userId,
            String hanhDong,
            String doiTuong,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return store.findPage(pageable, userId, hanhDong, doiTuong, fromDate, toDate);
    }

    public AuditLogResponse getAuditLogById(Long id) {
        return store.findById(id);
    }

    public AuditLogExportResponse exportLogs(String format, LocalDate fromDate, LocalDate toDate) {
        return store.export(fromDate, toDate);
    }
}
