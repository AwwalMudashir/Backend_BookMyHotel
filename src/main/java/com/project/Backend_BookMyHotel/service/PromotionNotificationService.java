package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Promotion;
import com.project.Backend_BookMyHotel.domain.PromotionEmailNotification;
import com.project.Backend_BookMyHotel.domain.PromotionNotificationStatus;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.repository.PromotionEmailNotificationRepository;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PromotionNotificationService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PromotionEmailNotificationRepository notificationRepository;

    public void enqueuePromotionNotifications(Promotion promo) {
        try {
            List<User> recipients = userRepository.findByEmailNotificationsTrue();
            if (recipients == null || recipients.isEmpty()) return;

            for (User u : recipients) {
                if (notificationRepository.existsByPromotionIdAndRecipientEmailAndStatusIn(
                        promo.getId(),
                        u.getEmail(),
                        List.of(PromotionNotificationStatus.PENDING, PromotionNotificationStatus.SENT)
                )) {
                    continue;
                }

                PromotionEmailNotification queueItem = new PromotionEmailNotification();
                queueItem.setPromotionId(promo.getId());
                queueItem.setRecipientEmail(u.getEmail());
                queueItem.setStatus(PromotionNotificationStatus.PENDING);
                queueItem.setAttempts(0);
                notificationRepository.save(queueItem);
            }
        } catch (Exception e) {
            System.err.println("Error while queueing promotion notifications: " + e.getMessage());
        }
    }
}
