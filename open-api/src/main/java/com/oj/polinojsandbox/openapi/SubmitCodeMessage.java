package com.oj.polinojsandbox.openapi;

import lombok.Data;

import java.io.Serializable;

@Data
public class SubmitCodeMessage{
    private Long runTimes;
    private Long ccTimes;
    private Long memory;
    private String code;
    private Long problemId;
    private String cosPath;
    private String samplesMD5;
    private Long submitId;
}
