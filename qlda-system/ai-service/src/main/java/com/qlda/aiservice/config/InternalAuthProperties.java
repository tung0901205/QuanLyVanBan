package com.qlda.aiservice.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "internal.auth")
@Getter
@Setter
public class InternalAuthProperties {
    private String serviceName = "ai-service";
    private String serviceToken = "";
    private List<String> allowedServices = new ArrayList<>();
}
