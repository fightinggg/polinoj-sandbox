package com.oj.polinojsandbox.openapi;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class FinalCodeMessage  {
    private Integer status;
    private Long submitId;
    private Long times;
    private Long memory;

}
