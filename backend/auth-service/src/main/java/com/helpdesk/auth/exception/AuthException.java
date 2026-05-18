package com.helpdesk.auth.exception;

/**
 * Auth exception - thrown when authentication fails.
 * Optional HTTP status allows callers to return 403 for locked accounts vs 401 for bad credentials.
 */
public class AuthException extends RuntimeException {

    private final int httpStatus;

    public AuthException(String message) {
        this(message, 401);
    }

    public AuthException(String message, int httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public AuthException(String message, Throwable cause) {
        this(message, 401, cause);
    }

    public AuthException(String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
