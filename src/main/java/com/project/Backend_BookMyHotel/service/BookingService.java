package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.domain.BookingAddonService;
import com.project.Backend_BookMyHotel.domain.Promotion;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.*;
import com.project.Backend_BookMyHotel.repository.BookingRepository;
import com.project.Backend_BookMyHotel.repository.PromotionRepository;
import com.project.Backend_BookMyHotel.repository.RoomRepository;
import com.project.Backend_BookMyHotel.repository.ServiceRepository;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.specification.BookingSpecification;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.file.AccessDeniedException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    @Autowired
    private BookingRepository bookingRepo;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoomAvailabilityService availabilityService;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private EmailTemplateService notificationService;

    @Autowired
    private ResendEmailService resendEmailService;

    // Circular with PaymentService (which calls back into confirmBooking()). Modern Spring
    // rejects circular bean references by default even with field injection, so @Lazy wraps this
    // one side in a proxy that only resolves the real bean on first use — by then both beans have
    // finished constructing, so there's nothing left to deadlock on.
    @Lazy
    @Autowired
    private PaymentService paymentService;

    @Value("${eco.points.per-booking:5}")
    private int ecoPointsPerBooking;

    /*
     What @CacheEvict does: The moment createBooking() or cancelBooking() runs successfully,
     @CacheEvict automatically wipes the "availability" bucket in Redis. The very next room
     search is forced to hit PostgreSQL, fetch the fresh room counts, and store the updated
     data back in Redis.
     */

    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public ResponseEntity<?> createBooking(Authentication authentication, CreateBookingRequest request) {
        LocalDate checkIn = request.checkIn();
        LocalDate checkOut = request.checkOut();

        // 1. Date Validations
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return ResponseEntity.badRequest().body("Check-out date must be strictly after check-in date.");
        }

        if (checkIn.isBefore(LocalDate.now())) {
            return ResponseEntity.badRequest().body("Check-in date cannot be in the past.");
        }

        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new NoSuchElementException("Room not found with ID: " + request.roomId()));

        String email = authentication.getName();

        User user = userRepository.findByEmail(email);

        if (user == null){
            return ResponseEntity.badRequest().body("User Not Found");
        }

        // 2. Check Overlapping Active Bookings (PENDING & CONFIRMED)
        List<Booking> overlapping = bookingRepo.findOverlappingBookings(
                room.getId(), BookingStatus.PENDING, checkIn, checkOut);

        overlapping.addAll(bookingRepo.findOverlappingBookings(
                room.getId(), BookingStatus.CONFIRMED, checkIn, checkOut));

        if (!overlapping.isEmpty()) {
            return ResponseEntity.badRequest().body("Room " + room.getId() + " is not available for the selected dates.");
        }

        // 3. Calculate Base Price dynamically (accounting for daily rates)
        RoomPriceResponse priceCalculation = availabilityService.calculateTotalPrice(room.getId(), checkIn, checkOut);
        if (!priceCalculation.isAvailable()) {
            return ResponseEntity.badRequest().body("Room is unavailable due to maintenance or existing reservation.");
        }

        BigDecimal basePrice = priceCalculation.getTotalPrice();
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal finalPrice = basePrice;
        String appliedPromoCode = null;
        Promotion appliedPromotion = null;

        // 4. Apply Promotion Code if provided
        if (request.promoCode() != null && !request.promoCode().isBlank()) {
            Long hotelId = room.getBranch() != null && room.getBranch().getHotel() != null
                    ? room.getBranch().getHotel().getId() : null;

            PromotionBreakdownResponse promoResult = promotionService.applyPromotion(
                    request.promoCode().trim(), basePrice, hotelId);

            discountAmount = promoResult.getDiscountAmount();
            finalPrice = promoResult.getFinalPrice();
            appliedPromoCode = promoResult.getPromoCode();

            // applyPromotion only returns a non-null promoCode once it has already validated the
            // code exists, is active, in-date, under its usage cap, and matches this hotel — so
            // this lookup is just fetching the entity to link, not re-validating anything.
            if (appliedPromoCode != null) {
                appliedPromotion = promotionRepository.findByCodeIgnoreCase(appliedPromoCode).orElse(null);
            }
        }

        String reference = "BMH" + UUID.randomUUID();

        // 5. Persist PENDING Booking
        Booking booking = Booking.builder()
                .user(user)
                .room(room)
                .checkIn(checkIn)
                .checkOut(checkOut)
                .status(BookingStatus.PENDING)
                .reference(reference)
                .promotion(appliedPromotion)
                .totalPrice(finalPrice)
                .createdAt(LocalDateTime.now())
                .build();

        Booking savedBooking = bookingRepo.save(booking);

        // 6. Build & Return Response DTO
        return new ResponseEntity<>(BookingResponse.builder()
                .id(savedBooking.getId())
                .roomId(room.getId())
                .userId(user.getId())
                .checkIn(savedBooking.getCheckIn())
                .checkOut(savedBooking.getCheckOut())
                .status(savedBooking.getStatus())
                .reference(reference)
                .totalPrice(savedBooking.getTotalPrice())
                .promoCode(appliedPromoCode)
                .ecoPointsEarned(savedBooking.getEcoPointsEarned())
                .createdAt(savedBooking.getCreatedAt())
                .priceBreakdown(BookingResponse.PriceBreakdown.builder()
                        .basePrice(basePrice)
                        .discountAmount(discountAmount)
                        .finalPrice(finalPrice)
                        .appliedPromoCode(appliedPromoCode)
                        .build())
                .build(), HttpStatus.CREATED);
    }

    // CONFIRM BOOKING: Called by Stripe Webhook / Payment Success Callback
    @Transactional
    public ResponseEntity<?> confirmBooking(Long bookingId) {
        Booking booking = bookingRepo.findById(bookingId).orElseThrow(() -> new RuntimeException("Booking not found with ID: " + bookingId));

        if (booking.getStatus() != BookingStatus.PENDING) {
            return ResponseEntity.badRequest().body("Only PENDING bookings can be confirmed. Current status: " + booking.getStatus());
        }

        booking.setStatus(BookingStatus.CONFIRMED);

        // Eco points: awarded once, here at confirmation (not at creation, so a PENDING booking
        // that never gets paid can't farm points), for a room tagged eco-friendly. Recorded on
        // the booking itself so cancelBooking() can claw back exactly this amount later rather
        // than guessing how many points a cancelled booking had contributed.
        if (booking.getRoom().getTags() != null && booking.getRoom().getTags().contains(RoomTag.ECO_FRIENDLY)) {
            booking.setEcoPointsEarned(ecoPointsPerBooking);
            User bookingUser = booking.getUser();
            int currentPoints = bookingUser.getEcoPoints() != null ? bookingUser.getEcoPoints() : 0;
            bookingUser.setEcoPoints(currentPoints + ecoPointsPerBooking);
            userRepository.save(bookingUser);
        }

        Booking updated = bookingRepo.save(booking);

        // Increment usage on whichever promo is actually attached to this booking — reading it
        // off the persisted record instead of trusting a client-supplied promoCode means a caller
        // can no longer bump the usage counter of a promo that was never applied to this booking.
        if (booking.getPromotion() != null) {
            promotionService.incrementPromotionUsage(booking.getPromotion().getCode());
        }

        // Send Email Confirmation
        String html = notificationService.bookingConfirmationTemplate(
                booking.getUser().getFirstName() + booking.getUser().getLastName(),
                booking.getReference(),
                booking.getRoom().getBranch().getHotel().getName(),
                booking.getRoom().getBranch().getName(),
                booking.getRoom().getRoomType(),
                booking.getCheckIn(),
                booking.getCheckOut(),
                booking.getTotalPrice(),
                booking.getRoom().getBranch().getCurrency()
        );

        // This runs in the same transaction as the payment/booking status writes above (this method
        // is called directly from PaymentService.confirmPayment() inside the webhook handler). An
        // uncaught exception here — e.g. Resend's API rejecting the send — would roll back the
        // whole transaction and silently undo a real, already-succeeded Stripe payment. A failed
        // confirmation email is not worth losing that for, so it's logged and swallowed instead.
        try {
            System.out.println("HTML template built, sending email");
            resendEmailService.sendEmail(
                    booking.getUser().getEmail(),
                    "Confirmation of Booked Room",
                    html
            );
            System.out.println("Email Sent successfully");
        } catch (Exception e) {
            log.error("Failed to send booking confirmation email for booking {}: {}", booking.getId(), e.getMessage(), e);
        }

        return mapToBookingResponse(updated);
    }

    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public ResponseEntity<?> cancelBooking(Long bookingId, Long userId, Role userRole) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found with ID: " + bookingId));

        // Ownership Check: Customers can only cancel their own bookings
        if ("CUSTOMER".equalsIgnoreCase(userRole.toString()) && !booking.getUser().getId().equals(userId)) {
            return ResponseEntity.badRequest().body("You are not authorized to cancel this booking.");
        }

        // Valid State Check
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            return ResponseEntity.badRequest().body("Booking has already been cancelled.");
        }

        // Cancellation window: standard hotel/OTA practice closes cancellation at check-in — not
        // same-day, and never mid-stay (that's an early checkout / no-show, a different operation
        // with different refund rules, not handled by this endpoint). Staff can still override for
        // exceptional cases (complaints, fraud, goodwill refunds) — only customers are bound by it.
        if ("CUSTOMER".equalsIgnoreCase(userRole.toString()) && !booking.getCheckIn().isAfter(LocalDate.now())) {
            return ResponseEntity.badRequest().body("Bookings can only be cancelled before the check-in date.");
        }

        booking.setStatus(BookingStatus.CANCELLED);

        // Claw back eco points this booking previously earned — only fires if it had already
        // been confirmed (a still-PENDING booking never had any awarded in the first place).
        if (booking.getEcoPointsEarned() != null && booking.getEcoPointsEarned() > 0) {
            User bookingUser = booking.getUser();
            int currentPoints = bookingUser.getEcoPoints() != null ? bookingUser.getEcoPoints() : 0;
            bookingUser.setEcoPoints(Math.max(0, currentPoints - booking.getEcoPointsEarned()));
            userRepository.save(bookingUser);
            booking.setEcoPointsEarned(0);
        }

        bookingRepo.save(booking);

        // No-op if this booking was never actually paid for (most cancellations are of PENDING
        // bookings that never got past checkout) — see PaymentService.initiateRefund for details.
        paymentService.initiateRefund(booking.getId());

        // Note: Dates are freed automatically because availability queries filter out CANCELLED bookings.
        return mapToBookingResponse(booking);
    }

    @Transactional
    public ResponseEntity<?> addServicesToBooking(Long bookingId, Long userId, Role userRole, AddServicesRequest request) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found with ID: " + bookingId));

        // Ownership Check: Customers can only modify their own bookings
        if ("CUSTOMER".equalsIgnoreCase(userRole.toString()) && !booking.getUser().getId().equals(userId)) {
            return ResponseEntity.badRequest().body("You are not authorized to modify this booking.");
        }

        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            return ResponseEntity.badRequest().body("Services can only be added to PENDING or CONFIRMED bookings. Current status: " + booking.getStatus());
        }

        if (booking.getCheckIn().isBefore(LocalDate.now())) {
            return ResponseEntity.badRequest().body("Cannot add services after check-in has passed.");
        }

        Long branchId = booking.getRoom().getBranch().getId();
        List<BookingAddonService> newAddons = new ArrayList<>();
        BigDecimal addedTotal = BigDecimal.ZERO;

        for (AddServicesRequest.ServiceItem item : request.services()) {
            com.project.Backend_BookMyHotel.domain.Service service = serviceRepository.findById(item.serviceId())
                    .orElseThrow(() -> new NoSuchElementException("Service not found with ID: " + item.serviceId()));

            if (!service.getBranch().getId().equals(branchId)) {
                return ResponseEntity.badRequest().body("Service " + service.getId() + " is not offered at this booking's branch.");
            }

            BigDecimal subtotal = service.getPrice().multiply(BigDecimal.valueOf(item.quantity()));

            BookingAddonService addon = new BookingAddonService();
            addon.setBooking(booking);
            addon.setService(service);
            addon.setQuantity(item.quantity());
            addon.setSubtotal(subtotal);

            newAddons.add(addon);
            addedTotal = addedTotal.add(subtotal);
        }

        // The current total already reflects the room price (net of any promotion discount) plus any
        // previously added services, so recalculating just means adding this request's new subtotal on top.
        if (booking.getBookingServices() == null) {
            booking.setBookingServices(new ArrayList<>());
        }
        booking.getBookingServices().addAll(newAddons);
        booking.setTotalPrice(booking.getTotalPrice().add(addedTotal));

        Booking saved = bookingRepo.save(booking);

        return ResponseEntity.ok(toBookingResponseWithServices(saved));
    }

    @Transactional
    public ResponseEntity<?> removeServiceFromBooking(Long bookingId, Long serviceId, Long userId, Role userRole) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found with ID: " + bookingId));

        if ("CUSTOMER".equalsIgnoreCase(userRole.toString()) && !booking.getUser().getId().equals(userId)) {
            return ResponseEntity.badRequest().body("You are not authorized to modify this booking.");
        }

        if (booking.getStatus() != BookingStatus.PENDING && booking.getStatus() != BookingStatus.CONFIRMED) {
            return ResponseEntity.badRequest().body("Services can only be removed from PENDING or CONFIRMED bookings. Current status: " + booking.getStatus());
        }

        List<BookingAddonService> addons = booking.getBookingServices();
        List<BookingAddonService> toRemove = addons != null
                ? addons.stream().filter(a -> a.getService() != null && a.getService().getId().equals(serviceId)).toList()
                : List.of();

        if (toRemove.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Service " + serviceId + " is not attached to booking " + bookingId + ".");
        }

        BigDecimal removedTotal = toRemove.stream()
                .map(BookingAddonService::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        addons.removeAll(toRemove);
        booking.setTotalPrice(booking.getTotalPrice().subtract(removedTotal));

        Booking saved = bookingRepo.save(booking);

        return ResponseEntity.ok(toBookingResponseWithServices(saved));
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> getUserBookings(User user, BookingStatus status, Pageable pageable) throws BadRequestException, AccessDeniedException {
        Page<Booking> bookings;

        switch (user.getRole()) {
            case CUSTOMER -> {
                bookings = (status != null)
                        ? bookingRepo.findByUserIdAndStatus(user.getId(), status, pageable)
                        : bookingRepo.findByUserId(user.getId(), pageable);
            }
//            case HOTEL_MANAGER -> {
//                if (user.getHotel() == null) {
//                    throw new BadRequestException("Hotel manager is not assigned to any hotel.");
//                }
//                Long hotelId = user.getHotel().getId();
//                bookings = (status != null)
//                        ? bookingRepo.findByRoomBranchHotelIdAndStatus(hotelId, status, pageable)
//                        : bookingRepo.findByRoomBranchHotelId(hotelId, pageable);
//            }
            default -> throw new AccessDeniedException("Admins do not have access to personal booking lists.");
        }

        return bookings.map(booking -> {
            BookingResponse response = new BookingResponse();
            response.setId(booking.getId());
            response.setRoomId(booking.getRoom() != null ? booking.getRoom().getId() : null);
            response.setUserId(booking.getUser() != null ? booking.getUser().getId() : null);
            response.setCheckIn(booking.getCheckIn());
            response.setCheckOut(booking.getCheckOut());
            response.setStatus(booking.getStatus());
            response.setTotalPrice(booking.getTotalPrice());
            response.setPromoCode(promoCodeOf(booking));
            response.setEcoPointsEarned(booking.getEcoPointsEarned());
            response.setCreatedAt(booking.getCreatedAt());
            return response;
        });
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getBookingById(Long bookingId, Long userId, Role userRole) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found with ID: " + bookingId));

        // Ownership check
        if ("CUSTOMER".equalsIgnoreCase(userRole.toString()) && !booking.getUser().getId().equals(userId)) {
            return ResponseEntity.badRequest().body("You are not authorized to view this booking.");
        }

        var services = booking.getBookingServices() != null
                ? booking.getBookingServices().stream()
                .map(s -> BookingDetailResponse.AddonServiceResponse.builder()
                        .id(s.getId())
                        .serviceName(s.getService() != null ? s.getService().getName() : "Addon Service")
                        .quantity(s.getQuantity())
                        .subtotal(s.getSubtotal())
                        .build())
                .toList()
                : List.<BookingDetailResponse.AddonServiceResponse>of();

        var payments = booking.getPayments() != null
                ? booking.getPayments().stream()
                .map(p -> BookingDetailResponse.PaymentResponse.builder()
                        .id(p.getId())
                        .stripePaymentId(p.getStripePaymentId())
                        .amount(p.getAmount())
                        .currency(p.getCurrency())
                        .status(p.getStatus())
                        .refundId(p.getRefundId())
                        .paidAt(p.getPaidAt())
                        .build())
                .toList()
                : List.<BookingDetailResponse.PaymentResponse>of();

        return ResponseEntity.ok(BookingDetailResponse.builder()
                .id(booking.getId())
                .roomId(booking.getRoom().getId())
                .roomNumber(booking.getRoom().getId().toString())
                .userId(booking.getUser().getId())
                .checkIn(booking.getCheckIn())
                .checkOut(booking.getCheckOut())
                .status(booking.getStatus())
                .totalPrice(booking.getTotalPrice())
                .promoCode(promoCodeOf(booking))
                .ecoPointsEarned(booking.getEcoPointsEarned())
                .createdAt(booking.getCreatedAt())
                .services(services)
                .payments(payments)
                .build());
    }

    // Null-safe accessor for Booking.promotion.code — most bookings won't have one attached.
    private String promoCodeOf(Booking booking) {
        return booking.getPromotion() != null ? booking.getPromotion().getCode() : null;
    }

    private ResponseEntity<BookingResponse> mapToBookingResponse(Booking booking) {
        return ResponseEntity.ok(toBookingResponseDto(booking));
    }

    private BookingResponse toBookingResponseDto(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .roomId(booking.getRoom().getId())
                .userId(booking.getUser().getId())
                .checkIn(booking.getCheckIn())
                .checkOut(booking.getCheckOut())
                .status(booking.getStatus())
                .totalPrice(booking.getTotalPrice())
                .promoCode(promoCodeOf(booking))
                .ecoPointsEarned(booking.getEcoPointsEarned())
                .createdAt(booking.getCreatedAt())
                .build();
    }

    // Admin reservation list: any combination of hotel/date/status, or none at all.
    @Transactional(readOnly = true)
    public Page<BookingResponse> getReservationsForAdmin(Long hotelId, LocalDate date, BookingStatus status, Pageable pageable) {
        Specification<Booking> spec = BookingSpecification.buildAdminFilterSpec(hotelId, date, status);
        return bookingRepo.findAll(spec, pageable).map(this::toBookingResponseDto);
    }

    // Admin status override. CONFIRMED/CANCELLED delegate to the same methods the customer-facing
    // and webhook-driven flows use, so an admin-driven change carries the same side effects (eco
    // points, promo usage, refund initiation, confirmation email) — just without the ownership and
    // cancellation-window checks, which only apply when the caller's role is CUSTOMER. Reverting to
    // PENDING isn't supported: there's no safe way to unwind those side effects once applied.
    @Transactional
    public ResponseEntity<?> updateReservationStatus(Long bookingId, BookingStatus newStatus, Long adminUserId) {
        if (!bookingRepo.existsById(bookingId)) {
            throw new NoSuchElementException("Booking not found with ID: " + bookingId);
        }

        return switch (newStatus) {
            case CONFIRMED -> confirmBooking(bookingId);
            case CANCELLED -> cancelBooking(bookingId, adminUserId, Role.ADMIN);
            case PENDING -> ResponseEntity.badRequest().body("Reservations cannot be reverted to PENDING.");
        };
    }

    private BookingResponse toBookingResponseWithServices(Booking booking) {
        List<BookingResponse.AddonServiceResponse> services = booking.getBookingServices() != null
                ? booking.getBookingServices().stream()
                .map(s -> BookingResponse.AddonServiceResponse.builder()
                        .id(s.getId())
                        .serviceName(s.getService() != null ? s.getService().getName() : "Addon Service")
                        .quantity(s.getQuantity())
                        .subtotal(s.getSubtotal())
                        .build())
                .toList()
                : List.<BookingResponse.AddonServiceResponse>of();

        return BookingResponse.builder()
                .id(booking.getId())
                .roomId(booking.getRoom().getId())
                .userId(booking.getUser().getId())
                .reference(booking.getReference())
                .checkIn(booking.getCheckIn())
                .checkOut(booking.getCheckOut())
                .status(booking.getStatus())
                .totalPrice(booking.getTotalPrice())
                .promoCode(promoCodeOf(booking))
                .ecoPointsEarned(booking.getEcoPointsEarned())
                .services(services)
                .createdAt(booking.getCreatedAt())
                .build();
    }

}
