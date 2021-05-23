package com.oj.polinojsandbox.openapi;

import lombok.Data;

@Data
public class SampleTestRequestDTO {
    private Long runTimes;
    private Long ccTimes;
    private Long memory;
    private String code;
    private Long problemId;
    private String cosPath;
    private String samplesMD5;
}
