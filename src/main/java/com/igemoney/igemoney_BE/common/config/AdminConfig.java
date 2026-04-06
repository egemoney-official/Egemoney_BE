package com.igemoney.igemoney_BE.common.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "app.auth")
@Getter @Setter
public class AdminConfig {
    private List<Long> adminIds;
}
