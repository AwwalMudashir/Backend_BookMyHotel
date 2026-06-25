package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.RefreshToken;
import com.project.Backend_BookMyHotel.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    // Find a token in the database so we can verify it
    Optional<RefreshToken> findByToken(String token);

    // Useful for deleting tokens when a user logs out
    @Modifying
    void deleteByUser(User user);
}