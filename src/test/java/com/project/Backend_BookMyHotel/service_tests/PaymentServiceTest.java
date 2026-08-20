package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Payment;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import com.project.Backend_BookMyHotel.dto.PaymentIntentResponse;
import com.project.Backend_BookMyHotel.dto.PaymentStatus;
import com.project.Backend_BookMyHotel.dto.PaymentStatusResponse;
import com.project.Backend_BookMyHotel.dto.Role;
import com.project.Backend_BookMyHotel.repository.BookingRepository;
import com.project.Backend_BookMyHotel.repository.PaymentRepository;
import com.project.Backend_BookMyHotel.service.BookingService;
import com.project.Backend_BookMyHotel.service.PaymentService;
import com.stripe.exception.ApiConnectionException;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingService bookingService;

    @InjectMocks
    private PaymentService paymentService;

    private User customer;
    private Booking booking;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setId(1L);
        customer.setEmail("guest@example.com");
        customer.setRole(Role.CUSTOMER);

        Branch branch = new Branch();
        branch.setId(10L);
        branch.setCurrency("GBP");

        Room room = new Room();
        room.setId(100L);
        room.setBranch(branch);

        booking = Booking.builder()
                .id(50L)
                .user(customer)
                .room(room)
                .reference("BMH-TEST-REF")
                .status(BookingStatus.PENDING)
                .totalPrice(BigDecimal.valueOf(300))
                .build();
    }

    @Test
    void createPaymentIntent_Success_CreatesStripeIntentAndPersistsPendingPayment() {
        Mockito.when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(booking));
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.SUCCEEDED)).thenReturn(Optional.empty());
        PaymentIntent fakeIntent = Mockito.mock(PaymentIntent.class);
        Mockito.when(fakeIntent.getId()).thenReturn("pi_123");
        Mockito.when(fakeIntent.getClientSecret()).thenReturn("pi_123_secret_abc");

        try (MockedStatic<PaymentIntent> stripeStatic = Mockito.mockStatic(PaymentIntent.class)) {
            stripeStatic.when(() -> PaymentIntent.create(Mockito.any(PaymentIntentCreateParams.class), Mockito.any(RequestOptions.class)))
                    .thenReturn(fakeIntent);

            ResponseEntity<?> response = paymentService.createPaymentIntent(50L, 1L, Role.CUSTOMER);

            Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
            PaymentIntentResponse body = (PaymentIntentResponse) response.getBody();
            Assertions.assertEquals("pi_123_secret_abc", body.getClientSecret());
            Assertions.assertEquals(0, BigDecimal.valueOf(300).compareTo(body.getAmount()));
            Assertions.assertEquals("GBP", body.getCurrency());

            ArgumentCaptor<PaymentIntentCreateParams> paramsCaptor = ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
            stripeStatic.verify(() -> PaymentIntent.create(paramsCaptor.capture(), Mockito.any(RequestOptions.class)));
            Assertions.assertEquals(30000L, paramsCaptor.getValue().getAmount());
            Assertions.assertEquals("gbp", paramsCaptor.getValue().getCurrency());
        }

        ArgumentCaptor<Payment> savedPaymentCaptor = ArgumentCaptor.forClass(Payment.class);
        Mockito.verify(paymentRepository).save(savedPaymentCaptor.capture());
        Assertions.assertEquals("pi_123", savedPaymentCaptor.getValue().getStripePaymentId());
        Assertions.assertEquals(PaymentStatus.PENDING, savedPaymentCaptor.getValue().getStatus());
    }

    @Test
    void createPaymentIntent_WhenNotOwner_ReturnsBadRequestWithoutCallingStripe() {
        Mockito.when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(booking));

        try (MockedStatic<PaymentIntent> stripeStatic = Mockito.mockStatic(PaymentIntent.class)) {
            ResponseEntity<?> response = paymentService.createPaymentIntent(50L, 999L, Role.CUSTOMER);

            Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            stripeStatic.verifyNoInteractions();
        }
        Mockito.verify(paymentRepository, Mockito.never()).save(Mockito.any(Payment.class));
    }

    @Test
    void createPaymentIntent_WhenBookingNotPending_ReturnsBadRequest() {
        booking.setStatus(BookingStatus.CONFIRMED);
        Mockito.when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(booking));

        ResponseEntity<?> response = paymentService.createPaymentIntent(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Mockito.verify(paymentRepository, Mockito.never()).save(Mockito.any(Payment.class));
    }

    @Test
    void createPaymentIntent_WhenAlreadyPaid_ReturnsSucceededResponseWithoutRemountingStripe() {
        Payment succeeded = new Payment();
        succeeded.setBooking(booking);
        succeeded.setStripePaymentId("pi_paid");
        succeeded.setStatus(PaymentStatus.SUCCEEDED);
        Mockito.when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(booking));
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.SUCCEEDED))
                .thenReturn(Optional.of(succeeded));

        ResponseEntity<?> response = paymentService.createPaymentIntent(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        PaymentIntentResponse body = (PaymentIntentResponse) response.getBody();
        Assertions.assertEquals(PaymentStatus.SUCCEEDED, body.getStatus());
        Assertions.assertNull(body.getClientSecret());
    }

    @Test
    void createPaymentIntent_WhenStripeAlreadySucceeded_RecoversMissedWebhook() {
        Payment pending = new Payment();
        pending.setId(500L);
        pending.setBooking(booking);
        pending.setStripePaymentId("pi_completed");
        pending.setStatus(PaymentStatus.PENDING);

        Mockito.when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(booking));
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.SUCCEEDED))
                .thenReturn(Optional.empty());
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.PENDING))
                .thenReturn(Optional.of(pending));

        PaymentIntent completedIntent = Mockito.mock(PaymentIntent.class);
        Mockito.when(completedIntent.getId()).thenReturn("pi_completed");
        Mockito.when(completedIntent.getStatus()).thenReturn("succeeded");

        try (MockedStatic<PaymentIntent> stripeStatic = Mockito.mockStatic(PaymentIntent.class)) {
            stripeStatic.when(() -> PaymentIntent.retrieve("pi_completed")).thenReturn(completedIntent);

            ResponseEntity<?> response = paymentService.createPaymentIntent(50L, 1L, Role.CUSTOMER);

            Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
            PaymentIntentResponse body = (PaymentIntentResponse) response.getBody();
            Assertions.assertEquals(PaymentStatus.SUCCEEDED, body.getStatus());
            Assertions.assertEquals("succeeded", body.getStripeStatus());
            Assertions.assertNull(body.getClientSecret());
            stripeStatic.verify(
                    () -> PaymentIntent.create(Mockito.any(PaymentIntentCreateParams.class), Mockito.any(RequestOptions.class)),
                    Mockito.never());
        }

        Assertions.assertEquals(PaymentStatus.SUCCEEDED, pending.getStatus());
        Mockito.verify(paymentRepository).save(pending);
        Mockito.verify(bookingService).confirmBooking(50L);
    }

    @Test
    void createPaymentIntent_WhenOldIntentCanceled_UsesNewRetryIdempotencyKey() {
        Payment pending = new Payment();
        pending.setId(500L);
        pending.setBooking(booking);
        pending.setStripePaymentId("pi_canceled");
        pending.setStatus(PaymentStatus.PENDING);

        Mockito.when(bookingRepository.findByIdForUpdate(50L)).thenReturn(Optional.of(booking));
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.SUCCEEDED))
                .thenReturn(Optional.empty());
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.PENDING))
                .thenReturn(Optional.of(pending));

        PaymentIntent canceledIntent = Mockito.mock(PaymentIntent.class);
        Mockito.when(canceledIntent.getId()).thenReturn("pi_canceled");
        Mockito.when(canceledIntent.getStatus()).thenReturn("canceled");

        PaymentIntent replacementIntent = Mockito.mock(PaymentIntent.class);
        Mockito.when(replacementIntent.getId()).thenReturn("pi_replacement");
        Mockito.when(replacementIntent.getStatus()).thenReturn("requires_payment_method");
        Mockito.when(replacementIntent.getClientSecret()).thenReturn("pi_replacement_secret");

        try (MockedStatic<PaymentIntent> stripeStatic = Mockito.mockStatic(PaymentIntent.class)) {
            stripeStatic.when(() -> PaymentIntent.retrieve("pi_canceled")).thenReturn(canceledIntent);
            stripeStatic.when(() -> PaymentIntent.create(
                            Mockito.any(PaymentIntentCreateParams.class), Mockito.any(RequestOptions.class)))
                    .thenReturn(replacementIntent);

            ResponseEntity<?> response = paymentService.createPaymentIntent(50L, 1L, Role.CUSTOMER);

            Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
            PaymentIntentResponse body = (PaymentIntentResponse) response.getBody();
            Assertions.assertEquals("pi_replacement_secret", body.getClientSecret());

            ArgumentCaptor<RequestOptions> optionsCaptor = ArgumentCaptor.forClass(RequestOptions.class);
            stripeStatic.verify(() -> PaymentIntent.create(
                    Mockito.any(PaymentIntentCreateParams.class), optionsCaptor.capture()));
            Assertions.assertEquals("booking-intent-retry-50-pi_canceled",
                    optionsCaptor.getValue().getIdempotencyKey());
        }

        Assertions.assertEquals("pi_replacement", pending.getStripePaymentId());
        Mockito.verify(paymentRepository).save(pending);
    }

    @Test
    void confirmPayment_WhenPending_MarksSucceededAndConfirmsBooking() {
        Payment payment = new Payment();
        payment.setId(500L);
        payment.setBooking(booking);
        payment.setStripePaymentId("pi_123");
        payment.setStatus(PaymentStatus.PENDING);

        Mockito.when(paymentRepository.findByStripePaymentId("pi_123")).thenReturn(Optional.of(payment));

        paymentService.confirmPayment("pi_123");

        Assertions.assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        Assertions.assertNotNull(payment.getPaidAt());
        Mockito.verify(paymentRepository).save(payment);
        Mockito.verify(bookingService).confirmBooking(50L);
    }

    @Test
    void confirmPayment_WhenAlreadySucceeded_IsIdempotentAndDoesNotReconfirmBooking() {
        Payment payment = new Payment();
        payment.setBooking(booking);
        payment.setStripePaymentId("pi_123");
        payment.setStatus(PaymentStatus.SUCCEEDED);

        Mockito.when(paymentRepository.findByStripePaymentId("pi_123")).thenReturn(Optional.of(payment));

        paymentService.confirmPayment("pi_123");

        Mockito.verify(paymentRepository, Mockito.never()).save(Mockito.any(Payment.class));
        Mockito.verify(bookingService, Mockito.never()).confirmBooking(Mockito.anyLong());
    }

    @Test
    void confirmPayment_WhenPaymentIntentUnknown_DoesNothing() {
        Mockito.when(paymentRepository.findByStripePaymentId("pi_unknown")).thenReturn(Optional.empty());

        paymentService.confirmPayment("pi_unknown");

        Mockito.verify(bookingService, Mockito.never()).confirmBooking(Mockito.anyLong());
    }

    @Test
    void markPaymentFailed_UpdatesStatusToFailed() {
        Payment payment = new Payment();
        payment.setStripePaymentId("pi_123");
        payment.setStatus(PaymentStatus.PENDING);
        Mockito.when(paymentRepository.findByStripePaymentId("pi_123")).thenReturn(Optional.of(payment));

        paymentService.markPaymentFailed("pi_123");

        Assertions.assertEquals(PaymentStatus.FAILED, payment.getStatus());
        Mockito.verify(paymentRepository).save(payment);
    }

    @Test
    void initiateRefund_WhenSuccessfulPaymentExists_CallsStripeAndMarksRefunded() {
        Payment payment = new Payment();
        payment.setStripePaymentId("pi_123");
        payment.setStatus(PaymentStatus.SUCCEEDED);
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.SUCCEEDED))
                .thenReturn(Optional.of(payment));

        Refund fakeRefund = Mockito.mock(Refund.class);
        Mockito.when(fakeRefund.getId()).thenReturn("re_456");

        try (MockedStatic<Refund> stripeStatic = Mockito.mockStatic(Refund.class)) {
            stripeStatic.when(() -> Refund.create(Mockito.any(RefundCreateParams.class))).thenReturn(fakeRefund);

            paymentService.initiateRefund(50L);

            stripeStatic.verify(() -> Refund.create(Mockito.any(RefundCreateParams.class)));
        }

        Assertions.assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        Assertions.assertEquals("re_456", payment.getRefundId());
        Mockito.verify(paymentRepository).save(payment);
    }

    @Test
    void initiateRefund_WhenNoSuccessfulPayment_DoesNothing() {
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.SUCCEEDED))
                .thenReturn(Optional.empty());

        try (MockedStatic<Refund> stripeStatic = Mockito.mockStatic(Refund.class)) {
            paymentService.initiateRefund(50L);
            stripeStatic.verifyNoInteractions();
        }
        Mockito.verify(paymentRepository, Mockito.never()).save(Mockito.any(Payment.class));
    }

    @Test
    void initiateRefund_WhenStripeCallFails_LeavesPaymentSucceededInsteadOfThrowing() {
        Payment payment = new Payment();
        payment.setStripePaymentId("pi_123");
        payment.setStatus(PaymentStatus.SUCCEEDED);
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.SUCCEEDED))
                .thenReturn(Optional.of(payment));

        try (MockedStatic<Refund> stripeStatic = Mockito.mockStatic(Refund.class)) {
            stripeStatic.when(() -> Refund.create(Mockito.any(RefundCreateParams.class)))
                    .thenThrow(new ApiConnectionException("network down"));

            Assertions.assertDoesNotThrow(() -> paymentService.initiateRefund(50L));
        }

        Assertions.assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        Mockito.verify(paymentRepository, Mockito.never()).save(Mockito.any(Payment.class));
    }

    @Test
    void getPaymentStatus_ReturnsLatestPaymentForBooking() {
        Payment payment = new Payment();
        payment.setStripePaymentId("pi_123");
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.setAmount(BigDecimal.valueOf(300));
        payment.setCurrency("GBP");

        Mockito.when(bookingRepository.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(paymentRepository.findFirstByBookingIdOrderByIdDesc(50L)).thenReturn(Optional.of(payment));

        ResponseEntity<?> response = paymentService.getPaymentStatus(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        PaymentStatusResponse body = (PaymentStatusResponse) response.getBody();
        Assertions.assertEquals(PaymentStatus.SUCCEEDED, body.getStatus());
        Assertions.assertEquals("pi_123", body.getStripePaymentId());
    }

    @Test
    void getPaymentStatus_ReturnsLatestSucceededEvenIfPendingAttemptExists() {
        Payment successfulPayment = new Payment();
        successfulPayment.setStripePaymentId("pi_success");
        successfulPayment.setStatus(PaymentStatus.SUCCEEDED);
        successfulPayment.setAmount(BigDecimal.valueOf(300));
        successfulPayment.setCurrency("GBP");

        Mockito.when(bookingRepository.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.SUCCEEDED))
                .thenReturn(Optional.of(successfulPayment));

        ResponseEntity<?> response = paymentService.getPaymentStatus(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        PaymentStatusResponse body = (PaymentStatusResponse) response.getBody();
        Assertions.assertEquals(PaymentStatus.SUCCEEDED, body.getStatus());
        Assertions.assertEquals("pi_success", body.getStripePaymentId());

        Mockito.verify(paymentRepository, Mockito.never())
                .findFirstByBookingIdOrderByIdDesc(50L);
    }

    @Test
    void getPaymentStatus_WhenNoPaymentExists_ReturnsNotFound() {
        Mockito.when(bookingRepository.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.SUCCEEDED))
                .thenReturn(Optional.empty());
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.PENDING))
                .thenReturn(Optional.empty());
        Mockito.when(paymentRepository.findFirstByBookingIdOrderByIdDesc(50L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = paymentService.getPaymentStatus(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getPaymentStatus_WhenWebhookWasMissed_ReconcilesWithStripe() {
        Payment pending = new Payment();
        pending.setId(500L);
        pending.setBooking(booking);
        pending.setStripePaymentId("pi_completed");
        pending.setStatus(PaymentStatus.PENDING);
        pending.setAmount(BigDecimal.valueOf(300));
        pending.setCurrency("GBP");

        Mockito.when(bookingRepository.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.SUCCEEDED))
                .thenReturn(Optional.empty());
        Mockito.when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(50L, PaymentStatus.PENDING))
                .thenReturn(Optional.of(pending));

        PaymentIntent completedIntent = Mockito.mock(PaymentIntent.class);
        Mockito.when(completedIntent.getId()).thenReturn("pi_completed");
        Mockito.when(completedIntent.getStatus()).thenReturn("succeeded");

        try (MockedStatic<PaymentIntent> stripeStatic = Mockito.mockStatic(PaymentIntent.class)) {
            stripeStatic.when(() -> PaymentIntent.retrieve("pi_completed")).thenReturn(completedIntent);

            ResponseEntity<?> response = paymentService.getPaymentStatus(50L, 1L, Role.CUSTOMER);

            PaymentStatusResponse body = (PaymentStatusResponse) response.getBody();
            Assertions.assertEquals(PaymentStatus.SUCCEEDED, body.getStatus());
        }

        Mockito.verify(paymentRepository).save(pending);
        Mockito.verify(bookingService).confirmBooking(50L);
    }
}
