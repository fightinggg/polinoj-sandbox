package com.oj.polinojsandbox;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = SandBoxProperties.prefix)
@Component
@Data
public class SandBoxProperties {
    public final static String prefix = "polinoj.sandbox";
    private String running = ".running";
}
