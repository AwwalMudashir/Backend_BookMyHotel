package com.project.Backend_BookMyHotel.dto;

public record TokenRefreshResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {
    // Convenience constructor so you only have to pass two arguments
    public TokenRefreshResponse(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, "Bearer");
    }
}
