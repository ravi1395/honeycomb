package com.honeycomb.core.locking;

/**
 * Thrown when a distributed lock cannot be acquired.
 *
 * @since 1.4.2
 */
public class LockNotAcquiredException extends RuntimeException {

    public LockNotAcquiredException(String message) {
        super(message);
    }

    public LockNotAcquiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
