package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.CreatePaymentIntentRequest;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.PaymentService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepo;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/intent")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<?> createPaymentIntent(
            @Valid @RequestBody CreatePaymentIntentRequest request,
            Authentication authentication
    ) {
        User user = userRepo.findByEmail(authentication.getName());
        return paymentService.createPaymentIntent(request.bookingId(), user.getId(), user.getRole());
    }

    // No @PreAuthorize / JWT here on purpose — Stripe calls this directly, server-to-server, with
    // no user session at all. The Stripe-Signature header is the only authentication this endpoint
    // has, which is exactly what Webhook.constructEvent() below verifies. It's also whitelisted in
    // AppConfig's permitAll list for the same reason.
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signatureHeader
    ) {
        Event event;
        if (log.isDebugEnabled()) {
            int len = payload == null ? 0 : payload.length();
            String prefix = payload == null ? "" : payload.substring(0, Math.min(1000, payload.length())).replaceAll("\\s+", " ");
            log.debug("Incoming raw webhook payload length={} prefix={}", len, prefix);
        }
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
            log.info("Stripe webhook signature verified for event {} type={}", event.getId(), event.getType());
        } catch (SignatureVerificationException e) {
            log.warn("Rejected Stripe webhook with invalid signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        try {
            log.info("Controller handing webhook event {} to PaymentService", event.getId());
            paymentService.handleWebhookEvent(event);
            log.info("Controller finished handling webhook event {} (no error thrown)", event.getId());
        } catch (Exception e) {
            // Returning 500 causes Stripe to retry — useful for transient processing failures.
            log.error("Error processing Stripe webhook event {} ({}): {}", event.getId(), event.getType(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Webhook processing failed");
        }

        // 3) Always acknowledge with 200 when processing completed successfully.
        return ResponseEntity.ok("");
    }

    // Legacy endpoint for backward compatibility — uses bookingId
    @GetMapping("/{bookingId}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<?> getPaymentStatus(
            @PathVariable Long bookingId,
            Authentication authentication
    ) {
        User user = userRepo.findByEmail(authentication.getName());
        return paymentService.getPaymentStatus(bookingId, user.getId(), user.getRole());
    }

    // New secure endpoint — uses paymentId instead of exposing database IDs in URLs
    @GetMapping("/by-id/{paymentId}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<?> getPaymentStatusByPaymentId(
            @PathVariable String paymentId,
            Authentication authentication
    ) {
        User user = userRepo.findByEmail(authentication.getName());
        return paymentService.getPaymentStatusByPaymentId(paymentId, user.getId(), user.getRole());
    }
}
