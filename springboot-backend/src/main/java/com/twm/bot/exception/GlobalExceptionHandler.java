package com.twm.bot.exception;

import com.twm.bot.exception.auth.AuthLoginFailException;
import com.twm.bot.exception.auth.AuthServiceExcption;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleRuntimeExceptions(RuntimeException ex) {
        return ResponseEntity.badRequest().body(ex.getMessage());
    }

    // 這裡還可以添加具體的異常類型處理方法
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleGeneralExceptions(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error: " + ex.getMessage());
    }

    @ExceptionHandler(AuthLoginFailException.class)
    public ResponseEntity<Map<String, Object>> handleAuthLoginFailException(AuthLoginFailException ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Authentication failed. Please try again later.");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AuthServiceExcption.class)
    public ResponseEntity<Map<String, Object>> handleAuthServiceExcption(AuthServiceExcption ex) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("error", "Get access_token or profile failed.");
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

}