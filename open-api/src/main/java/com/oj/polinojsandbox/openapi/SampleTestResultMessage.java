package com.oj.polinojsandbox.openapi;

import lombok.Data;

@Data
public class SampleTestResultMessage<T> {
    String type;
    T data;

    public static <T> SampleTestResultMessage<T> buildMessage(String type, T data) {
        SampleTestResultMessage<T> sampleTestResultMessage = new SampleTestResultMessage<>();
        sampleTestResultMessage.setData(data);
        sampleTestResultMessage.setType(type);
        return sampleTestResultMessage;
    }
}
