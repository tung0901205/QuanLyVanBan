package com.qlda.documentservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.qlda.documentservice.config.AppProperties;
import com.qlda.documentservice.dto.request.DocumentRequests;
import com.qlda.documentservice.entity.ChuKySo;
import com.qlda.documentservice.entity.VanBan;
import com.qlda.documentservice.repository.ChuKySoRepository;
import com.qlda.documentservice.repository.TepDinhKemRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DigitalSignatureServiceTest {

    @Test
    void storesAuthenticatedSignerId() {
        ChuKySoRepository signatureRepository = mock(ChuKySoRepository.class);
        TepDinhKemRepository attachmentRepository = mock(TepDinhKemRepository.class);
        AppProperties appProperties = mock(AppProperties.class);
        when(appProperties.getUploadDir()).thenReturn(".");
        when(attachmentRepository.findByVanBan_Id(10L)).thenReturn(List.of());

        DigitalSignatureService service = new DigitalSignatureService(
            signatureRepository,
            attachmentRepository,
            appProperties
        );
        VanBan document = new VanBan();
        document.setId(10L);

        service.sign(
            document,
            42L,
            new DocumentRequests.DigitalSignRequest("LOCAL_HASH_SHA256", "Approved")
        );

        ArgumentCaptor<ChuKySo> signature = ArgumentCaptor.forClass(ChuKySo.class);
        verify(signatureRepository).save(signature.capture());
        assertThat(signature.getValue().getNguoiKyId()).isEqualTo(42L);
    }
}
