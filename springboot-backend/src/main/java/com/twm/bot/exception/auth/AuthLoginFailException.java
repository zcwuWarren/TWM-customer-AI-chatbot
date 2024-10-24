package com.twm.bot.exception.auth;

public class AuthLoginFailException extends RuntimeException {
    public AuthLoginFailException(String message, Throwable cause) {
        super(message, cause);
    }
}
