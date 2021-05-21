package com.oj.polinojsandbox;

public enum SandBoxErrorCode {
    // 001 SandBox

    // 程序运行异常 000
    COMPILE_TIMEOUT("001-000-000-000", "编译超时"),
    RUNNING_TIMEOUT("001-000-000-001", "运行超时"),
    RUNNING_ERROR("001-000-000-002", "运行时错误"),
    RETURN_ERROR("001-000-000-003", "返回非0"),
    WRONG_ANSWER("001-000-000-004", "答案错误"),

    // 通用001
    UNKNOW_ERROR("001-001-000-000", "服务器发生未知错误");


    String code;

    String msg;

    SandBoxErrorCode(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }


    public String getCode() {
        return code;
    }

    public String getMsg() {
        return msg;
    }
}
