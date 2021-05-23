package com.oj.polinojsandbox.openapi;

import lombok.Data;

import java.util.List;

@Data
public class SampleTestResultDTO {
    Integer status;
    String submitId;
    Integer ccTime;
    String ccInfo;
    List<SampleTestResult> sampleTestResults;
}
