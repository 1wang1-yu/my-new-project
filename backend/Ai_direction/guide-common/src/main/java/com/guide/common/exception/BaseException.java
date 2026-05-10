package com.guide.common.exception;

import lombok.Getter;

@Getter
public class BaseException extends RuntimeException {

    private final int code;

    public BaseException(String message) {
        this(1, message);
    }

    public BaseException(int code, String message) {
        super(message);
        this.code = code;
    }
}
