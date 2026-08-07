package com.project.Backend_BookMyHotel.dto;

import jakarta.validation.constraints.NotBlank;


public record GoogleLoginRequest(
        @NotBlank(message = "Google ID token is required")
        String idToken
) {}
