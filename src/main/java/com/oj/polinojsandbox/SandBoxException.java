package com.oj.polinojsandbox;

import lombok.Getter;

@Getter
public class SandBoxException extends RuntimeException {
    private String code;
    private String msg;

    private SandBoxException() {

    }


    public static SandBoxException buildException(SandBoxErrorCode ojErrorCode) {
        SandBoxException ojException = new SandBoxException();
        ojException.code = ojErrorCode.getCode();
        ojException.msg = ojErrorCode.getMsg();
        return ojException;
    }

    public static SandBoxException buildException(SandBoxErrorCode ojErrorCode, String msg) {
        SandBoxException ojException = new SandBoxException();
        ojException.code = ojErrorCode.getCode();
        ojException.msg = ojErrorCode.getMsg() + "\n" + msg;
        return ojException;
    }
}
