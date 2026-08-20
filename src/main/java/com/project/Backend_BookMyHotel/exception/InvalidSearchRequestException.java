package com.project.Backend_BookMyHotel.exception;

public class InvalidSearchRequestException extends RuntimeException {
    public InvalidSearchRequestException(String message) {
        super(message);
    }
}
