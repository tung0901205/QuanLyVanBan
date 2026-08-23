package com.qlda.aiservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.qlda.aiservice.config.InternalAuthProperties;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalServiceAuthenticationFilterTest {
    private InternalServiceAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        InternalAuthProperties properties = new InternalAuthProperties();
        properties.setServiceToken("strong-test-token");
        properties.setAllowedServices(List.of("document-service"));
        filter = new InternalServiceAuthenticationFilter(properties);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMissingToken() throws Exception {
        var request = request();
        request.addHeader("X-Service-Name", "document-service");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsUnknownCallingService() throws Exception {
        var request = request();
        request.addHeader("Authorization", "Bearer strong-test-token");
        request.addHeader("X-Service-Name", "unknown-service");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> {});
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    void authenticatesAllowedServiceAndContinuesChain() throws Exception {
        var request = request();
        request.addHeader("Authorization", "Bearer strong-test-token");
        request.addHeader("X-Service-Name", "document-service");
        var response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean(false);
        filter.doFilter(request, response, (req, res) -> {
            called.set(true);
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").contains("ROLE_INTERNAL_SERVICE");
        });
        assertThat(called).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/internal/ai/index-document/1");
        request.setServletPath("/internal/ai/index-document/1");
        return request;
    }
}
