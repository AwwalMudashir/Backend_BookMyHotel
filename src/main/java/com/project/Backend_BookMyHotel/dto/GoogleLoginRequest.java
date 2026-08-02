package com.project.Backend_BookMyHotel.dto;

import jakarta.validation.constraints.NotBlank;

// The ID token the frontend receives directly from Google Identity Services after the user signs
// in with their Google account. This backend verifies it server-side and never sees the user's
// Google password or a client secret.
public record GoogleLoginRequest(
        @NotBlank(message = "Google ID token is required")
        String idToken
) {}
