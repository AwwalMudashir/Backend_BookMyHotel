package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Promotion;
import com.project.Backend_BookMyHotel.domain.PromotionEmailNotification;
import com.project.Backend_BookMyHotel.domain.PromotionNotificationStatus;
import com.project.Backend_BookMyHotel.repository.PromotionEmailNotificationRepository;
import com.project.Backend_BookMyHotel.repository.PromotionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class PromotionNotificationDispatcher {

    @Autowired
    private PromotionEmailNotificationRepository notificationRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private EmailTemplateService emailTemplateService;

    @Autowired
    private ResendEmailService resendEmailService;

    @Scheduled(fixedDelayString = "${app.promotion.notification.poll-interval-ms:30000}")
    public void dispatchPendingNotifications() {
        List<PromotionEmailNotification> pendingItems = notificationRepository
                .findTop50ByStatusAndAttemptsLessThanOrderByCreatedAtAsc(PromotionNotificationStatus.PENDING, 3);

        for (PromotionEmailNotification item : pendingItems) {
            try {
                Promotion promo = promotionRepository.findById(item.getPromotionId()).orElse(null);
                if (promo == null) {
                    item.setStatus(PromotionNotificationStatus.FAILED);
                    item.setLastError("Promotion not found for queued notification.");
                    item.setProcessedAt(LocalDateTime.now());
                    notificationRepository.save(item);
                    continue;
                }

                String html = emailTemplateService.promotionAnnouncementTemplate(
                        promo.getCode(),
                        promo.getDiscountType(),
                        promo.getDiscountValue(),
                        promo.getHotel().getName(),
                        promo.getHotel().getLongImage(),
                        promo.getValidTo()
                );

                boolean success = resendEmailService.sendEmail(
                        item.getRecipientEmail(),
                        "New promotion from " + promo.getHotel().getName(),
                        html
                );

                item.setProcessedAt(LocalDateTime.now());
                item.setUpdatedAt(LocalDateTime.now());
                if (success) {
                    item.setStatus(PromotionNotificationStatus.SENT);
                } else {
                    item.setAttempts(item.getAttempts() + 1);
                    item.setLastError("Email service returned false.");
                    if (item.getAttempts() >= 3) {
                        item.setStatus(PromotionNotificationStatus.FAILED);
                    }
                }
            } catch (Exception ex) {
                item.setAttempts(item.getAttempts() + 1);
                item.setLastError(ex.getMessage());
                if (item.getAttempts() >= 3) {
                    item.setStatus(PromotionNotificationStatus.FAILED);
                }
                item.setUpdatedAt(LocalDateTime.now());
            }

            notificationRepository.save(item);
        }
    }
}
