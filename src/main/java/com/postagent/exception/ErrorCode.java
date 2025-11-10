package com.postagent.exception;

/**
 * 错误码枚举
 */
public enum ErrorCode {
    SYSTEM_ERROR(5000, "System Error"),
    PROMPT_PROCESSING_ERROR(5001, "提示词处理错误"),
    PROMPT_NOT_FOUND(4004, "提示词未找到"),
    PROMPT_TEMPLATE_ERROR(5002, "提示词模板错误");

    private final Integer code;
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
