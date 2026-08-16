package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.PromotionEmailNotification;
import com.project.Backend_BookMyHotel.domain.PromotionNotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PromotionEmailNotificationRepository extends JpaRepository<PromotionEmailNotification, Long> {
    List<PromotionEmailNotification> findTop50ByStatusAndAttemptsLessThanOrderByCreatedAtAsc(PromotionNotificationStatus status, Integer attempts);
    boolean existsByPromotionIdAndRecipientEmailAndStatusIn(Long promotionId, String recipientEmail, List<PromotionNotificationStatus> statuses);
}
