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
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Rejected Stripe webhook with invalid signature: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        try {
            paymentService.handleWebhookEvent(event);
        } catch (Exception e) {
            // Acknowledge receipt regardless — Stripe retries on non-2xx, which is only useful for
            // the signature check above. A bug on our side processing an already-valid event isn't
            // something re-delivering the same webhook repeatedly would fix.
            log.error("Error processing Stripe webhook event {} ({}): {}", event.getId(), event.getType(), e.getMessage(), e);
        }

        return ResponseEntity.ok("");
    }

    @GetMapping("/{bookingId}")
    @PreAuthorize("hasAuthority('CUSTOMER')")
    public ResponseEntity<?> getPaymentStatus(
            @PathVariable Long bookingId,
            Authentication authentication
    ) {
        User user = userRepo.findByEmail(authentication.getName());
        return paymentService.getPaymentStatus(bookingId, user.getId(), user.getRole());
    }
}
