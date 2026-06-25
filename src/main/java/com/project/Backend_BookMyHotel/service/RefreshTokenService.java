package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.RefreshToken;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.repository.RefreshTokenRepository;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    // 7 days in milliseconds
    private final Long refreshTokenDurationMs = 604800000L;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private UserRepository userRepository; // To fetch the user entity linked to the token

    // Create a brand new Refresh Token for a user during Login
    @Transactional
    public RefreshToken createRefreshToken(String userId) {
        RefreshToken refreshToken = new RefreshToken();

        // 1. Link it to the user
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        refreshToken.setUser(user);

        // 2. Set expiration date (Current time + 7 days)
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));

        // 3. Generate a secure, completely random universal unique identifier string
        refreshToken.setToken(UUID.randomUUID().toString());

        return refreshTokenRepository.save(refreshToken);
    }

    // Verify that the claim ticket hasn't expired yet
    public RefreshToken verifyExpiration(RefreshToken token) {

        // If the expiration timestamp is before the current moment in time...
        if (token.getExpiryDate().compareTo(Instant.now()) < 0) {
            refreshTokenRepository.delete(token);
            throw new RuntimeException("Refresh token was expired. Please make a new sign-in request");
        }

        return token;
    }

    public Optional<RefreshToken> findByToken(String requestRefreshToken) {
        return refreshTokenRepository.findByToken(requestRefreshToken);
    }
}
