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
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
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
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found with ID: " + bookingId));

        if ("CUSTOMER".equalsIgnoreCase(userRole.toString()) && !booking.getUser().getId().equals(userId)) {
            return ResponseEntity.badRequest().body("You are not authorized to pay for this booking.");
        }

        boolean alreadyPaid = paymentRepository.findByBookingId(bookingId).stream()
                .anyMatch(p -> p.getStatus() == PaymentStatus.SUCCEEDED);
        if (alreadyPaid) {
            return ResponseEntity.badRequest().body("This booking has already been paid for.");
        }

        if (booking.getStatus() != BookingStatus.PENDING) {
            return ResponseEntity.badRequest().body("Only PENDING bookings can be paid for. Current status: " + booking.getStatus());
        }

        // Amount and currency always come from the persisted booking, never from the request —
        // accepting either from the client would let someone pay $1 for a $500 stay by editing
        // the request body before it hits this endpoint.
        BigDecimal amount = booking.getTotalPrice();
        String currency = booking.getRoom().getBranch().getCurrency();

        try {
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

            PaymentIntent intent = PaymentIntent.create(params);

            Payment payment = new Payment();
            payment.setBooking(booking);
            payment.setStripePaymentId(intent.getId());
            payment.setAmount(amount);
            payment.setCurrency(currency);
            payment.setStatus(PaymentStatus.PENDING);
            paymentRepository.save(payment);

            return ResponseEntity.status(HttpStatus.CREATED).body(PaymentIntentResponse.builder()
                    .bookingId(bookingId)
                    .paymentIntentId(intent.getId())
                    .clientSecret(intent.getClientSecret())
                    .amount(amount)
                    .currency(currency)
                    .build());
        } catch (StripeException e) {
            log.error("Stripe error creating PaymentIntent for booking {}: {}", bookingId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Could not start payment: " + e.getMessage());
        }
    }

    // Entry point for the webhook controller — routes each event type we care about to its own
    // handler and silently ignores anything else Stripe might send to this endpoint in future.
    public void handleWebhookEvent(Event event) {
        Optional<com.stripe.model.StripeObject> dataObject = event.getDataObjectDeserializer().getObject();

        switch (event.getType()) {
            case "payment_intent.succeeded" -> dataObject
                    .filter(PaymentIntent.class::isInstance)
                    .map(PaymentIntent.class::cast)
                    .ifPresentOrElse(intent -> confirmPayment(intent.getId()),
                            () -> log.warn("payment_intent.succeeded event {} had no deserializable PaymentIntent", event.getId()));
            case "payment_intent.payment_failed" -> dataObject
                    .filter(PaymentIntent.class::isInstance)
                    .map(PaymentIntent.class::cast)
                    .ifPresentOrElse(intent -> markPaymentFailed(intent.getId()),
                            () -> log.warn("payment_intent.payment_failed event {} had no deserializable PaymentIntent", event.getId()));
            case "charge.refunded" -> dataObject
                    .filter(Charge.class::isInstance)
                    .map(Charge.class::cast)
                    .ifPresent(this::markChargeRefunded);
            default -> log.debug("Ignoring unhandled Stripe event type: {}", event.getType());
        }
    }

    // Fires from the payment_intent.succeeded webhook, once Stripe confirms the card actually
    // charged successfully — this, not the client calling back, is the trustworthy signal.
    @Transactional
    public void confirmPayment(String paymentIntentId) {
        Optional<Payment> paymentOpt = paymentRepository.findByStripePaymentId(paymentIntentId);
        if (paymentOpt.isEmpty()) {
            log.warn("Received payment_intent.succeeded for unknown PaymentIntent {}", paymentIntentId);
            return;
        }

        Payment payment = paymentOpt.get();
        if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
            return; // Stripe can deliver the same webhook more than once — already handled.
        }

        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setPaidAt(LocalDateTime.now());
        paymentRepository.save(payment);

        bookingService.confirmBooking(payment.getBooking().getId());
    }

    @Transactional
    public void markPaymentFailed(String paymentIntentId) {
        paymentRepository.findByStripePaymentId(paymentIntentId).ifPresentOrElse(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
        }, () -> log.warn("Received payment_intent.payment_failed for unknown PaymentIntent {}", paymentIntentId));
    }

    // Keeps our record in sync even if a refund was issued from the Stripe Dashboard directly
    // rather than through initiateRefund() below.
    @Transactional
    public void markChargeRefunded(Charge charge) {
        String paymentIntentId = charge.getPaymentIntent();
        if (paymentIntentId == null) {
            return;
        }

        paymentRepository.findByStripePaymentId(paymentIntentId).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.REFUNDED) {
                return;
            }
            payment.setStatus(PaymentStatus.REFUNDED);
            if (payment.getRefundId() == null) {
                latestRefundId(charge).ifPresent(payment::setRefundId);
            }
            paymentRepository.save(payment);
        });
    }

    private Optional<String> latestRefundId(Charge charge) {
        if (charge.getRefunds() == null || charge.getRefunds().getData() == null) {
            return Optional.empty();
        }
        List<Refund> refunds = charge.getRefunds().getData();
        return refunds.isEmpty() ? Optional.empty() : Optional.of(refunds.get(refunds.size() - 1).getId());
    }

    // Called from BookingService.cancelBooking(). Deliberately a no-op (not an error) when the
    // booking was never actually paid for — most cancellations are of PENDING bookings that
    // never got past checkout.
    @Transactional
    public void initiateRefund(Long bookingId) {
        Optional<Payment> paymentOpt = paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(bookingId, PaymentStatus.SUCCEEDED);
        if (paymentOpt.isEmpty()) {
            return;
        }

        Payment payment = paymentOpt.get();
        try {
            RefundCreateParams params = RefundCreateParams.builder()
                    .setPaymentIntent(payment.getStripePaymentId())
                    .build();
            Refund refund = Refund.create(params);

            payment.setStatus(PaymentStatus.REFUNDED);
            payment.setRefundId(refund.getId());
            paymentRepository.save(payment);
        } catch (StripeException e) {
            // Deliberately swallowed rather than rethrown: cancelBooking() has already persisted
            // the CANCELLED status by the time this runs, and a flaky Stripe call shouldn't roll
            // that back. The payment stays SUCCEEDED (accurate — no refund actually happened yet)
            // for manual follow-up.
            log.error("Stripe refund failed for booking {} (PaymentIntent {}): {}",
                    bookingId, payment.getStripePaymentId(), e.getMessage(), e);
        }
    }

    public ResponseEntity<?> getPaymentStatus(Long bookingId, Long userId, Role userRole) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found with ID: " + bookingId));

        if ("CUSTOMER".equalsIgnoreCase(userRole.toString()) && !booking.getUser().getId().equals(userId)) {
            return ResponseEntity.badRequest().body("You are not authorized to view payments for this booking.");
        }

        Payment payment = paymentRepository.findFirstByBookingIdOrderByIdDesc(bookingId).orElse(null);
        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("No payment found for booking " + bookingId);
        }

        return ResponseEntity.ok(PaymentStatusResponse.builder()
                .bookingId(bookingId)
                .stripePaymentId(payment.getStripePaymentId())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .paidAt(payment.getPaidAt())
                .refundId(payment.getRefundId())
                .build());
    }

    private long toSmallestUnit(BigDecimal amount, String currency) {
        BigDecimal multiplier = ZERO_DECIMAL_CURRENCIES.contains(currency.toUpperCase(Locale.ROOT))
                ? BigDecimal.ONE
                : BigDecimal.valueOf(100);
        return amount.multiply(multiplier).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
