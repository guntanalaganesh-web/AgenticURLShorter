package com.schwab.assessment.api;

import java.time.Instant;

/**
 * The uniform envelope every REST response in this service returns, so
 * clients can rely on one shape whether a call succeeded or failed.
 *
 * @param success  whether the request succeeded
 * @param data     the payload on success (null on failure)
 * @param error    error details on failure (null on success)
 * @param timestamp when this response was constructed
 */
public record ApiResponse<T>(boolean success, T data, ApiError error, Instant timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, null, new ApiError(code, message), Instant.now());
    }

    /**
     * @param code    a stable, machine-readable error identifier
     * @param message a human-readable explanation
     */
    public record ApiError(String code, String message) {
    }
}
