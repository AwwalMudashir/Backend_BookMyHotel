package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.OtpVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OtpRepository extends JpaRepository<OtpVerification, Long> {
    Optional<OtpVerification> findByIdAndEmail(Long id, String email);

    Optional<OtpVerification> findByEmail(String email);

    Optional<OtpVerification> findByResetToken(String resetToken);
}
