package com.qlda.documentservice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qlda.documentservice.client.AiServiceClient;
import com.qlda.documentservice.client.AuthServiceClient;
import com.qlda.documentservice.client.WorkflowServiceClient;
import com.qlda.documentservice.common.DocumentConstants;
import com.qlda.documentservice.dto.request.DocumentRequests;
import com.qlda.documentservice.entity.VanBan;
import com.qlda.documentservice.exception.BusinessException;
import com.qlda.documentservice.mapper.DocumentMapper;
import com.qlda.documentservice.notification.NotificationEventPublisher;
import com.qlda.documentservice.repository.LoaiVanBanRepository;
import com.qlda.documentservice.repository.TepDinhKemRepository;
import com.qlda.documentservice.repository.VanBanPhienBanRepository;
import com.qlda.documentservice.repository.VanBanRepository;
import com.qlda.documentservice.security.SecurityUtils;
import com.qlda.documentservice.service.DigitalSignatureService;
import com.qlda.documentservice.service.FileStorageService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DocumentWorkflowServiceImplSecurityTest {

    private VanBanRepository vanBanRepository;
    private SecurityUtils securityUtils;
    private DigitalSignatureService digitalSignatureService;
    private DocumentWorkflowServiceImpl service;

    @BeforeEach
    void setUp() {
        vanBanRepository = mock(VanBanRepository.class);
        securityUtils = mock(SecurityUtils.class);
        digitalSignatureService = mock(DigitalSignatureService.class);

        service = new DocumentWorkflowServiceImpl(
            vanBanRepository,
            mock(LoaiVanBanRepository.class),
            mock(TepDinhKemRepository.class),
            mock(DocumentMapper.class),
            mock(FileStorageService.class),
            securityUtils,
            mock(AuthServiceClient.class),
            mock(WorkflowServiceClient.class),
            mock(AiServiceClient.class),
            mock(NotificationEventPublisher.class),
            Optional.empty(),
            digitalSignatureService,
            mock(VanBanPhienBanRepository.class)
        );
    }

    @Test
    void digitalSignUsesAuthenticatedJwtUser() {
        VanBan document = approvedDocument();
        when(vanBanRepository.findByIdAndDaXoaFalse(10L)).thenReturn(Optional.of(document));
        when(securityUtils.getCurrentUserId()).thenReturn(Optional.of(42L));

        var request = new DocumentRequests.DigitalSignRequest("LOCAL_HASH_SHA256", "Approved");
        var response = service.digitalSign(10L, request);

        assertThat(response.nguoiKyId()).isEqualTo(42L);
        verify(digitalSignatureService).sign(document, 42L, request);
    }

    @Test
    void digitalSignRejectsDocumentThatWorkflowHasNotApproved() {
        VanBan document = approvedDocument();
        document.setTrangThaiQuyTrinh("APPROVAL_PENDING");
        when(vanBanRepository.findByIdAndDaXoaFalse(10L)).thenReturn(Optional.of(document));
        when(securityUtils.getCurrentUserId()).thenReturn(Optional.of(42L));

        var request = new DocumentRequests.DigitalSignRequest("LOCAL_HASH_SHA256", null);

        assertThatThrownBy(() -> service.digitalSign(10L, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("phe duyet");
        verify(vanBanRepository, never()).save(any());
        verify(digitalSignatureService, never()).sign(any(), any(), any());
    }

    @Test
    void digitalSignRejectsRequestWithoutAuthenticatedJwtIdentity() {
        VanBan document = approvedDocument();
        when(vanBanRepository.findByIdAndDaXoaFalse(10L)).thenReturn(Optional.of(document));
        when(securityUtils.getCurrentUserId()).thenReturn(Optional.empty());

        var request = new DocumentRequests.DigitalSignRequest("LOCAL_HASH_SHA256", null);

        assertThatThrownBy(() -> service.digitalSign(10L, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("JWT");
        verify(vanBanRepository, never()).save(any());
        verify(digitalSignatureService, never()).sign(any(), any(), any());
    }

    @Test
    void publishRejectsDocumentThatHasNotBeenDigitallySigned() {
        VanBan document = approvedDocument();
        document.setDaKySo(false);
        when(vanBanRepository.findByIdAndDaXoaFalse(10L)).thenReturn(Optional.of(document));

        var request = new DocumentRequests.PublishRequest(null, null);

        assertThatThrownBy(() -> service.publish(10L, request))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("ky so");
        verify(vanBanRepository, never()).save(any());
    }

    @Test
    void publishAcceptsOnlyConsistentlySignedDocument() {
        VanBan document = approvedDocument();
        document.setDaKySo(true);
        document.setTrangThai(DocumentConstants.TRANG_THAI_DA_KY);
        when(vanBanRepository.findByIdAndDaXoaFalse(10L)).thenReturn(Optional.of(document));

        var response = service.publish(10L, new DocumentRequests.PublishRequest(null, null));

        assertThat(response.trangThai()).isEqualTo(DocumentConstants.TRANG_THAI_DA_PHAT_HANH);
        assertThat(document.getNgayPhatHanh()).isNotNull();
        verify(vanBanRepository).save(document);
    }

    private VanBan approvedDocument() {
        VanBan document = new VanBan();
        document.setId(10L);
        document.setTrangThai(DocumentConstants.TRANG_THAI_TRINH_KY);
        document.setTrangThaiQuyTrinh("APPROVED");
        document.setDaKySo(false);
        return document;
    }
}
