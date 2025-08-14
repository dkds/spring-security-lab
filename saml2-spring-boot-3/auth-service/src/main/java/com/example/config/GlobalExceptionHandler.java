package com.example.config;

import com.example.dto.ErrorResponseDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleException(HttpServletRequest req, IllegalArgumentException e) {
        log.error("Request: {} raised exception", req.getRequestURL(), e);
        return ResponseEntity.badRequest().body(new ErrorResponseDto(e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleException(HttpServletRequest req, Exception e) {
        log.error("Request: {} raised exception", req.getRequestURL(), e);
        return ResponseEntity.internalServerError().body(new ErrorResponseDto(e.getMessage()));
    }
}
