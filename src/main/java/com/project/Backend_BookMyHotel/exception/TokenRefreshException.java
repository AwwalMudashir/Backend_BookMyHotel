package com.project.Backend_BookMyHotel.exception;

/**
 * Raised when a refresh token is unknown, already rotated away or expired.
 * Mapped to 401 so the client can tell "session is dead, log in again" apart
 * from a genuine server fault.
 */
public class TokenRefreshException extends RuntimeException {

    public TokenRefreshException(String message) {
        super(message);
    }
}
