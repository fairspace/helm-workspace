package io.fairspace.saturn.controller.exception;

import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import io.fairspace.saturn.controller.dto.ErrorDto;
import io.fairspace.saturn.services.metadata.validation.ValidationException;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorDto> handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest req) {
        var violations = ex.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .sorted()
                .collect(Collectors.joining("; "));
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation Error", "Violations: " + violations);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorDto> handleValidationException(ValidationException ex, HttpServletRequest req) {
        log.error("Validation error for request {} {}", req.getMethod(), req.getRequestURI(), ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation Error", ex.getViolations());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorDto> handleIllegalArgumentException(
            IllegalArgumentException ex, HttpServletRequest req) {
        log.error("Validation error for request {} {}", req.getMethod(), req.getRequestURI(), ex);
        return buildErrorResponse(HttpStatus.BAD_REQUEST, "Validation Error", ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorDto> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest req) {
        log.error("Access denied for request {} {}", req.getMethod(), req.getRequestURI(), ex);
        return buildErrorResponse(HttpStatus.FORBIDDEN, "Access Denied");
    }

    private ResponseEntity<ErrorDto> buildErrorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(new ErrorDto(status.value(), message, null));
    }

    private ResponseEntity<ErrorDto> buildErrorResponse(HttpStatus status, String message, Object info) {
        return ResponseEntity.status(status).body(new ErrorDto(status.value(), message, info));
    }
}
