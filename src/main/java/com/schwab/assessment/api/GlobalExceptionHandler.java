package com.schwab.assessment.api;

import com.schwab.assessment.orchestration.model.PolicyViolationException;
import com.schwab.assessment.service.InvalidUrlException;
import com.schwab.assessment.service.ShortLinkExpiredException;
import com.schwab.assessment.service.ShortLinkNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * Translates domain and orchestration exceptions into the service's
 * uniform {@link ApiResponse} error envelope with an appropriate HTTP
 * status, so callers never see a raw stack trace.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InvalidUrlException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidUrl(InvalidUrlException e) {
        return respond(HttpStatus.BAD_REQUEST, "INVALID_URL", e.getMessage());
    }

    @ExceptionHandler(ShortLinkNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(ShortLinkNotFoundException e) {
        return respond(HttpStatus.NOT_FOUND, "SHORT_LINK_NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(ShortLinkExpiredException.class)
    public ResponseEntity<ApiResponse<Void>> handleExpired(ShortLinkExpiredException e) {
        return respond(HttpStatus.GONE, "SHORT_LINK_EXPIRED", e.getMessage());
    }

    @ExceptionHandler(PolicyViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handlePolicyViolation(PolicyViolationException e) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "POLICY_VIOLATION:" + e.getRule(),
                e.getMessage() + " | remediation: " + e.getRemediationHint());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoSuchElementException e) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(IllegalArgumentException e) {
        return respond(HttpStatus.BAD_REQUEST, "BAD_REQUEST", e.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(IllegalStateException e) {
        return respond(HttpStatus.CONFLICT, "CONFLICT", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return respond(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("Unhandled exception", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    private ResponseEntity<ApiResponse<Void>> respond(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(ApiResponse.error(code, message));
    }
}
