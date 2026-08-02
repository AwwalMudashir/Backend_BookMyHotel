package com.project.Backend_BookMyHotel.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.project.Backend_BookMyHotel.domain.RefreshToken;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.GoogleLoginRequest;
import com.project.Backend_BookMyHotel.dto.JwtResponse;
import com.project.Backend_BookMyHotel.dto.Role;
import com.project.Backend_BookMyHotel.exception.InvalidGoogleTokenException;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.util.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.GeneralSecurityException;
import java.io.IOException;
import java.util.Random;
import java.util.UUID;

@Service
public class GoogleAuthService {

    private static final Logger log = LoggerFactory.getLogger(GoogleAuthService.class);

    @Autowired
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private ResendEmailService resendEmailService;

    private final BCryptPasswordEncoder bencoder = new BCryptPasswordEncoder(12);

    public ResponseEntity<?> googleLogin(GoogleLoginRequest request) {
        GoogleIdToken.Payload payload = verifyIdToken(request.idToken());

        String email = payload.getEmail();
        Boolean emailVerified = payload.getEmailVerified();
        if (email == null || emailVerified == null || !emailVerified) {
            throw new InvalidGoogleTokenException("Google account email is not verified.");
        }

        String googleId = payload.getSubject();

        User user = userRepo.findByGoogleId(googleId).orElse(null);
        if (user == null) {
            // Not seen this Google account before — fall back to matching by email so someone
            // who already has a password account doesn't end up with a second, duplicate one.
            user = userRepo.findByEmail(email);
            if (user == null) {
                user = createUserFromGoogle(payload, googleId);
            } else if (user.getGoogleId() == null) {
                user.setGoogleId(googleId);
                userRepo.save(user);
            }
        }

        if (user.getIsActive() != null && user.getIsActive() == 'N') {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("This Account isn't Active Again");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        RefreshToken refreshToken = refreshTokenService.createOrUpdateRefreshToken(user.getUserId());

        JwtResponse jwtResponse = new JwtResponse(
                token,
                refreshToken.getToken(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getRole()
        );

        return ResponseEntity.ok(jwtResponse);
    }

    private GoogleIdToken.Payload verifyIdToken(String idTokenString) {
        try {
            GoogleIdToken idToken = googleIdTokenVerifier.verify(idTokenString);
            if (idToken == null) {
                throw new InvalidGoogleTokenException("Invalid or expired Google ID token.");
            }
            return idToken.getPayload();
        } catch (GeneralSecurityException | IOException | IllegalArgumentException e) {
            throw new InvalidGoogleTokenException("Could not verify Google ID token.");
        }
    }

    private User createUserFromGoogle(GoogleIdToken.Payload payload, String googleId) {
        User user = new User();
        user.setFirstName(stringClaim(payload, "given_name"));
        user.setLastName(stringClaim(payload, "family_name"));
        user.setEmail(payload.getEmail());
        // Google-only accounts never use this password — it's a random, unusable BCrypt hash
        // rather than a nullable column, so every other code path that reads user.password
        // (login, reset) keeps working without a null check.
        user.setPassword(bencoder.encode(UUID.randomUUID().toString()));
        user.setRole(Role.CUSTOMER);
        user.setManagedHotel(null);
        user.setIsActive('Y');
        user.setGoogleId(googleId);

        String userId = generateRandomId();
        while (userRepo.existsByUserId(userId)) {
            userId = generateRandomId();
        }
        user.setUserId(userId);

        userRepo.save(user);

        // The account is already persisted above — a failed welcome email (Resend sandbox
        // restriction, network hiccup, etc.) must not turn a successful Google sign-up into a
        // 500 with no JWT for the caller. Same lesson as BookingService.confirmBooking.
        try {
            String html = emailTemplateService.userWelcomeTemplate(user.getFirstName());
            resendEmailService.sendEmail(user.getEmail(), "Registeration Successful", html);
        } catch (Exception e) {
            log.error("Failed to send welcome email for Google sign-up {}: {}", user.getEmail(), e.getMessage(), e);
        }

        return user;
    }

    private String stringClaim(GoogleIdToken.Payload payload, String key) {
        Object value = payload.get(key);
        return value == null ? "" : value.toString();
    }

    private String generateRandomId() {
        Random random = new Random();
        return "BMH" + (random.nextInt() * 999999);
    }
}
