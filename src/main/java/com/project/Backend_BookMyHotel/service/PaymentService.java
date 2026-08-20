package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.domain.Payment;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import com.project.Backend_BookMyHotel.dto.PaymentIntentResponse;
import com.project.Backend_BookMyHotel.dto.PaymentStatus;
import com.project.Backend_BookMyHotel.dto.PaymentStatusResponse;
import com.project.Backend_BookMyHotel.dto.Role;
import com.project.Backend_BookMyHotel.repository.BookingRepository;
import com.project.Backend_BookMyHotel.repository.PaymentRepository;
// Stripe's own model/param/exception classes — the SDK is organized as a set of static calls
// (PaymentIntent.create(), Refund.create(), Webhook.constructEvent()) rather than an injected
// client object, so there's no "StripeClient" bean here the way there is for e.g. Cloudinary.
import com.stripe.exception.StripeException;
import com.stripe.model.Charge;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    // Stripe requires these currencies passed as whole units (e.g. 100 = ¥100), not the usual
    // "amount x 100" smallest-unit convention every 2-decimal currency (GBP, USD, AED...) uses.
    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "BIF", "CLP", "DJF", "GNF", "JPY", "KMF", "KRW", "MGA", "PYG", "RWF",
            "UGX", "VND", "VUV", "XAF", "XOF", "XPF"
    );

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private BookingRepository bookingRepository;

    // Circular on purpose: BookingService.cancelBooking() calls paymentService.initiateRefund(),
    // and confirmPayment() below calls bookingService.confirmBooking(). BookingService's field for
    // this class is marked @Lazy (see the comment there) so Spring can construct both beans without
    // deadlocking on "who gets built first" — this side stays a normal eager reference.
    @Autowired
    private BookingService bookingService;

    @Transactional
    public ResponseEntity<?> createPaymentIntent(Long bookingId, Long userId, Role userRole) {
        Booking booking = bookingRepository.findByIdForUpdate(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found with ID: " + bookingId));

        if ("CUSTOMER".equalsIgnoreCase(userRole.toString()) && !booking.getUser().getId().equals(userId)) {
            return ResponseEntity.badRequest().body("You are not authorized to pay for this booking.");
        }

        Optional<Payment> succeededOpt = paymentRepository
                .findFirstByBookingIdAndStatusOrderByIdDesc(bookingId, PaymentStatus.SUCCEEDED);
        if (succeededOpt.isPresent()) {
            Payment succeeded = succeededOpt.get();
            return ResponseEntity.ok(toIntentResponse(
                    succeeded,
                    booking.getTotalPrice(),
                    booking.getRoom().getBranch().getCurrency(),
                    null,
                    PaymentStatus.SUCCEEDED,
                    "succeeded"
            ));
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            return ResponseEntity.badRequest().body("Only PENDING bookings can be paid for. Current status: " + booking.getStatus());
        }

        // Amount and currency always come from the persisted booking, never from the request —
        // accepting either from the client would let someone pay $1 for a $500 stay by editing
        // the request body before it hits this endpoint.
        BigDecimal amount = booking.getTotalPrice();
        String currency = booking.getRoom().getBranch().getCurrency();

        // A retry (page refresh, double-click, flaky network) would otherwise call this endpoint
        // again and create a brand new Stripe PaymentIntent + Payment row every time, leaving a
        // trail of abandoned PENDING rows for one booking. If there's already a PENDING attempt,
        // reuse it — its existing PaymentIntent, if Stripe says it's still payable, or a fresh
        // PaymentIntent written onto the same row otherwise — instead of inserting a new one.
        Optional<Payment> pendingOpt = paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(bookingId, PaymentStatus.PENDING);

        if (pendingOpt.isPresent()) {
            Payment pending = pendingOpt.get();
            try {
                log.info("Found existing pending Payment ({}) for booking {}. Checking Stripe status.", pending.getId(), bookingId);
                PaymentIntent existingIntent = PaymentIntent.retrieve(pending.getStripePaymentId());
                String stripeStatus = existingIntent.getStatus();

                if ("succeeded".equals(stripeStatus)) {
                    log.info("Stripe already completed PaymentIntent {}. Reconciling the missed webhook for booking {}.",
                            existingIntent.getId(), bookingId);
                    completeSuccessfulPayment(pending);
                    return ResponseEntity.ok(toIntentResponse(
                            pending, amount, currency, existingIntent, PaymentStatus.SUCCEEDED, stripeStatus));
                }

                if (isAwaitingSettlement(stripeStatus)) {
                    log.info("PaymentIntent {} is still {}. Sending the customer to payment status polling.",
                            existingIntent.getId(), stripeStatus);
                    return ResponseEntity.ok(toIntentResponse(
                            pending, amount, currency, existingIntent, PaymentStatus.PENDING, stripeStatus));
                }

                if (isReusable(existingIntent.getStatus())) {
                    log.info("Reusing existing Stripe PaymentIntent {} with status {} for booking {}.", existingIntent.getId(), existingIntent.getStatus(), bookingId);
                    return ResponseEntity.ok(toIntentResponse(
                            pending, amount, currency, existingIntent, PaymentStatus.PENDING, stripeStatus));
                }
                log.info("Existing Stripe PaymentIntent {} status {} is not reusable; creating a fresh one for booking {}.",
                        existingIntent.getId(), existingIntent.getStatus(), bookingId);
            } catch (StripeException e) {
                log.error("Could not retrieve existing PaymentIntent {} for booking {}: {}",
                        pending.getStripePaymentId(), bookingId, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                        .body("Could not verify the previous payment attempt. Please try again in a moment.");
            }

            try {
                // A completed/cancelled intent cannot be reused. The retry key must differ from
                // the original key or Stripe idempotency will return that same unusable intent.
                String retryKey = "booking-intent-retry-" + bookingId + "-" + pending.getStripePaymentId();
                PaymentIntent intent = createStripeIntent(bookingId, booking, amount, currency, retryKey);
                pending.setStripePaymentId(intent.getId());
                pending.setAmount(amount);
                pending.setCurrency(currency);
                paymentRepository.save(pending);

                return ResponseEntity.ok(toIntentResponse(
                        pending, amount, currency, intent, PaymentStatus.PENDING, intent.getStatus()));
            } catch (StripeException e) {
                log.error("Stripe error creating PaymentIntent for booking {}: {}", bookingId, e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Could not start payment: " + e.getMessage());
            }
        }

        try {
            String initialKey = "booking-intent-" + bookingId + "-" + booking.getReference();
            PaymentIntent intent = createStripeIntent(bookingId, booking, amount, currency, initialKey);

            Payment payment = new Payment();
            payment.setBooking(booking);
            payment.setStripePaymentId(intent.getId());
            payment.setAmount(amount);
            payment.setCurrency(currency);
            payment.setStatus(PaymentStatus.PENDING);
            paymentRepository.save(payment);

            return ResponseEntity.status(HttpStatus.CREATED).body(PaymentIntentResponse.builder()
                    .bookingId(bookingId)
                    .paymentId(payment.getPaymentId())
                    .paymentIntentId(intent.getId())
                    .clientSecret(intent.getClientSecret())
                    .amount(amount)
                    .currency(currency)
                    .status(PaymentStatus.PENDING)
                    .stripeStatus(intent.getStatus())
                    .build());
        } catch (StripeException e) {
            log.error("Stripe error creating PaymentIntent for booking {}: {}", bookingId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Could not start payment: " + e.getMessage());
        }
    }

    private PaymentIntent createStripeIntent(Long bookingId, Booking booking, BigDecimal amount, String currency,
                                             String idempotencyKey) throws StripeException {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(toSmallestUnit(amount, currency))
                .setCurrency(currency.toLowerCase(Locale.ROOT))
                .putMetadata("bookingId", String.valueOf(bookingId))
                .putMetadata("bookingReference", booking.getReference())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build())
                .build();

        RequestOptions requestOptions = RequestOptions.builder().setIdempotencyKey(idempotencyKey).build();

        return PaymentIntent.create(params, requestOptions);
    }

    // Only these statuses mean the client can still complete payment against the existing
    // PaymentIntent using its existing client_secret. "processing"/"succeeded"/"canceled" (and
    // anything else) all need a fresh PaymentIntent instead.
    private boolean isReusable(String stripeStatus) {
        return "requires_payment_method".equals(stripeStatus)
                || "requires_confirmation".equals(stripeStatus)
                || "requires_action".equals(stripeStatus);
    }

    private boolean isAwaitingSettlement(String stripeStatus) {
        return "processing".equals(stripeStatus) || "requires_capture".equals(stripeStatus);
    }

    private PaymentIntentResponse toIntentResponse(Payment payment, BigDecimal amount, String currency,
                                                   PaymentIntent intent, PaymentStatus status,
                                                   String stripeStatus) {
        boolean payable = intent != null && isReusable(stripeStatus);
        return PaymentIntentResponse.builder()
                .bookingId(payment.getBooking().getId())
                .paymentId(payment.getPaymentId())
                .paymentIntentId(intent != null ? intent.getId() : payment.getStripePaymentId())
                .clientSecret(payable ? intent.getClientSecret() : null)
                .amount(amount)
                .currency(currency)
                .status(status)
                .stripeStatus(stripeStatus)
                .build();
    }

    // Entry point for the webhook controller.
    // This method is intentionally verbose with logs so the full processing path is visible in
    // the application logs: controller -> handleWebhookEvent -> case handler -> DB updates.
    public void handleWebhookEvent(Event event) {
        Optional<com.stripe.model.StripeObject> dataObject = event.getDataObjectDeserializer().getObject();

        log.info("Stripe webhook event received in service: {} type={}", event.getId(), event.getType());

        if (dataObject.isEmpty()) {
            log.warn("Event {} has no data object deserializable by Stripe SDK", event.getId());
        } else {
            log.debug("Event {} data object class: {}", event.getId(), dataObject.get().getClass().getName());
        }

        switch (event.getType()) {
            case "payment_intent.succeeded" -> {
                // payment_intent.succeeded: the canonical Stripe signal that payment completed.
                log.info("Handling payment_intent.succeeded for event {}", event.getId());
                dataObject
                        .filter(PaymentIntent.class::isInstance)
                        .map(PaymentIntent.class::cast)
                        .ifPresentOrElse(intent -> {
                            log.info("Deserialized PaymentIntent {} from event {} — calling confirmPayment", intent.getId(), event.getId());
                            confirmPayment(intent.getId());
                            log.info("Finished confirmPayment for PaymentIntent {}", intent.getId());
                        }, () -> {
                            // SDK couldn't deserialize the nested object — try stronger fallbacks.
                            try {
                                // 1) Try the provided raw JSON for the data.object
                                String raw = event.getDataObjectDeserializer().getRawJson();
                                if (raw != null && !raw.isBlank()) {
                                    raw = raw.replace("\uFEFF", "");
                                    Gson gson = new Gson();
                                    try {
                                        PaymentIntent parsed = gson.fromJson(raw, PaymentIntent.class);
                                        if (parsed != null && parsed.getId() != null) {
                                            log.info("Fallback-parsed PaymentIntent {} from raw JSON — calling confirmPayment (event {})", parsed.getId(), event.getId());
                                            confirmPayment(parsed.getId());
                                            log.info("Finished confirmPayment for fallback-parsed PaymentIntent {}", parsed.getId());
                                            return;
                                        }
                                    } catch (Exception e) {
                                        log.debug("Raw JSON parse as PaymentIntent failed: {}", e.getMessage());
                                    }
                                }

                                // 2) Try parsing the whole Event JSON (more robust) to extract data.object.id
                                Optional<String> extracted = extractNestedObjectIdFromEvent(event);
                                if (extracted.isPresent()) {
                                    String id = extracted.get();
                                    log.info("Extracted nested id {} from event.toJson() — calling confirmPayment (event {})", id, event.getId());
                                    confirmPayment(id);
                                    log.info("Finished confirmPayment for extracted PaymentIntent {}", id);
                                    return;
                                }

                                log.warn("payment_intent.succeeded event {} could not be deserialized by SDK and all fallbacks returned empty", event.getId());
                            } catch (Exception e) {
                                log.warn("Failed fallbacks for payment_intent.succeeded event {}: {}", event.getId(), e.getMessage(), e);
                            }
                        });
            }
            case "payment_intent.payment_failed" -> {
                // Client-side failure path — mark the Payment FAILED so frontend shows proper state.
                log.info("Handling payment_intent.payment_failed for event {}", event.getId());
                dataObject
                        .filter(PaymentIntent.class::isInstance)
                        .map(PaymentIntent.class::cast)
                        .ifPresentOrElse(intent -> {
                            log.info("Deserialized failed PaymentIntent {} from event {} — calling markPaymentFailed", intent.getId(), event.getId());
                            markPaymentFailed(intent.getId());
                            log.info("Finished markPaymentFailed for PaymentIntent {}", intent.getId());
                        }, () -> {
                            // Fallbacks like in succeeded: raw parse then event.toJson extract
                            try {
                                String raw = event.getDataObjectDeserializer().getRawJson();
                                if (raw != null && !raw.isBlank()) {
                                    raw = raw.replace("\uFEFF", "");
                                    Gson gson = new Gson();
                                    try {
                                        PaymentIntent parsed = gson.fromJson(raw, PaymentIntent.class);
                                        if (parsed != null && parsed.getId() != null) {
                                            log.info("Fallback-parsed failed PaymentIntent {} from raw JSON — calling markPaymentFailed (event {})", parsed.getId(), event.getId());
                                            markPaymentFailed(parsed.getId());
                                            log.info("Finished markPaymentFailed for fallback-parsed PaymentIntent {}", parsed.getId());
                                            return;
                                        }
                                    } catch (Exception e) {
                                        log.debug("Raw JSON parse as PaymentIntent failed: {}", e.getMessage());
                                    }
                                }
                                Optional<String> extracted = extractNestedObjectIdFromEvent(event);
                                if (extracted.isPresent()) {
                                    String id = extracted.get();
                                    log.info("Extracted nested id {} from event.toJson() — calling markPaymentFailed (event {})", id, event.getId());
                                    markPaymentFailed(id);
                                    log.info("Finished markPaymentFailed for extracted PaymentIntent {}", id);
                                    return;
                                }
                                log.warn("payment_intent.payment_failed event {} could not be deserialized by SDK and all fallbacks returned empty", event.getId());
                            } catch (Exception e) {
                                log.warn("Failed fallbacks for payment_intent.payment_failed event {}: {}", event.getId(), e.getMessage(), e);
                            }
                        });
            }
            case "charge.succeeded" -> {
                // Occasionally Stripe sends charge.* events in addition to payment_intent.* — handle
                // charge.succeeded to cover webhooks that include only the charge object.
                log.info("Handling charge.succeeded for event {}", event.getId());
                dataObject
                        .filter(Charge.class::isInstance)
                        .map(Charge.class::cast)
                        .ifPresent(charge -> {
                            log.info("Deserialized Charge {} from event {} — calling confirmChargeSucceeded", charge.getId(), event.getId());
                            confirmChargeSucceeded(charge);
                            log.info("Finished confirmChargeSucceeded for Charge {}", charge.getId());
                        });
            }
            case "charge.updated" -> {
                // Some Stripe setups send charge.updated rather than charge.succeeded. Attempt to
                // handle the common case (status -> succeeded/refunded) by parsing the Charge.
                log.info("Handling charge.updated for event {}", event.getId());
                // First try the SDK-deserialized object.
                dataObject
                        .filter(Charge.class::isInstance)
                        .map(Charge.class::cast)
                        .ifPresentOrElse(charge -> {
                            log.info("Deserialized Charge {} from event {} — status={}", charge.getId(), event.getId(), charge.getStatus());
                            if ("succeeded".equalsIgnoreCase(charge.getStatus())) {
                                confirmChargeSucceeded(charge);
                            } else if ("refunded".equalsIgnoreCase(charge.getStatus())) {
                                markChargeRefunded(charge);
                            } else {
                                log.debug("charge.updated for Charge {} status {} — no action taken", charge.getId(), charge.getStatus());
                            }
                        }, () -> {
                            // Fallback: try to parse the raw JSON payload if the SDK couldn't deserialize.
                            try {
                                String raw = event.getDataObjectDeserializer().getRawJson();
                                if (raw != null && !raw.isBlank()) {
                                    // Remove common BOM if present
                                    raw = raw.replace("\uFEFF", "");
                                    Gson gson = new Gson();
                                    Charge parsed = gson.fromJson(raw, Charge.class);
                                    if (parsed != null) {
                                        log.info("Fallback-parsed Charge {} from raw JSON (status={}) — event {}", parsed.getId(), parsed.getStatus(), event.getId());
                                        if ("succeeded".equalsIgnoreCase(parsed.getStatus())) {
                                            confirmChargeSucceeded(parsed);
                                        } else if ("refunded".equalsIgnoreCase(parsed.getStatus())) {
                                            markChargeRefunded(parsed);
                                        } else {
                                            log.debug("Fallback charge.updated parsed Charge {} status {} — no action taken", parsed.getId(), parsed.getStatus());
                                        }
                                        return;
                                    }
                                }
                                log.warn("charge.updated event {} could not be deserialized by SDK and fallback parse returned null/raw empty", event.getId());
                            } catch (Exception e) {
                                log.warn("Failed fallback parse for charge.updated event {}: {}", event.getId(), e.getMessage(), e);
                            }
                        });
            }
            case "charge.refunded" -> {
                log.info("Handling charge.refunded for event {}", event.getId());
                dataObject
                        .filter(Charge.class::isInstance)
                        .map(Charge.class::cast)
                        .ifPresent(charge -> {
                            log.info("Deserialized refunded Charge {} from event {} — calling markChargeRefunded", charge.getId(), event.getId());
                            markChargeRefunded(charge);
                            log.info("Finished markChargeRefunded for Charge {}", charge.getId());
                        });
            }
            default -> {
                log.debug("Ignoring unhandled Stripe event type: {} (event {})", event.getType(), event.getId());
            }
        }
    }

    private void confirmChargeSucceeded(Charge charge) {
        String paymentIntentId = charge.getPaymentIntent();
        if (paymentIntentId == null) {
            log.warn("charge.succeeded event {} did not include a PaymentIntent id", charge.getId());
            return;
        }
        confirmPayment(paymentIntentId);
    }

    // Fires from the payment_intent.succeeded webhook, once Stripe confirms the card actually
    // charged successfully — this, not the client calling back, is the trustworthy signal.
    @Transactional
    public void confirmPayment(String paymentIntentId) {
        log.info("confirmPayment called for PaymentIntent {}", paymentIntentId);
        Optional<Payment> paymentOpt = paymentRepository.findByStripePaymentId(paymentIntentId);
        if (paymentOpt.isEmpty()) {
            log.warn("Received payment_intent.succeeded for unknown PaymentIntent {} — no DB row found", paymentIntentId);
            return;
        }

        Payment payment = paymentOpt.get();
        log.debug("Found DB payment id={} with status={} for PaymentIntent {}", payment.getId(), payment.getStatus(), paymentIntentId);
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            log.info("Payment {} already marked SUCCEEDED; ignoring duplicate webhook.", paymentIntentId);
            return; // Stripe can deliver the same webhook more than once — already handled.
        }

        completeSuccessfulPayment(payment);
    }

    private void completeSuccessfulPayment(Payment payment) {
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return;
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);
        log.info("Payment row id={} marked SUCCEEDED (PaymentIntent={})",
                payment.getId(), payment.getStripePaymentId());

        if (payment.getBooking().getStatus() == BookingStatus.PENDING) {
            bookingService.confirmBooking(payment.getBooking().getId());
            log.info("Triggered booking confirmation for booking id={}", payment.getBooking().getId());
        }
    }

    @Transactional
    public void markPaymentFailed(String paymentIntentId) {
        log.info("markPaymentFailed called for PaymentIntent {}", paymentIntentId);
        paymentRepository.findByStripePaymentId(paymentIntentId).ifPresentOrElse(payment -> {
            log.debug("Found DB payment id={} with current status {} for PaymentIntent {}", payment.getId(), payment.getStatus(), paymentIntentId);
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            log.info("Payment row id={} marked FAILED (PaymentIntent={})", payment.getId(), paymentIntentId);
        }, () -> log.warn("Received payment_intent.payment_failed for unknown PaymentIntent {}", paymentIntentId));
    }

    // Keeps our record in sync even if a refund was issued from the Stripe Dashboard directly
    // rather than through initiateRefund() below.
    @Transactional
    public void markChargeRefunded(Charge charge) {
        String paymentIntentId = charge.getPaymentIntent();
        if (paymentIntentId == null) {
            log.warn("markChargeRefunded called but Charge {} had no PaymentIntent reference", charge.getId());
            return;
        }

        log.info("markChargeRefunded called for Charge {} -> PaymentIntent {}", charge.getId(), paymentIntentId);
        paymentRepository.findByStripePaymentId(paymentIntentId).ifPresentOrElse(payment -> {
            log.debug("Found DB payment id={} with status {} for PaymentIntent {}", payment.getId(), payment.getStatus(), paymentIntentId);
            if (payment.getStatus() == PaymentStatus.REFUNDED) {
                log.info("Payment {} already marked REFUNDED; nothing to do", payment.getId());
                return;
            }
            payment.setStatus(PaymentStatus.REFUNDED);
            if (payment.getRefundId() == null) {
                latestRefundId(charge).ifPresent(refId -> {
                    payment.setRefundId(refId);
                    log.info("Recorded refund id {} on payment id={}", refId, payment.getId());
                });
            }
            paymentRepository.save(payment);
            log.info("Payment id={} marked REFUNDED (PaymentIntent={})", payment.getId(), paymentIntentId);
        }, () -> log.warn("Received charge.refunded for unknown PaymentIntent {} (charge {})", paymentIntentId, charge.getId()));
    }

    private Optional<String> latestRefundId(Charge charge) {
        if (charge.getRefunds() == null || charge.getRefunds().getData() == null) {
            return Optional.empty();
        }
        List<Refund> refunds = charge.getRefunds().getData();
        return refunds.isEmpty() ? Optional.empty() : Optional.of(refunds.get(refunds.size() - 1).getId());
    }

    // When SDK deserialization fails, extract data.object.id from the full Event JSON as a last-resort.
    private Optional<String> extractNestedObjectIdFromEvent(Event event) {
        try {
            String json = event.toJson();
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            com.google.gson.JsonElement root = com.google.gson.JsonParser.parseString(json);
            if (!root.isJsonObject()) return Optional.empty();
            com.google.gson.JsonObject rootObj = root.getAsJsonObject();
            if (!rootObj.has("data")) return Optional.empty();
            com.google.gson.JsonObject data = rootObj.getAsJsonObject("data");
            if (!data.has("object")) return Optional.empty();
            com.google.gson.JsonElement objEl = data.get("object");
            if (objEl.isJsonObject()) {
                com.google.gson.JsonObject obj = objEl.getAsJsonObject();
                if (obj.has("id") && !obj.get("id").isJsonNull()) {
                    return Optional.of(obj.get("id").getAsString());
                }
            }
        } catch (Exception e) {
            log.debug("extractNestedObjectIdFromEvent failed: {}", e.getMessage());
        }
        return Optional.empty();
    }

    // Called from BookingService.cancelBooking(). Deliberately a no-op (not an error) when the
    // booking was never actually paid for — most cancellations are of PENDING bookings that
    // never got past checkout.
    public static record RefundResult(BigDecimal amount, String currency, String refundId) {
    }

    @Transactional
    public Optional<RefundResult> initiateRefund(Long bookingId) {
        Optional<Payment> paymentOpt = paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(bookingId, PaymentStatus.SUCCEEDED);
        if (paymentOpt.isEmpty()) {
            return Optional.empty();
        }

        Payment payment = paymentOpt.get();
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return Optional.of(new RefundResult(payment.getAmount(), payment.getCurrency(), payment.getRefundId()));
        }

        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getStripePaymentId())
                    .build();
            Refund refund = Refund.create(params);

            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundId(refund.getId());
            paymentRepository.save(payment);
            return Optional.of(new RefundResult(payment.getAmount(), payment.getCurrency(), refund.getId()));
        } catch (StripeException e) {
            // Deliberately swallowed rather than rethrown: cancelBooking() has already persisted
            // the CANCELLED status by the time this runs, and a flaky Stripe call shouldn't roll
            // that back. The payment stays SUCCEEDED (accurate — no refund actually happened yet)
            // for manual follow-up.
            log.error("Stripe refund failed for booking {} (PaymentIntent {}): {}",
                    bookingId, payment.getStripePaymentId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    @Transactional
    public ResponseEntity<?> getPaymentStatus(Long bookingId, Long userId, Role userRole) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found with ID: " + bookingId));

        if ("CUSTOMER".equalsIgnoreCase(userRole.toString()) && !booking.getUser().getId().equals(userId)) {
            return ResponseEntity.badRequest().body("You are not authorized to view payments for this booking.");
        }

        Optional<Payment> paymentOpt = paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(bookingId, PaymentStatus.SUCCEEDED);
        if (paymentOpt.isEmpty()) {
            paymentOpt = paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(bookingId, PaymentStatus.PENDING);
        }
        if (paymentOpt.isEmpty()) {
            paymentOpt = paymentRepository.findFirstByBookingIdOrderByIdDesc(bookingId);
        }
        if (paymentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No payment found for booking " + bookingId);
        }
        Payment payment = reconcilePendingPayment(paymentOpt.get());

        return ResponseEntity.ok(PaymentStatusResponse.builder()
                .bookingId(bookingId)
                .paymentId(payment.getPaymentId())
                .stripePaymentId(payment.getStripePaymentId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paidAt(payment.getPaidAt())
                .refundId(payment.getRefundId())
                .build());
    }

    // Secure endpoint that uses paymentId instead of database IDs
    @Transactional
    public ResponseEntity<?> getPaymentStatusByPaymentId(String paymentId, Long userId, Role userRole) {
        Optional<Payment> paymentOpt = paymentRepository.findByPaymentId(paymentId);
        if (paymentOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No payment found with ID: " + paymentId);
        }

        Payment payment = paymentOpt.get();
        Booking booking = payment.getBooking();

        if ("CUSTOMER".equalsIgnoreCase(userRole.toString()) && !booking.getUser().getId().equals(userId)) {
            return ResponseEntity.badRequest().body("You are not authorized to view this payment.");
        }

        payment = reconcilePendingPayment(payment);

        return ResponseEntity.ok(PaymentStatusResponse.builder()
                .bookingId(booking.getId())
                .paymentId(payment.getPaymentId())
                .stripePaymentId(payment.getStripePaymentId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paidAt(payment.getPaidAt())
                .refundId(payment.getRefundId())
                .build());
    }

    private Payment reconcilePendingPayment(Payment payment) {
        if (payment.getStatus() != PaymentStatus.PENDING || payment.getStripePaymentId() == null) {
            return payment;
        }

        try {
            PaymentIntent intent = PaymentIntent.retrieve(payment.getStripePaymentId());
            if ("succeeded".equals(intent.getStatus())) {
                log.info("Status check recovered succeeded PaymentIntent {} after a missed webhook.", intent.getId());
                completeSuccessfulPayment(payment);
            } else if ("canceled".equals(intent.getStatus())) {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        } catch (StripeException e) {
            // Keep returning the local state so a temporary Stripe outage does not break polling.
            log.warn("Could not reconcile PaymentIntent {} during status check: {}",
                    payment.getStripePaymentId(), e.getMessage());
        }
        return payment;
    }

    private long toSmallestUnit(BigDecimal amount, String currency) {
        BigDecimal multiplier = ZERO_DECIMAL_CURRENCIES.contains(currency.toUpperCase(Locale.ROOT))
                ? BigDecimal.ONE
                : BigDecimal.valueOf(100);
        return amount.multiply(multiplier).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
