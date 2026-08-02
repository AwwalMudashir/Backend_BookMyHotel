package com.project.Backend_BookMyHotel.exception;

public class NoEligibleBookingException extends RuntimeException {
    public NoEligibleBookingException(String message) {
        super(message);
    }
}
