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
    private Integer concurrentTestSize = 2;
    private String ccCpus = "0.1";
    private String ccMemoryMb = "200";
    private String runCpus = "0.1";
    private String checkCpus = "0.1";
}
