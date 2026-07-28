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
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic; // Lets a test stub a *static* method (PaymentIntent.create) rather than one on an injected mock
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
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
        Mockito.when(bookingRepository.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(paymentRepository.findByBookingId(50L)).thenReturn(new ArrayList<>());

        PaymentIntent fakeIntent = Mockito.mock(PaymentIntent.class);
        Mockito.when(fakeIntent.getId()).thenReturn("pi_123");
        Mockito.when(fakeIntent.getClientSecret()).thenReturn("pi_123_secret_abc");

        // Stripe's PaymentIntent.create is a static SDK call, not something injected — intercept
        // it at the class level for the duration of this try block only.
        try (MockedStatic<PaymentIntent> stripeStatic = Mockito.mockStatic(PaymentIntent.class)) {
            stripeStatic.when(() -> PaymentIntent.create(Mockito.any(PaymentIntentCreateParams.class)))
                    .thenReturn(fakeIntent);

            ResponseEntity<?> response = paymentService.createPaymentIntent(50L, 1L, Role.CUSTOMER);

            Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
            PaymentIntentResponse body = (PaymentIntentResponse) response.getBody();
            Assertions.assertEquals("pi_123_secret_abc", body.getClientSecret());
            Assertions.assertEquals(0, BigDecimal.valueOf(300).compareTo(body.getAmount()));
            Assertions.assertEquals("GBP", body.getCurrency());

            // 300.00 GBP (a normal 2-decimal currency) must reach Stripe as 30000 — its smallest unit.
            ArgumentCaptor<PaymentIntentCreateParams> paramsCaptor = ArgumentCaptor.forClass(PaymentIntentCreateParams.class);
            stripeStatic.verify(() -> PaymentIntent.create(paramsCaptor.capture()));
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
        Mockito.when(bookingRepository.findById(50L)).thenReturn(Optional.of(booking));

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
        Mockito.when(bookingRepository.findById(50L)).thenReturn(Optional.of(booking));

        ResponseEntity<?> response = paymentService.createPaymentIntent(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Mockito.verify(paymentRepository, Mockito.never()).save(Mockito.any(Payment.class));
    }

    @Test
    void createPaymentIntent_WhenAlreadyPaid_ReturnsBadRequest() {
        Payment succeeded = new Payment();
        succeeded.setStatus(PaymentStatus.SUCCEEDED);
        Mockito.when(bookingRepository.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(paymentRepository.findByBookingId(50L)).thenReturn(List.of(succeeded));

        ResponseEntity<?> response = paymentService.createPaymentIntent(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
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

            // Must not propagate — cancelBooking() has already committed the CANCELLED status by
            // the time this runs, and a flaky Stripe call shouldn't roll that back.
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
    void getPaymentStatus_WhenNoPaymentExists_ReturnsNotFound() {
        Mockito.when(bookingRepository.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(paymentRepository.findFirstByBookingIdOrderByIdDesc(50L)).thenReturn(Optional.empty());

        ResponseEntity<?> response = paymentService.getPaymentStatus(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
