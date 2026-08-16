package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.BookingAddonService;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.Promotion;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.AddServicesRequest;
import com.project.Backend_BookMyHotel.dto.BookingResponse;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import com.project.Backend_BookMyHotel.dto.CreateBookingRequest;
import com.project.Backend_BookMyHotel.dto.DiscountType;
import com.project.Backend_BookMyHotel.dto.PromotionBreakdownResponse;
import com.project.Backend_BookMyHotel.dto.Role;
import com.project.Backend_BookMyHotel.dto.RoomPriceResponse;
import com.project.Backend_BookMyHotel.dto.RoomTag;
import com.project.Backend_BookMyHotel.repository.BookingRepository;
import com.project.Backend_BookMyHotel.repository.PromotionRepository;
import com.project.Backend_BookMyHotel.repository.RoomRepository;
import com.project.Backend_BookMyHotel.repository.ServiceRepository;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.BookingService;
import com.project.Backend_BookMyHotel.service.EmailTemplateService;
import com.project.Backend_BookMyHotel.service.ExchangeRateService;
import com.project.Backend_BookMyHotel.service.PaymentService;
import com.project.Backend_BookMyHotel.service.PromotionService;
import com.project.Backend_BookMyHotel.service.ResendEmailService;
import com.project.Backend_BookMyHotel.service.RoomAvailabilityService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor; // Mockito helper that intercepts and records the actual arguments a mock was called with, so we can assert on them after the fact
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
public class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepo;

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoomAvailabilityService availabilityService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private EmailTemplateService notificationService;

    @Mock
    private ResendEmailService resendEmailService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private PromotionService promotionService;

    @Mock
    private PromotionRepository promotionRepository;

    @InjectMocks
    private BookingService bookingService;

    private User customer;
    private Branch branch;
    private Hotel hotel;
    private Room room;
    private Booking booking;
    private com.project.Backend_BookMyHotel.domain.Service service;

    @BeforeEach
    void setUp() {
        customer = new User();
        customer.setId(1L);
        customer.setEmail("guest@example.com");
        customer.setFirstName("Jane");
        customer.setLastName("Doe");
        customer.setRole(Role.CUSTOMER);

        hotel = new Hotel();
        hotel.setId(1000L);
        hotel.setName("Grand Hotel");

        branch = new Branch();
        branch.setId(10L);
        branch.setName("Central Branch");
        branch.setCurrency("GBP");
        branch.setHotel(hotel);

        room = new Room();
        room.setId(100L);
        room.setBranch(branch);
        room.setRoomType("Deluxe");

        booking = Booking.builder()
                .id(50L)
                .user(customer)
                .room(room)
                .reference("BMH-TEST-REF")
                .checkIn(LocalDate.now().plusDays(5))
                .checkOut(LocalDate.now().plusDays(7))
                .status(BookingStatus.PENDING)
                .totalPrice(BigDecimal.valueOf(200))
                .bookingServices(new ArrayList<>())
                .build();

        service = new com.project.Backend_BookMyHotel.domain.Service();
        service.setId(200L);
        service.setHotel(hotel);
        service.setBranch(branch);
        service.setName("Spa");
        service.setPrice(BigDecimal.valueOf(50));

        // @Value-injected fields aren't populated by Mockito's @InjectMocks, so this has to be
        // @Value fields are not populated by @InjectMocks; this matches the configured 5 points/night.
        ReflectionTestUtils.setField(bookingService, "ecoPointsPerNight", 5);
        Mockito.lenient().when(exchangeRateService.convert(
                        Mockito.any(BigDecimal.class), Mockito.eq("USD"), Mockito.eq("GBP")))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void addServicesToBooking_Success_UpdatesTotalAndReturnsBookingResponse() {
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(serviceRepository.findById(200L)).thenReturn(Optional.of(service));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        AddServicesRequest request = new AddServicesRequest(
                List.of(new AddServicesRequest.ServiceItem(200L, 2)));

        ResponseEntity<?> response = bookingService.addServicesToBooking(50L, 1L, Role.CUSTOMER, request);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertTrue(response.getBody() instanceof BookingResponse);
        BookingResponse body = (BookingResponse) response.getBody();
        Assertions.assertEquals(BigDecimal.valueOf(300), body.getTotalPrice());
        Assertions.assertEquals(1, body.getServices().size());
        Mockito.verify(bookingRepo, Mockito.times(1)).save(Mockito.any(Booking.class));
    }

    @Test
    void addServicesToBooking_MultipleServices_TotalPriceReflectsSumOfSubtotals() {
        com.project.Backend_BookMyHotel.domain.Service airportPickup = new com.project.Backend_BookMyHotel.domain.Service();
        airportPickup.setId(201L);
        airportPickup.setBranch(branch);
        airportPickup.setName("Airport Pickup");
        airportPickup.setPrice(BigDecimal.valueOf(25));

        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(serviceRepository.findById(200L)).thenReturn(Optional.of(service)); // Spa, price 50
        Mockito.when(serviceRepository.findById(201L)).thenReturn(Optional.of(airportPickup)); // price 25
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        // 1x Spa (50 * 1 = 50) + 3x Airport Pickup (25 * 3 = 75) => 125 added on top of the 200 room total
        AddServicesRequest request = new AddServicesRequest(List.of(
                new AddServicesRequest.ServiceItem(200L, 1),
                new AddServicesRequest.ServiceItem(201L, 3)));

        ResponseEntity<?> response = bookingService.addServicesToBooking(50L, 1L, Role.CUSTOMER, request);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        BookingResponse body = (BookingResponse) response.getBody();
        Assertions.assertEquals(0, BigDecimal.valueOf(325).compareTo(body.getTotalPrice()));
        Assertions.assertEquals(2, body.getServices().size());

        BigDecimal subtotalSum = body.getServices().stream()
                .map(BookingResponse.AddonServiceResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        Assertions.assertEquals(0, BigDecimal.valueOf(125).compareTo(subtotalSum));
    }

    @Test
    void addServicesToBooking_WhenNotOwner_ReturnsBadRequest() {
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));

        AddServicesRequest request = new AddServicesRequest(
                List.of(new AddServicesRequest.ServiceItem(200L, 1)));

        ResponseEntity<?> response = bookingService.addServicesToBooking(50L, 999L, Role.CUSTOMER, request);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Mockito.verify(bookingRepo, Mockito.never()).save(Mockito.any(Booking.class));
    }

    @Test
    void addServicesToBooking_WhenServiceBelongsToDifferentBranch_ReturnsBadRequest() {
        Branch otherBranch = new Branch();
        otherBranch.setId(999L);
        service.setBranch(otherBranch);

        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(serviceRepository.findById(200L)).thenReturn(Optional.of(service));

        AddServicesRequest request = new AddServicesRequest(
                List.of(new AddServicesRequest.ServiceItem(200L, 1)));

        ResponseEntity<?> response = bookingService.addServicesToBooking(50L, 1L, Role.CUSTOMER, request);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Mockito.verify(bookingRepo, Mockito.never()).save(Mockito.any(Booking.class));
    }

    @Test
    void addServicesToBooking_WhenCheckInHasPassed_ReturnsBadRequest() {
        booking.setCheckIn(LocalDate.now().minusDays(1));
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));

        AddServicesRequest request = new AddServicesRequest(
                List.of(new AddServicesRequest.ServiceItem(200L, 1)));

        ResponseEntity<?> response = bookingService.addServicesToBooking(50L, 1L, Role.CUSTOMER, request);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Mockito.verify(bookingRepo, Mockito.never()).save(Mockito.any(Booking.class));
    }

    @Test
    void removeServiceFromBooking_Success_SubtractsSubtotalFromTotal() {
        BookingAddonService addon = new BookingAddonService();
        addon.setId(1L);
        addon.setBooking(booking);
        addon.setService(service);
        addon.setQuantity(2);
        addon.setSubtotal(BigDecimal.valueOf(100));
        booking.setBookingServices(new ArrayList<>(List.of(addon)));

        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = bookingService.removeServiceFromBooking(50L, 200L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        BookingResponse body = (BookingResponse) response.getBody();
        Assertions.assertEquals(BigDecimal.valueOf(100), body.getTotalPrice());
        Assertions.assertTrue(body.getServices().isEmpty());
    }

    @Test
    void removeServiceFromBooking_WhenServiceNotAttached_ReturnsNotFound() {
        booking.setBookingServices(new ArrayList<>());
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));

        ResponseEntity<?> response = bookingService.removeServiceFromBooking(50L, 200L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Mockito.verify(bookingRepo, Mockito.never()).save(Mockito.any(Booking.class));
    }

    @Test
    void createBooking_WithValidPromoCode_PersistsPromotionOnBooking() {
        LocalDate checkIn = LocalDate.now().plusDays(20);
        LocalDate checkOut = LocalDate.now().plusDays(22);

        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getName()).thenReturn(customer.getEmail());
        Mockito.when(userRepository.findByEmail(customer.getEmail())).thenReturn(customer);
        Mockito.when(roomRepository.findById(100L)).thenReturn(Optional.of(room));
        Mockito.when(bookingRepo.findOverlappingBookings(Mockito.eq(100L), Mockito.any(BookingStatus.class), Mockito.eq(checkIn), Mockito.eq(checkOut)))
                .thenReturn(new ArrayList<>());
        Mockito.when(availabilityService.calculateTotalPrice(100L, checkIn, checkOut)).thenReturn(
                RoomPriceResponse.builder()
                        .roomId(100L).checkIn(checkIn).checkOut(checkOut).totalNights(2)
                        .totalPrice(BigDecimal.valueOf(300)).currency("GBP").isAvailable(true).breakdown(List.of())
                        .build());

        Promotion promotion = Promotion.builder().id(900L).code("SUMMER10").discountType(DiscountType.PERCENTAGE).discountValue(BigDecimal.TEN).build();
        // applyPromotion only ever returns a non-null promoCode after it has already validated the
        // code itself, so the test only needs to stub the breakdown response, not the validation rules.
        Mockito.when(promotionService.applyPromotion("SUMMER10", BigDecimal.valueOf(300), hotel.getId())).thenReturn(
                PromotionBreakdownResponse.builder()
                        .promoCode("SUMMER10").discountType(DiscountType.PERCENTAGE).discountValue(BigDecimal.TEN)
                        .originalPrice(BigDecimal.valueOf(300)).discountAmount(BigDecimal.valueOf(30)).finalPrice(BigDecimal.valueOf(270))
                        .message("Promo code successfully applied!")
                        .build());
        Mockito.when(promotionRepository.findByCodeIgnoreCase("SUMMER10")).thenReturn(Optional.of(promotion));

        ArgumentCaptor<Booking> savedBookingCaptor = ArgumentCaptor.forClass(Booking.class);
        Mockito.when(bookingRepo.save(savedBookingCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        CreateBookingRequest request = new CreateBookingRequest(100L, checkIn, checkOut, "SUMMER10");
        ResponseEntity<?> response = bookingService.createBooking(authentication, request);

        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Assertions.assertSame(promotion, savedBookingCaptor.getValue().getPromotion());
        BookingResponse body = (BookingResponse) response.getBody();
        Assertions.assertEquals("SUMMER10", body.getPromoCode());
        Assertions.assertEquals(0, BigDecimal.valueOf(270).compareTo(body.getTotalPrice()));
    }

    @Test
    void createBooking_WithHotelWideService_PersistsSnapshotAndIncludesItInTotal() {
        LocalDate checkIn = LocalDate.now().plusDays(25);
        LocalDate checkOut = LocalDate.now().plusDays(27);
        service.setBranch(null);
        service.setActive(true);

        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getName()).thenReturn(customer.getEmail());
        Mockito.when(userRepository.findByEmail(customer.getEmail())).thenReturn(customer);
        Mockito.when(roomRepository.findById(100L)).thenReturn(Optional.of(room));
        Mockito.when(bookingRepo.findOverlappingBookings(
                        Mockito.eq(100L), Mockito.any(BookingStatus.class), Mockito.eq(checkIn), Mockito.eq(checkOut)))
                .thenReturn(new ArrayList<>());
        Mockito.when(availabilityService.calculateTotalPrice(100L, checkIn, checkOut)).thenReturn(
                RoomPriceResponse.builder()
                        .roomId(100L).checkIn(checkIn).checkOut(checkOut).totalNights(2)
                        .totalPrice(BigDecimal.valueOf(300)).currency("GBP").isAvailable(true).breakdown(List.of())
                        .build());
        Mockito.when(serviceRepository.findById(200L)).thenReturn(Optional.of(service));

        ArgumentCaptor<Booking> savedBookingCaptor = ArgumentCaptor.forClass(Booking.class);
        Mockito.when(bookingRepo.save(savedBookingCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

        CreateBookingRequest request = new CreateBookingRequest(
                100L,
                checkIn,
                checkOut,
                null,
                List.of(new AddServicesRequest.ServiceItem(200L, 2))
        );

        ResponseEntity<?> response = bookingService.createBooking(authentication, request);

        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Booking saved = savedBookingCaptor.getValue();
        Assertions.assertEquals(0, BigDecimal.valueOf(400).compareTo(saved.getTotalPrice()));
        Assertions.assertEquals(1, saved.getBookingServices().size());
        BookingAddonService addon = saved.getBookingServices().get(0);
        Assertions.assertEquals("Spa", addon.getServiceName());
        Assertions.assertEquals(0, BigDecimal.valueOf(50).compareTo(addon.getUnitPrice()));
        Assertions.assertEquals(0, BigDecimal.valueOf(100).compareTo(addon.getSubtotal()));

        BookingResponse body = (BookingResponse) response.getBody();
        Assertions.assertEquals(0, BigDecimal.valueOf(100).compareTo(body.getPriceBreakdown().getServicesTotal()));
        Assertions.assertEquals(1, body.getServices().size());
    }

    @Test
    void createBooking_WithEcoPoints_DeductsBalanceAndDiscountsRoom() {
        LocalDate checkIn = LocalDate.now().plusDays(30);
        LocalDate checkOut = checkIn.plusDays(2);
        customer.setEcoPoints(100);

        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getName()).thenReturn(customer.getEmail());
        Mockito.when(userRepository.findByEmail(customer.getEmail())).thenReturn(customer);
        Mockito.when(userRepository.findByEmailForUpdate(customer.getEmail())).thenReturn(Optional.of(customer));
        Mockito.when(roomRepository.findById(100L)).thenReturn(Optional.of(room));
        Mockito.when(bookingRepo.findOverlappingBookings(
                        Mockito.eq(100L), Mockito.any(BookingStatus.class), Mockito.eq(checkIn), Mockito.eq(checkOut)))
                .thenReturn(new ArrayList<>());
        Mockito.when(availabilityService.calculateTotalPrice(100L, checkIn, checkOut)).thenReturn(
                RoomPriceResponse.builder()
                        .roomId(100L).checkIn(checkIn).checkOut(checkOut).totalNights(2)
                        .totalPrice(BigDecimal.valueOf(300)).currency("GBP").isAvailable(true).breakdown(List.of())
                        .build());
        Mockito.when(exchangeRateService.convert(BigDecimal.ONE, "USD", "GBP"))
                .thenReturn(BigDecimal.valueOf(0.8));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateBookingRequest request = new CreateBookingRequest(
                100L, checkIn, checkOut, null, List.of(), 50);
        ResponseEntity<?> response = bookingService.createBooking(authentication, request);

        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        BookingResponse body = (BookingResponse) response.getBody();
        Assertions.assertEquals(0, BigDecimal.valueOf(296).compareTo(body.getTotalPrice()));
        Assertions.assertEquals(50, body.getEcoPointsRedeemed());
        Assertions.assertEquals(0, BigDecimal.valueOf(4).compareTo(body.getEcoPointsDiscount()));
        Assertions.assertEquals(50, customer.getEcoPoints());
        Mockito.verify(userRepository).save(customer);
    }

    @Test
    void createBooking_WithMoreEcoPointsThanBalance_IsRejected() {
        LocalDate checkIn = LocalDate.now().plusDays(30);
        LocalDate checkOut = checkIn.plusDays(2);
        customer.setEcoPoints(20);

        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getName()).thenReturn(customer.getEmail());
        Mockito.when(userRepository.findByEmail(customer.getEmail())).thenReturn(customer);
        Mockito.when(userRepository.findByEmailForUpdate(customer.getEmail())).thenReturn(Optional.of(customer));
        Mockito.when(roomRepository.findById(100L)).thenReturn(Optional.of(room));
        Mockito.when(bookingRepo.findOverlappingBookings(
                        Mockito.eq(100L), Mockito.any(BookingStatus.class), Mockito.eq(checkIn), Mockito.eq(checkOut)))
                .thenReturn(new ArrayList<>());
        Mockito.when(availabilityService.calculateTotalPrice(100L, checkIn, checkOut)).thenReturn(
                RoomPriceResponse.builder()
                        .roomId(100L).checkIn(checkIn).checkOut(checkOut).totalNights(2)
                        .totalPrice(BigDecimal.valueOf(300)).currency("GBP").isAvailable(true).breakdown(List.of())
                        .build());

        CreateBookingRequest request = new CreateBookingRequest(
                100L, checkIn, checkOut, null, List.of(), 50);
        ResponseEntity<?> response = bookingService.createBooking(authentication, request);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals(20, customer.getEcoPoints());
        Mockito.verify(bookingRepo, Mockito.never()).save(Mockito.any(Booking.class));
        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any(User.class));
    }

    @Test
    void cancelBooking_WhenDifferentCustomerAttempts_ReturnsBadRequestAndLeavesBookingUnchanged() {
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));

        ResponseEntity<?> response = bookingService.cancelBooking(50L, 999L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals(BookingStatus.PENDING, booking.getStatus());
        Mockito.verify(bookingRepo, Mockito.never()).save(Mockito.any(Booking.class));
    }

    @Test
    void cancelBooking_WhenOwnerCancels_Succeeds() {
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = bookingService.cancelBooking(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        // PaymentService.initiateRefund is itself a no-op when there's nothing SUCCEEDED to
        // refund, so cancelling an unpaid booking is still expected to reach this call.
        Mockito.verify(paymentService).initiateRefund(50L);
    }

    @Test
    void cancelBooking_WhenCustomerCancelsOnCheckInDay_ReturnsBadRequest() {
        booking.setCheckIn(LocalDate.now());
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));

        ResponseEntity<?> response = bookingService.cancelBooking(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals(BookingStatus.PENDING, booking.getStatus());
        Mockito.verify(bookingRepo, Mockito.never()).save(Mockito.any(Booking.class));
        Mockito.verify(paymentService, Mockito.never()).initiateRefund(Mockito.anyLong());
    }

    @Test
    void cancelBooking_WhenCustomerCancelsMidStay_ReturnsBadRequest() {
        // Check-in was 2 days ago, check-out is still ahead — the guest is mid-stay, which is an
        // early-checkout situation, not a cancellation.
        booking.setCheckIn(LocalDate.now().minusDays(2));
        booking.setCheckOut(LocalDate.now().plusDays(1));
        booking.setStatus(BookingStatus.CONFIRMED);
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));

        ResponseEntity<?> response = bookingService.cancelBooking(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Assertions.assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        Mockito.verify(bookingRepo, Mockito.never()).save(Mockito.any(Booking.class));
    }

    @Test
    void cancelBooking_WhenAdminCancelsAfterCheckInHasPassed_Succeeds() {
        // Staff retain override capability for exceptional cases (complaints, fraud, goodwill
        // refunds) even though customers are bound by the check-in cutoff.
        booking.setCheckIn(LocalDate.now().minusDays(2));
        booking.setCheckOut(LocalDate.now().plusDays(1));
        booking.setStatus(BookingStatus.CONFIRMED);
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = bookingService.cancelBooking(50L, 999L, Role.ADMIN);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(BookingStatus.CANCELLED, booking.getStatus());
    }

    @Test
    void confirmBooking_WhenPending_SendsConfirmationEmailToBookingOwner() {
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(notificationService.bookingConfirmationTemplate(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.any(LocalDate.class), Mockito.any(LocalDate.class), Mockito.any(BigDecimal.class),
                Mockito.any(BigDecimal.class), Mockito.anyString(), Mockito.anyInt(),
                Mockito.any(BigDecimal.class), Mockito.anyList()))
                .thenReturn("<html>confirmation</html>");

        ResponseEntity<?> response = bookingService.confirmBooking(50L);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        BookingResponse body = (BookingResponse) response.getBody();
        Assertions.assertEquals(BookingStatus.CONFIRMED, body.getStatus());

        // Capture the actual arguments ResendEmailService.sendEmail() was called with, to confirm both
        // that the email fires and that it carries the booking owner's address and the rendered template.
        ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
        Mockito.verify(resendEmailService, Mockito.times(1))
                .sendEmail(toCaptor.capture(), subjectCaptor.capture(), htmlCaptor.capture());

        Assertions.assertEquals("guest@example.com", toCaptor.getValue());
        Assertions.assertEquals("Confirmation of Booked Room", subjectCaptor.getValue());
        Assertions.assertEquals("<html>confirmation</html>", htmlCaptor.getValue());
        // No promotion on this booking (setUp never attaches one) — confirming must not touch it.
        Mockito.verify(promotionService, Mockito.never()).incrementPromotionUsage(Mockito.anyString());
    }

    @Test
    void confirmBooking_WhenBookingHasPromotion_IncrementsUsageForThatPromotionsCode() {
        Promotion promotion = Promotion.builder()
                .id(900L)
                .code("SUMMER10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .build();
        booking.setPromotion(promotion);

        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(notificationService.bookingConfirmationTemplate(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.any(LocalDate.class), Mockito.any(LocalDate.class), Mockito.any(BigDecimal.class),
                Mockito.any(BigDecimal.class), Mockito.anyString(), Mockito.anyInt(),
                Mockito.any(BigDecimal.class), Mockito.anyList()))
                .thenReturn("<html>confirmation</html>");

        ResponseEntity<?> response = bookingService.confirmBooking(50L);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        BookingResponse body = (BookingResponse) response.getBody();
        Assertions.assertEquals("SUMMER10", body.getPromoCode());
        // Reads the code straight off the persisted booking, not from any caller-supplied value —
        // there's no request parameter left to spoof a different promo's usage counter with.
        Mockito.verify(promotionService, Mockito.times(1)).incrementPromotionUsage("SUMMER10");
    }

    @Test
    void confirmBooking_WhenNotPending_ReturnsBadRequestAndSendsNoEmail() {
        booking.setStatus(BookingStatus.CONFIRMED);
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));

        ResponseEntity<?> response = bookingService.confirmBooking(50L);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Mockito.verify(resendEmailService, Mockito.never())
                .sendEmail(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void confirmBooking_WhenRoomIsEcoFriendly_AwardsEcoPointsToUser() {
        room.setTags(Set.of(RoomTag.ECO_FRIENDLY));
        customer.setEcoPoints(10); // starts with some points already, to prove it's additive not a reset

        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(notificationService.bookingConfirmationTemplate(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.any(LocalDate.class), Mockito.any(LocalDate.class), Mockito.any(BigDecimal.class),
                Mockito.any(BigDecimal.class), Mockito.anyString(), Mockito.anyInt(),
                Mockito.any(BigDecimal.class), Mockito.anyList()))
                .thenReturn("<html>confirmation</html>");

        ResponseEntity<?> response = bookingService.confirmBooking(50L);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        BookingResponse body = (BookingResponse) response.getBody();
        Assertions.assertEquals(10, body.getEcoPointsEarned());
        Assertions.assertEquals(20, customer.getEcoPoints());
        Assertions.assertEquals(10, booking.getEcoPointsEarned());
        Mockito.verify(userRepository).save(customer);
    }

    @Test
    void confirmBooking_WhenRoomIsNotEcoFriendly_NoPointsAwarded() {
        // room.tags defaults to an empty set (see setUp) — not tagged ECO_FRIENDLY.
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(notificationService.bookingConfirmationTemplate(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.any(LocalDate.class), Mockito.any(LocalDate.class), Mockito.any(BigDecimal.class),
                Mockito.any(BigDecimal.class), Mockito.anyString(), Mockito.anyInt(),
                Mockito.any(BigDecimal.class), Mockito.anyList()))
                .thenReturn("<html>confirmation</html>");

        ResponseEntity<?> response = bookingService.confirmBooking(50L);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        BookingResponse body = (BookingResponse) response.getBody();
        Assertions.assertEquals(0, body.getEcoPointsEarned());
        Assertions.assertEquals(0, customer.getEcoPoints());
        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any(User.class));
    }

    @Test
    void cancelBooking_WhenConfirmedEcoBookingCancelled_RevokesEcoPoints() {
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setEcoPointsEarned(5);
        customer.setEcoPoints(5);

        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = bookingService.cancelBooking(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(0, customer.getEcoPoints());
        Assertions.assertEquals(0, booking.getEcoPointsEarned());
        Mockito.verify(userRepository).save(customer);
    }

    @Test
    void cancelBooking_WhenPendingNonEcoBookingCancelled_LeavesEcoPointsUntouched() {
        // Booking was never confirmed, so it never earned any points to claw back — cancelling a
        // still-PENDING booking should be a no-op for eco points.
        customer.setEcoPoints(10);
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = bookingService.cancelBooking(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(10, customer.getEcoPoints());
        Mockito.verify(userRepository, Mockito.never()).save(Mockito.any(User.class));
    }

    @Test
    void cancelBooking_WhenPointsWereRedeemed_ReturnsThemToUser() {
        booking.setEcoPointsRedeemed(40);
        booking.setEcoPointsDiscount(BigDecimal.valueOf(4));
        customer.setEcoPoints(10);
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = bookingService.cancelBooking(50L, 1L, Role.CUSTOMER);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(50, customer.getEcoPoints());
        Mockito.verify(userRepository).save(customer);
    }

    @Test
    void updateReservationStatus_WhenBookingMissing_ThrowsNoSuchElementException() {
        Mockito.when(bookingRepo.existsById(50L)).thenReturn(false);

        Assertions.assertThrows(java.util.NoSuchElementException.class,
                () -> bookingService.updateReservationStatus(50L, BookingStatus.CONFIRMED, 999L));
    }

    @Test
    void updateReservationStatus_Confirmed_DelegatesToConfirmBookingWithSameSideEffects() {
        Mockito.when(bookingRepo.existsById(50L)).thenReturn(true);
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));
        Mockito.when(notificationService.bookingConfirmationTemplate(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.any(LocalDate.class), Mockito.any(LocalDate.class), Mockito.any(BigDecimal.class),
                Mockito.any(BigDecimal.class), Mockito.anyString(), Mockito.anyInt(),
                Mockito.any(BigDecimal.class), Mockito.anyList()))
                .thenReturn("<html>confirmation</html>");

        ResponseEntity<?> response = bookingService.updateReservationStatus(50L, BookingStatus.CONFIRMED, 999L);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(BookingStatus.CONFIRMED, booking.getStatus());
        Mockito.verify(resendEmailService).sendEmail(Mockito.eq("guest@example.com"), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void updateReservationStatus_Cancelled_DelegatesToCancelBookingBypassingOwnershipCheck() {
        // adminUserId (999L) is not the booking owner (1L) — an admin override must still succeed,
        // unlike the equivalent CUSTOMER-role call which cancelBooking_WhenDifferentCustomerAttempts
        // above proves gets rejected.
        Mockito.when(bookingRepo.existsById(50L)).thenReturn(true);
        Mockito.when(bookingRepo.findById(50L)).thenReturn(Optional.of(booking));
        Mockito.when(bookingRepo.save(Mockito.any(Booking.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = bookingService.updateReservationStatus(50L, BookingStatus.CANCELLED, 999L);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertEquals(BookingStatus.CANCELLED, booking.getStatus());
        Mockito.verify(paymentService).initiateRefund(50L);
    }

    @Test
    void updateReservationStatus_Pending_ReturnsBadRequestAndLeavesBookingUntouched() {
        Mockito.when(bookingRepo.existsById(50L)).thenReturn(true);

        ResponseEntity<?> response = bookingService.updateReservationStatus(50L, BookingStatus.PENDING, 999L);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Mockito.verify(bookingRepo, Mockito.never()).save(Mockito.any(Booking.class));
    }

    @Test
    void getReservationsForAdmin_MapsRepositoryPageToBookingResponsePage() {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 20);
        org.springframework.data.domain.Page<Booking> repoPage =
                new org.springframework.data.domain.PageImpl<>(List.of(booking), pageable, 1);

        Mockito.when(bookingRepo.findAll(
                        Mockito.<org.springframework.data.jpa.domain.Specification<Booking>>any(),
                        Mockito.eq(pageable)))
                .thenReturn(repoPage);

        org.springframework.data.domain.Page<BookingResponse> result =
                bookingService.getReservationsForAdmin(1000L, null, null, pageable);

        Assertions.assertEquals(1, result.getTotalElements());
        Assertions.assertEquals(50L, result.getContent().get(0).getId());
        Assertions.assertEquals(BookingStatus.PENDING, result.getContent().get(0).getStatus());
    }
}
