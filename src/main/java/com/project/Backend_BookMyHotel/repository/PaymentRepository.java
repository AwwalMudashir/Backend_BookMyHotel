package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.Payment;
import com.project.Backend_BookMyHotel.dto.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByBookingId(Long bookingId);

    Optional<Payment> findByStripePaymentId(String stripePaymentId);

    // A booking can accumulate multiple payment attempts (e.g. a failed one followed by a retry) —
    // this is "whichever attempt happened most recently."
    Optional<Payment> findFirstByBookingIdOrderByIdDesc(Long bookingId);

    Optional<Payment> findFirstByBookingIdAndStatusOrderByIdDesc(Long bookingId, PaymentStatus status);
}
