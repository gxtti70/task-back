package com.taskmanager.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "vapid")
@Getter
@Setter
public class VapidProperties {
    private String publicKey;
    private String privateKey;
    private String subject = "mailto:admin@taskmanager.local";
}
