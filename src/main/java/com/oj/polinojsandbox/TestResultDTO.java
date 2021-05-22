package com.oj.polinojsandbox;

import lombok.Data;

import java.util.List;

@Data
public class TestResultDTO {
    Integer ccTime;
    String ccInfo;
    List<SampleTestResult> sampleTestResults;
}
