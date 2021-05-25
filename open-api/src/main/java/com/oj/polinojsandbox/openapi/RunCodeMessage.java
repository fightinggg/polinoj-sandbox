package com.oj.polinojsandbox.openapi;

import lombok.Data;

import java.io.Serializable;

@Data
public class RunCodeMessage  {
    private Integer times;
    private Integer memory;
    private Integer returnCode;
    private Long submitId;
}
