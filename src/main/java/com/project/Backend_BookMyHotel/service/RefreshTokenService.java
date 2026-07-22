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
    public RefreshToken createOrUpdateRefreshToken(String userId) {
        User user = userRepository.findByUserId(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

        String newToken = UUID.randomUUID().toString();
        Instant expiryDate = Instant.now().plusMillis(refreshTokenDurationMs);

        Optional<RefreshToken> existing = refreshTokenRepository.findByUser(user);

        if (existing.isPresent()) {
            RefreshToken refreshToken = existing.get();
            refreshToken.setToken(newToken);
            refreshToken.setExpiryDate(expiryDate);
            return refreshTokenRepository.save(refreshToken);
        }

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setToken(newToken);
        refreshToken.setExpiryDate(expiryDate);
        return refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public RefreshToken rotateRefreshToken(String oldToken) {
        RefreshToken existing = refreshTokenRepository.findByToken(oldToken)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (existing.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(existing);
            throw new RuntimeException("Refresh token was expired. Please sign in again.");
        }

        User user = existing.getUser();
        String newToken = UUID.randomUUID().toString();
        Instant newExpiry = Instant.now().plusMillis(refreshTokenDurationMs);

        existing.setToken(newToken);
        existing.setExpiryDate(newExpiry);

        return refreshTokenRepository.save(existing);
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

    @Transactional
    public void deleteByToken(String token) {
        refreshTokenRepository.deleteByToken(token);
    }

    @Transactional
    public void deleteByUserId(Long userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    public Optional<RefreshToken> findByToken(String requestRefreshToken) {
        return refreshTokenRepository.findByToken(requestRefreshToken);
    }
}
