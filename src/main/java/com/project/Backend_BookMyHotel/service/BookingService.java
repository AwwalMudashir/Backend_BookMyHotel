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
import java.math.RoundingMode;
import java.nio.file.AccessDeniedException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
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
    private ExchangeRateService exchangeRateService;

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

    @Value("${eco.points.per-night:5}")
    private int ecoPointsPerNight;

    private static final BigDecimal ECO_POINTS_PER_USD = BigDecimal.TEN;

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

        if (Boolean.FALSE.equals(room.getActive())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("This room has been removed from public listings and is no longer available for booking.");
        }

        String email = authentication.getName();

        int requestedEcoPoints = request.ecoPointsToRedeem() != null ? request.ecoPointsToRedeem() : 0;
        if (requestedEcoPoints < 0) {
            return ResponseEntity.badRequest().body("Eco points cannot be negative.");
        }

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
        String bookingCurrency = priceCalculation.getCurrency();
        if (bookingCurrency == null || bookingCurrency.isBlank()) {
            bookingCurrency = roomCurrency(room);
        }
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

        // 5. Validate and price extras against the room's branch. Hotel-wide services have no
        // branch, while branch-specific services must match exactly. Prices and names are copied
        // onto the booking line so later service edits never rewrite a customer's receipt.
        List<BookingAddonService> addons = new ArrayList<>();
        BigDecimal servicesTotal = BigDecimal.ZERO;
        Set<Long> selectedServiceIds = new HashSet<>();
        List<AddServicesRequest.ServiceItem> requestedServices = request.services() != null
                ? request.services() : List.of();
        BigDecimal usdToBookingRate = (!requestedServices.isEmpty() || requestedEcoPoints > 0)
                ? exchangeRateService.convert(BigDecimal.ONE, "USD", bookingCurrency)
                : BigDecimal.ONE;

        for (AddServicesRequest.ServiceItem item : requestedServices) {
            if (!selectedServiceIds.add(item.serviceId())) {
                return ResponseEntity.badRequest().body("Each service can only be selected once.");
            }

            com.project.Backend_BookMyHotel.domain.Service service = serviceRepository.findById(item.serviceId())
                    .orElseThrow(() -> new NoSuchElementException("Service not found with ID: " + item.serviceId()));

            if (!isServiceAvailableAtBranch(service, room.getBranch())) {
                return ResponseEntity.badRequest().body("Service " + service.getId() + " is not available at this room's branch.");
            }

            BigDecimal unitPrice = service.getPrice().multiply(usdToBookingRate)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            BookingAddonService addon = new BookingAddonService();
            addon.setService(service);
            addon.setServiceName(service.getName());
            addon.setUnitPrice(unitPrice);
            addon.setQuantity(item.quantity());
            addon.setSubtotal(subtotal);
            addons.add(addon);
            servicesTotal = servicesTotal.add(subtotal);
        }

        // Promotions apply first. Eco points then discount the room only, while optional services
        // remain fully priced. Ten points always represent one US dollar before conversion into
        // the branch's payment currency.
        BigDecimal ecoPointsDiscount = BigDecimal.ZERO;
        if (requestedEcoPoints > 0) {
            // Take the row lock only after all external pricing work is complete, then validate
            // against the authoritative balance and keep it until the booking commits.
            user = userRepository.findByEmailForUpdate(email).orElse(null);
            if (user == null) {
                return ResponseEntity.badRequest().body("User Not Found");
            }
            int availablePoints = user.getEcoPoints() != null ? user.getEcoPoints() : 0;
            if (requestedEcoPoints > availablePoints) {
                return ResponseEntity.badRequest().body("You do not have enough eco points for this redemption.");
            }

            BigDecimal discountInUsd = BigDecimal.valueOf(requestedEcoPoints)
                    .divide(ECO_POINTS_PER_USD, 2, RoundingMode.HALF_UP);
            ecoPointsDiscount = discountInUsd.multiply(usdToBookingRate)
                    .setScale(2, RoundingMode.HALF_UP);

            if (ecoPointsDiscount.compareTo(finalPrice) > 0) {
                return ResponseEntity.badRequest().body("Eco point discount cannot exceed the room price after promotions.");
            }
        }

        BigDecimal bookingTotal = finalPrice.subtract(ecoPointsDiscount).add(servicesTotal)
                .setScale(2, RoundingMode.HALF_UP);

        String reference = "BMH" + UUID.randomUUID();

        // 6. Persist the PENDING booking and selected extras atomically. This prevents a payment
        // page from ever seeing a room-only total while a second request is still attaching extras.
        Booking booking = Booking.builder()
                .user(user)
                .room(room)
                .checkIn(checkIn)
                .checkOut(checkOut)
                .status(BookingStatus.PENDING)
                .reference(reference)
                .promotion(appliedPromotion)
                .totalPrice(bookingTotal)
                .ecoPointsRedeemed(requestedEcoPoints)
                .ecoPointsDiscount(ecoPointsDiscount)
                .bookingServices(addons)
                .createdAt(LocalDateTime.now())
                .build();

        addons.forEach(addon -> addon.setBooking(booking));

        if (requestedEcoPoints > 0) {
            user.setEcoPoints(user.getEcoPoints() - requestedEcoPoints);
            userRepository.save(user);
        }

        Booking savedBooking = bookingRepo.save(booking);

        // 7. Build & Return Response DTO
        return new ResponseEntity<>(BookingResponse.builder()
                .id(savedBooking.getId())
                .roomId(room.getId())
                .roomPublicId(room.getRoomId())
                .userId(user.getId())
                .checkIn(savedBooking.getCheckIn())
                .checkOut(savedBooking.getCheckOut())
                .status(savedBooking.getStatus())
                .reference(reference)
                .totalPrice(savedBooking.getTotalPrice())
                .promoCode(appliedPromoCode)
                .ecoPointsEarned(savedBooking.getEcoPointsEarned())
                .ecoPointsRedeemed(savedBooking.getEcoPointsRedeemed())
                .ecoPointsDiscount(savedBooking.getEcoPointsDiscount())
                .createdAt(savedBooking.getCreatedAt())
                .services(toAddonResponses(savedBooking))
                .priceBreakdown(BookingResponse.PriceBreakdown.builder()
                        .basePrice(basePrice)
                        .discountAmount(discountAmount)
                        .servicesTotal(servicesTotal)
                        .ecoPointsRedeemed(requestedEcoPoints)
                        .ecoPointsDiscount(ecoPointsDiscount)
                        .finalPrice(bookingTotal)
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
            long nights = ChronoUnit.DAYS.between(booking.getCheckIn(), booking.getCheckOut());
            int pointsEarned = Math.toIntExact(Math.multiplyExact(nights, ecoPointsPerNight));
            booking.setEcoPointsEarned(pointsEarned);
            User bookingUser = userRepository.findByEmailForUpdate(booking.getUser().getEmail())
                    .orElse(booking.getUser());
            int currentPoints = bookingUser.getEcoPoints() != null ? bookingUser.getEcoPoints() : 0;
            bookingUser.setEcoPoints(currentPoints + pointsEarned);
            userRepository.save(bookingUser);
        }

        Booking updated = bookingRepo.save(booking);

        // Increment usage on whichever promo is actually attached to this booking — reading it
        // off the persisted record instead of trusting a client-supplied promoCode means a caller
        // can no longer bump the usage counter of a promo that was never applied to this booking.
        if (booking.getPromotion() != null) {
            promotionService.incrementPromotionUsage(booking.getPromotion().getCode());
        }

        BigDecimal servicesTotal = bookingServicesTotal(booking);
        BigDecimal accommodationTotal = booking.getTotalPrice()
                .subtract(servicesTotal)
                .add(booking.getEcoPointsDiscount() != null ? booking.getEcoPointsDiscount() : BigDecimal.ZERO);

        // Send Email Confirmation, including every paid extra and the final charged total.
        String html = notificationService.bookingConfirmationTemplate(
                (booking.getUser().getFirstName() + " " + booking.getUser().getLastName()).trim(),
                booking.getReference(),
                booking.getRoom().getBranch().getHotel().getName(),
                booking.getRoom().getBranch().getName(),
                booking.getRoom().getRoomType(),
                booking.getCheckIn(),
                booking.getCheckOut(),
                accommodationTotal,
                booking.getTotalPrice(),
                roomCurrency(booking.getRoom()),
                booking.getEcoPointsRedeemed(),
                booking.getEcoPointsDiscount(),
                toEmailServiceLines(booking)
        );

        // This runs in the same transaction as the payment/booking status writes above (this method
        // is called directly from PaymentService.confirmPayment() inside the webhook handler). An
        // uncaught exception here — e.g. Resend's API rejecting the send — would roll back the
        // whole transaction and silently undo a real, already-succeeded Stripe payment. A failed
        // confirmation email is not worth losing that for, so it's logged and swallowed instead.
        System.out.println("HTML template built, sending email");
        boolean sent = resendEmailService.sendEmail(
                booking.getUser().getEmail(),
                "Confirmation of Booked Room",
                html
        );
        System.out.println(sent ? "Email Sent successfully" : "Email sending failed");

        if (!sent) {
            log.error("Failed to send booking confirmation email for booking {}", booking.getId());
            return ResponseEntity.ok().header("X-Email-Failure", "booking_confirmation_email_failed").body(toBookingResponseDto(updated));
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

        LocalDate today = LocalDate.now();
        boolean isEarlyCheckout = !booking.getCheckIn().isAfter(today) && booking.getCheckOut().isAfter(today);

        // Customers can cancel before check-out, including current stays. If the booking has
        // already reached or passed the checkout date, there is no cancellation action needed.
        if ("CUSTOMER".equalsIgnoreCase(userRole.toString()) && !booking.getCheckOut().isAfter(today)) {
            return ResponseEntity.badRequest().body("Bookings can only be cancelled before the checkout date.");
        }

        booking.setStatus(BookingStatus.CANCELLED);

        // Claw back eco points this booking previously earned — only fires if it had already
        // been confirmed (a still-PENDING booking never had any awarded in the first place).
        int pointsEarned = booking.getEcoPointsEarned() != null ? booking.getEcoPointsEarned() : 0;
        int pointsRedeemed = booking.getEcoPointsRedeemed() != null ? booking.getEcoPointsRedeemed() : 0;
        if (pointsEarned > 0 || pointsRedeemed > 0) {
            User bookingUser = userRepository.findByEmailForUpdate(booking.getUser().getEmail())
                    .orElse(booking.getUser());
            int currentPoints = bookingUser.getEcoPoints() != null ? bookingUser.getEcoPoints() : 0;
            bookingUser.setEcoPoints(Math.max(0, currentPoints - pointsEarned) + pointsRedeemed);
            userRepository.save(bookingUser);
            booking.setEcoPointsEarned(0);
        }

        bookingRepo.save(booking);

        // No-op if this booking was never actually paid for (most cancellations are of PENDING
        // bookings that never got past checkout) — see PaymentService.initiateRefund for details.
        Optional<PaymentService.RefundResult> refundResult = paymentService.initiateRefund(booking.getId());

        String refundAmount = refundResult
                .map(refund -> refund.currency() + " " + refund.amount().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .orElse(null);

        String customerName = (booking.getUser().getFirstName() + " " + booking.getUser().getLastName()).trim();
        String html = notificationService.bookingCancellationTemplate(
                customerName,
                booking.getReference(),
                booking.getRoom().getBranch().getHotel().getName(),
                booking.getRoom().getBranch().getName(),
                booking.getRoom().getRoomType(),
                booking.getCheckIn(),
                LocalDate.now(),
                refundAmount,
                refundResult.isPresent(),
                isEarlyCheckout
        );

        boolean sent = resendEmailService.sendEmail(
                booking.getUser().getEmail(),
                refundResult.isPresent() ? "Booking Cancelled and Refund Processed" : "Booking Cancelled",
                html
        );

        if (!sent) {
            log.error("Failed to send booking cancellation email for booking {}", booking.getId());
            return ResponseEntity.ok().header("X-Email-Failure", "booking_cancellation_email_failed").body(toBookingResponseDto(booking));
        }

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

        if (booking.getStatus() != BookingStatus.PENDING) {
            return ResponseEntity.badRequest().body("Services can only be changed before payment. Current status: " + booking.getStatus());
        }

        if (booking.getCheckIn().isBefore(LocalDate.now())) {
            return ResponseEntity.badRequest().body("Cannot add services after check-in has passed.");
        }

        if (booking.getPayments() != null && !booking.getPayments().isEmpty()) {
            return ResponseEntity.badRequest().body("Services cannot be changed after payment has started.");
        }

        List<BookingAddonService> newAddons = new ArrayList<>();
        BigDecimal addedTotal = BigDecimal.ZERO;
        String bookingCurrency = roomCurrency(booking.getRoom());
        BigDecimal usdToBookingRate = exchangeRateService.convert(BigDecimal.ONE, "USD", bookingCurrency);

        for (AddServicesRequest.ServiceItem item : request.services()) {
            com.project.Backend_BookMyHotel.domain.Service service = serviceRepository.findById(item.serviceId())
                    .orElseThrow(() -> new NoSuchElementException("Service not found with ID: " + item.serviceId()));

            if (!isServiceAvailableAtBranch(service, booking.getRoom().getBranch())) {
                return ResponseEntity.badRequest().body("Service " + service.getId() + " is not offered at this booking's branch.");
            }

            BigDecimal unitPrice = service.getPrice().multiply(usdToBookingRate)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.quantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            BookingAddonService addon = new BookingAddonService();
            addon.setBooking(booking);
            addon.setService(service);
            addon.setServiceName(service.getName());
            addon.setUnitPrice(unitPrice);
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

        if (booking.getStatus() != BookingStatus.PENDING) {
            return ResponseEntity.badRequest().body("Services can only be changed before payment. Current status: " + booking.getStatus());
        }

        if (booking.getPayments() != null && !booking.getPayments().isEmpty()) {
            return ResponseEntity.badRequest().body("Services cannot be changed after payment has started.");
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

        return bookings.map(this::toBookingResponseDto);
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
                        .serviceId(s.getService() != null ? s.getService().getId() : null)
                        .serviceName(addonName(s))
                        .unitPrice(addonUnitPrice(s))
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
                .roomPublicId(booking.getRoom().getRoomId())
                .roomNumber(booking.getRoom().getId().toString())
                .hotelName(booking.getRoom().getBranch().getHotel().getName())
                .userId(booking.getUser().getId())
                .checkIn(booking.getCheckIn())
                .checkOut(booking.getCheckOut())
                .status(booking.getStatus())
                .totalPrice(booking.getTotalPrice())
                .promoCode(promoCodeOf(booking))
                .ecoPointsEarned(booking.getEcoPointsEarned())
                .ecoPointsRedeemed(booking.getEcoPointsRedeemed())
                .ecoPointsDiscount(booking.getEcoPointsDiscount())
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
                .roomPublicId(booking.getRoom().getRoomId())
                .userId(booking.getUser().getId())
                .reference(booking.getReference())
                .checkIn(booking.getCheckIn())
                .checkOut(booking.getCheckOut())
                .status(booking.getStatus())
                .totalPrice(booking.getTotalPrice())
                .promoCode(promoCodeOf(booking))
                .ecoPointsEarned(booking.getEcoPointsEarned())
                .ecoPointsRedeemed(booking.getEcoPointsRedeemed())
                .ecoPointsDiscount(booking.getEcoPointsDiscount())
                .services(toAddonResponses(booking))
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
        return BookingResponse.builder()
                .id(booking.getId())
                .roomId(booking.getRoom().getId())
                .roomPublicId(booking.getRoom().getRoomId())
                .userId(booking.getUser().getId())
                .reference(booking.getReference())
                .checkIn(booking.getCheckIn())
                .checkOut(booking.getCheckOut())
                .status(booking.getStatus())
                .totalPrice(booking.getTotalPrice())
                .promoCode(promoCodeOf(booking))
                .ecoPointsEarned(booking.getEcoPointsEarned())
                .ecoPointsRedeemed(booking.getEcoPointsRedeemed())
                .ecoPointsDiscount(booking.getEcoPointsDiscount())
                .services(toAddonResponses(booking))
                .createdAt(booking.getCreatedAt())
                .build();
    }

    @Transactional
    public ResponseEntity<?> updateReservationStatusForManager(Long bookingId, BookingStatus newStatus, User manager) {
        Booking booking = bookingRepo.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking not found with ID: " + bookingId));
        Long bookingHotelId = booking.getRoom().getBranch().getHotel().getId();
        if (manager.getManagedHotel() == null || !manager.getManagedHotel().getId().equals(bookingHotelId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only update reservations for your assigned hotel.");
        }
        return updateReservationStatus(bookingId, newStatus, manager.getId());
    }

    private boolean isServiceAvailableAtBranch(com.project.Backend_BookMyHotel.domain.Service service,
                                               com.project.Backend_BookMyHotel.domain.Branch branch) {
        if (Boolean.FALSE.equals(service.getActive()) || branch == null || branch.getHotel() == null) return false;
        Long serviceHotelId = service.getHotel() != null
                ? service.getHotel().getId()
                : service.getBranch() != null ? service.getBranch().getHotel().getId() : null;
        if (!branch.getHotel().getId().equals(serviceHotelId)) return false;
        return service.getBranch() == null || service.getBranch().getId().equals(branch.getId());
    }

    private String roomCurrency(Room room) {
        return room.getCurrency() != null && !room.getCurrency().isBlank()
                ? room.getCurrency() : room.getBranch().getCurrency();
    }

    private String addonName(BookingAddonService addon) {
        if (addon.getServiceName() != null && !addon.getServiceName().isBlank()) return addon.getServiceName();
        return addon.getService() != null ? addon.getService().getName() : "Add-on service";
    }

    private BigDecimal addonUnitPrice(BookingAddonService addon) {
        if (addon.getUnitPrice() != null) return addon.getUnitPrice();
        return addon.getService() != null ? addon.getService().getPrice() : BigDecimal.ZERO;
    }

    private List<BookingResponse.AddonServiceResponse> toAddonResponses(Booking booking) {
        if (booking.getBookingServices() == null) return List.of();
        return booking.getBookingServices().stream()
                .map(addon -> BookingResponse.AddonServiceResponse.builder()
                        .id(addon.getId())
                        .serviceId(addon.getService() != null ? addon.getService().getId() : null)
                        .serviceName(addonName(addon))
                        .unitPrice(addonUnitPrice(addon))
                        .quantity(addon.getQuantity())
                        .subtotal(addon.getSubtotal())
                        .build())
                .toList();
    }

    private BigDecimal bookingServicesTotal(Booking booking) {
        if (booking.getBookingServices() == null) return BigDecimal.ZERO;
        return booking.getBookingServices().stream()
                .map(BookingAddonService::getSubtotal)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<EmailTemplateService.BookingServiceLine> toEmailServiceLines(Booking booking) {
        if (booking.getBookingServices() == null) return List.of();
        return booking.getBookingServices().stream()
                .map(addon -> new EmailTemplateService.BookingServiceLine(
                        addonName(addon), addonUnitPrice(addon), addon.getQuantity(), addon.getSubtotal()))
                .toList();
    }

}
