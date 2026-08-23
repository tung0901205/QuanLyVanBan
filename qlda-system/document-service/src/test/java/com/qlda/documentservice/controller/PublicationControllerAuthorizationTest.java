package com.qlda.documentservice.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.qlda.documentservice.dto.request.DocumentRequests;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class PublicationControllerAuthorizationTest {

    @Test
    void finalBusinessActionsAreRestrictedToManagerRole() throws Exception {
        assertManagerOnly("digitalSign", Long.class, DocumentRequests.DigitalSignRequest.class);
        assertManagerOnly("publish", Long.class, DocumentRequests.PublishRequest.class);
        assertManagerOnly("send", Long.class, DocumentRequests.SendDocumentRequest.class);
    }

    private void assertManagerOnly(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = PublicationController.class.getMethod(methodName, parameterTypes);
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasRole('LANH_DAO')");
    }
}
