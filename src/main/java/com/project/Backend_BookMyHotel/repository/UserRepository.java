package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User,Long> {

    User findByEmail(String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    boolean existsByEmail(String email);

    boolean existsByUserId(String userId);

    Optional<User> findByUserId(String userId);

    Optional<User> findByGoogleId(String googleId);

    List<User> findByRole(com.project.Backend_BookMyHotel.dto.Role role);

    // Return all users with the specified email (case-sensitive DB match). Use when callers need to
    // handle duplicate records or Google/OAuth checks safely.
    List<User> findAllByEmail(String email);

    // Find users who've opted into promotional emails
    List<User> findByEmailNotificationsTrue();
}
