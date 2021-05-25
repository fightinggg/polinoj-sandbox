package com.oj.polinojsandbox.openapi;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.apache.kafka.common.serialization.StringSerializer;


@Data
public class CCCodeMessage {
    private Integer ccTime;
    private String ccInfo;
    private Long submitId;

}
