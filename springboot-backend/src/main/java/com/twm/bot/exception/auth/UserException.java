package com.twm.bot.exception.auth;

sealed class UserException extends
        RuntimeException permits UserNotExistException, UserPasswordMismatchException {

    public UserException(String message) {
        super(message);
    }

}

