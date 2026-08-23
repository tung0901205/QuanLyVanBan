package com.qlda.aiservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import com.qlda.aiservice.config.InternalAuthProperties;

@SpringBootApplication
@EnableAsync
@EnableConfigurationProperties(InternalAuthProperties.class)
public class AiServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiServiceApplication.class, args);
    }

}
