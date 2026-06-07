package com.docintellect.api.exception;

public class DocIntelligenceException extends RuntimeException {

    private final String code;

    public DocIntelligenceException(String message, String code) {
        super(message);
        this.code = code;
    }

    public DocIntelligenceException(String message, String code, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
