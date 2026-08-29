package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.OffSeasonPackage;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.*;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import com.project.Backend_BookMyHotel.repository.OffSeasonPackageRepository;
import com.project.Backend_BookMyHotel.repository.RoomRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;

@Service
public class OffSeasonPackageService {
    private final OffSeasonPackageRepository packageRepository;
    private final HotelRepository hotelRepository;
    private final BranchRepository branchRepository;
    private final RoomRepository roomRepository;
    private final ExchangeRateService exchangeRateService;

    public OffSeasonPackageService(OffSeasonPackageRepository packageRepository,
                                   HotelRepository hotelRepository,
                                   BranchRepository branchRepository,
                                   RoomRepository roomRepository,
                                   ExchangeRateService exchangeRateService) {
        this.packageRepository = packageRepository;
        this.hotelRepository = hotelRepository;
        this.branchRepository = branchRepository;
        this.roomRepository = roomRepository;
        this.exchangeRateService = exchangeRateService;
    }

    @Transactional(readOnly = true)
    public List<OffSeasonPackageResponse> getForManagement(Long requestedHotelId, User actor) {
        if (actor.isAdmin()) {
            List<OffSeasonPackage> packages = requestedHotelId == null
                    ? packageRepository.findAllByOrderByCreatedAtDesc()
                    : packageRepository.findByHotelIdOrderByCreatedAtDesc(requestedHotelId);
            return packages.stream().map(this::toResponse).toList();
        }

        Long hotelId = managerHotelId(actor);
        if (requestedHotelId != null && !requestedHotelId.equals(hotelId)) {
            throw new AccessDeniedException("You can only manage packages for your assigned hotel.");
        }
        return packageRepository.findByHotelIdOrderByCreatedAtDesc(hotelId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OffSeasonPackageResponse> getPublicActivePackages() {
        LocalDate today = LocalDate.now();
        return packageRepository
                .findByActiveTrueAndBookingEndDateGreaterThanEqualOrderByFeaturedDescStayStartDateAsc(today)
                .stream()
                .filter(item -> !today.isBefore(item.getBookingStartDate()))
                .filter(item -> !item.getStayEndDate().isBefore(today))
                .filter(this::hasRemainingCapacity)
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OffSeasonPackageResponse> getPublicFeaturedPackages() {
        return getPublicActivePackages().stream()
                .filter(item -> Boolean.TRUE.equals(item.getFeatured()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OffSeasonPackageResponse> getPackagesForRoom(Long roomId) {
        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NoSuchElementException("Room not found with ID: " + roomId));
        return getPublicActivePackages().stream()
                .filter(item -> scopeMatches(item, room))
                .filter(item -> roomTypeMatches(item.getEligibleRoomTypes(), room.getRoomType()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OffSeasonPackageQuoteResponse quote(OffSeasonPackageQuoteRequest request) {
        OffSeasonPackage packageOffer = packageRepository.findById(request.packageId())
                .orElseThrow(() -> new NoSuchElementException("Off-season package not found with ID: " + request.packageId()));
        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new NoSuchElementException("Room not found with ID: " + request.roomId()));
        QuoteResult result = calculate(packageOffer, room, request.checkIn(), request.checkOut(),
                request.roomSubtotal(), request.currency());
        return toQuoteResponse(packageOffer, result, request.roomSubtotal(), request.currency());
    }

    @Transactional
    public OffSeasonPackageResponse create(OffSeasonPackageRequest request, User actor) {
        if (packageRepository.findByCodeIgnoreCase(normalizeCode(request.code())).isPresent()) {
            throw new IllegalArgumentException("A package with code " + normalizeCode(request.code()) + " already exists.");
        }
        validateRequest(request);
        Scope scope = resolveScope(request, actor);
        OffSeasonPackage packageOffer = new OffSeasonPackage();
        apply(packageOffer, request, scope);
        packageOffer.setCreatedBy(actor);
        packageOffer.setActive(true);
        packageOffer.setTimesBooked(0);
        return toResponse(packageRepository.save(packageOffer));
    }

    @Transactional
    public OffSeasonPackageResponse update(Long packageId, OffSeasonPackageRequest request, User actor) {
        OffSeasonPackage packageOffer = packageRepository.findById(packageId)
                .orElseThrow(() -> new NoSuchElementException("Off-season package not found with ID: " + packageId));
        requireAccess(actor, packageOffer);
        packageRepository.findByCodeIgnoreCase(normalizeCode(request.code()))
                .filter(other -> !other.getId().equals(packageId))
                .ifPresent(other -> { throw new IllegalArgumentException("A package with this code already exists."); });
        validateRequest(request);
        Scope scope = resolveScope(request, actor);
        apply(packageOffer, request, scope);
        return toResponse(packageRepository.save(packageOffer));
    }

    @Transactional
    public OffSeasonPackageResponse setActive(Long packageId, boolean active, User actor) {
        OffSeasonPackage packageOffer = packageRepository.findById(packageId)
                .orElseThrow(() -> new NoSuchElementException("Off-season package not found with ID: " + packageId));
        requireAccess(actor, packageOffer);
        packageOffer.setActive(active);
        return toResponse(packageRepository.save(packageOffer));
    }

    @Transactional
    public PackageApplication reserveAndApply(Long packageId, Room room, LocalDate checkIn, LocalDate checkOut,
                                              BigDecimal roomSubtotal, String bookingCurrency) {
        OffSeasonPackage packageOffer = packageRepository.findByIdForUpdate(packageId)
                .orElseThrow(() -> new NoSuchElementException("Off-season package not found with ID: " + packageId));
        QuoteResult result = calculate(packageOffer, room, checkIn, checkOut, roomSubtotal, bookingCurrency);
        if (!result.eligible()) {
            throw new IllegalArgumentException(result.message());
        }
        packageOffer.setTimesBooked(safeTimesBooked(packageOffer) + 1);
        packageRepository.save(packageOffer);
        return new PackageApplication(packageOffer, result.discountAmount(), result.finalPrice());
    }

    @Transactional(readOnly = true)
    public PackageApplication previewApplication(Long packageId, Room room, LocalDate checkIn, LocalDate checkOut,
                                                 BigDecimal roomSubtotal, String bookingCurrency) {
        OffSeasonPackage packageOffer = packageRepository.findById(packageId)
                .orElseThrow(() -> new NoSuchElementException("Off-season package not found with ID: " + packageId));
        QuoteResult result = calculate(packageOffer, room, checkIn, checkOut, roomSubtotal, bookingCurrency);
        if (!result.eligible()) throw new IllegalArgumentException(result.message());
        return new PackageApplication(packageOffer, result.discountAmount(), result.finalPrice());
    }

    @Transactional
    public void releaseReservation(Long packageId) {
        if (packageId == null) return;
        packageRepository.findByIdForUpdate(packageId).ifPresent(packageOffer -> {
            packageOffer.setTimesBooked(Math.max(0, safeTimesBooked(packageOffer) - 1));
            packageRepository.save(packageOffer);
        });
    }

    private QuoteResult calculate(OffSeasonPackage packageOffer, Room room, LocalDate checkIn, LocalDate checkOut,
                                  BigDecimal roomSubtotal, String requestedCurrency) {
        String bookingCurrency = exchangeRateService.requireSupportedCurrency(requestedCurrency);
        BigDecimal safeSubtotal = roomSubtotal == null ? BigDecimal.ZERO : roomSubtotal;
        LocalDate today = LocalDate.now();

        if (!Boolean.TRUE.equals(packageOffer.getActive())) return invalid("This package is inactive.");
        if (today.isBefore(packageOffer.getBookingStartDate())) return invalid("Bookings for this package have not opened yet.");
        if (today.isAfter(packageOffer.getBookingEndDate())) return invalid("The booking window for this package has closed.");
        if (!hasRemainingCapacity(packageOffer)) return invalid("This package has reached its booking limit.");
        if (!scopeMatches(packageOffer, room)) return invalid("This package is not available at the selected hotel branch.");
        if (!roomTypeMatches(packageOffer.getEligibleRoomTypes(), room.getRoomType())) {
            return invalid("This package is not available for this room type.");
        }
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            return invalid("Select a valid check-in and check-out date to use this package.");
        }
        if (checkIn.isBefore(packageOffer.getStayStartDate()) || checkOut.isAfter(packageOffer.getStayEndDate())) {
            return invalid("Your complete stay must fall between " + packageOffer.getStayStartDate()
                    + " and " + packageOffer.getStayEndDate() + ".");
        }

        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights < packageOffer.getMinimumNights()) {
            return invalid("This package requires a minimum stay of " + packageOffer.getMinimumNights() + " nights.");
        }
        if (packageOffer.getMaximumNights() != null && nights > packageOffer.getMaximumNights()) {
            return invalid("This package is limited to a maximum stay of " + packageOffer.getMaximumNights() + " nights.");
        }
        long advanceDays = ChronoUnit.DAYS.between(today, checkIn);
        if (advanceDays < packageOffer.getMinimumAdvanceDays()) {
            return invalid("This package must be booked at least " + packageOffer.getMinimumAdvanceDays() + " days before check-in.");
        }

        if (packageOffer.getMinimumRoomSubtotal() != null) {
            BigDecimal minimum = exchangeRateService.convert(packageOffer.getMinimumRoomSubtotal(),
                    packageOffer.getDiscountCurrency(), bookingCurrency);
            if (safeSubtotal.compareTo(minimum) < 0) {
                return invalid("The room subtotal does not meet this package's minimum spend.");
            }
        }

        BigDecimal discount;
        if (packageOffer.getDiscountType() == DiscountType.PERCENTAGE) {
            discount = safeSubtotal.multiply(packageOffer.getDiscountValue())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
        } else {
            discount = exchangeRateService.convert(packageOffer.getDiscountValue(),
                    packageOffer.getDiscountCurrency(), bookingCurrency);
        }
        if (packageOffer.getMaxDiscountAmount() != null) {
            BigDecimal cap = exchangeRateService.convert(packageOffer.getMaxDiscountAmount(),
                    packageOffer.getDiscountCurrency(), bookingCurrency);
            discount = discount.min(cap);
        }
        discount = discount.min(safeSubtotal).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return new QuoteResult(true, "Package applied successfully.", discount,
                safeSubtotal.subtract(discount).setScale(2, RoundingMode.HALF_UP));
    }

    private void validateRequest(OffSeasonPackageRequest request) {
        if (request.bookingEndDate().isBefore(request.bookingStartDate())) {
            throw new IllegalArgumentException("Booking end date cannot be before the booking start date.");
        }
        if (!request.stayEndDate().isAfter(request.stayStartDate())) {
            throw new IllegalArgumentException("Stay end date must be after the stay start date.");
        }
        if (request.maximumNights() != null && request.maximumNights() < request.minimumNights()) {
            throw new IllegalArgumentException("Maximum nights cannot be less than minimum nights.");
        }
        if (request.discountType() == DiscountType.PERCENTAGE
                && request.discountValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("Percentage discounts cannot exceed 100%.");
        }
        exchangeRateService.requireSupportedCurrency(request.discountCurrency());
        if (request.imageUrl() != null && !request.imageUrl().isBlank()) {
            String imageUrl = request.imageUrl().trim();
            if (!imageUrl.startsWith("/") && !isHttpUrl(imageUrl)) {
                throw new IllegalArgumentException("Image URL must be an http(s) URL or an application-relative path.");
            }
        }
    }

    private boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private Scope resolveScope(OffSeasonPackageRequest request, User actor) {
        if (request.scope() == PackageScope.GLOBAL) {
            if (!actor.isAdmin()) {
                throw new AccessDeniedException("Only administrators can create a package for every hotel.");
            }
            return new Scope(null, null);
        }

        Long hotelId = actor.isAdmin() ? request.hotelId() : managerHotelId(actor);
        if (hotelId == null) throw new IllegalArgumentException("Hotel ID is required for this package scope.");
        if (!actor.isAdmin() && request.hotelId() != null && !request.hotelId().equals(hotelId)) {
            throw new AccessDeniedException("You can only create packages for your assigned hotel.");
        }
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new NoSuchElementException("Hotel not found with ID: " + hotelId));
        if (request.scope() == PackageScope.HOTEL) return new Scope(hotel, null);

        if (request.branchId() == null) throw new IllegalArgumentException("Branch ID is required for a branch package.");
        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> new NoSuchElementException("Branch not found with ID: " + request.branchId()));
        if (!branch.getHotel().getId().equals(hotelId)) {
            throw new IllegalArgumentException("The selected branch does not belong to the selected hotel.");
        }
        return new Scope(hotel, branch);
    }

    private void apply(OffSeasonPackage target, OffSeasonPackageRequest request, Scope scope) {
        target.setScope(request.scope());
        target.setHotel(scope.hotel());
        target.setBranch(scope.branch());
        target.setCode(normalizeCode(request.code()));
        target.setName(request.name().trim());
        target.setSummary(request.summary().trim());
        target.setDescription(blankToNull(request.description()));
        target.setInclusions(cleanList(request.inclusions()));
        target.setEligibleRoomTypes(cleanList(request.eligibleRoomTypes()));
        target.setTermsAndConditions(blankToNull(request.termsAndConditions()));
        target.setImageUrl(blankToNull(request.imageUrl()));
        target.setDiscountType(request.discountType());
        target.setDiscountValue(request.discountValue().setScale(2, RoundingMode.HALF_UP));
        target.setDiscountCurrency(request.discountCurrency().trim().toUpperCase(Locale.ROOT));
        target.setMaxDiscountAmount(request.maxDiscountAmount());
        target.setMinimumRoomSubtotal(request.minimumRoomSubtotal());
        target.setBookingStartDate(request.bookingStartDate());
        target.setBookingEndDate(request.bookingEndDate());
        target.setStayStartDate(request.stayStartDate());
        target.setStayEndDate(request.stayEndDate());
        target.setMinimumNights(request.minimumNights());
        target.setMaximumNights(request.maximumNights());
        target.setMinimumAdvanceDays(request.minimumAdvanceDays());
        target.setMaxBookings(request.maxBookings());
        target.setFeatured(Boolean.TRUE.equals(request.featured()));
    }

    private void requireAccess(User actor, OffSeasonPackage packageOffer) {
        if (actor.isAdmin()) return;
        if (packageOffer.getScope() == PackageScope.GLOBAL || packageOffer.getHotel() == null
                || actor.getManagedHotel() == null
                || !actor.getManagedHotel().getId().equals(packageOffer.getHotel().getId())) {
            throw new AccessDeniedException("You can only manage packages for your assigned hotel.");
        }
    }

    private Long managerHotelId(User actor) {
        if (actor.getManagedHotel() == null) {
            throw new IllegalStateException("Your manager account is not assigned to a hotel.");
        }
        return actor.getManagedHotel().getId();
    }

    private boolean scopeMatches(OffSeasonPackageResponse item, Room room) {
        if (item.getScope() == PackageScope.GLOBAL) return true;
        Long roomHotelId = room.getBranch().getHotel().getId();
        if (!roomHotelId.equals(item.getHotelId())) return false;
        return item.getScope() == PackageScope.HOTEL || room.getBranch().getId().equals(item.getBranchId());
    }

    private boolean scopeMatches(OffSeasonPackage item, Room room) {
        if (item.getScope() == PackageScope.GLOBAL) return true;
        Long roomHotelId = room.getBranch().getHotel().getId();
        if (item.getHotel() == null || !roomHotelId.equals(item.getHotel().getId())) return false;
        return item.getScope() == PackageScope.HOTEL
                || (item.getBranch() != null && room.getBranch().getId().equals(item.getBranch().getId()));
    }

    private boolean roomTypeMatches(List<String> eligibleRoomTypes, String roomType) {
        if (eligibleRoomTypes == null || eligibleRoomTypes.isEmpty()) return true;
        return roomType != null && eligibleRoomTypes.stream().anyMatch(type -> type.equalsIgnoreCase(roomType.trim()));
    }

    private boolean hasRemainingCapacity(OffSeasonPackage item) {
        return item.getMaxBookings() == null || safeTimesBooked(item) < item.getMaxBookings();
    }

    private int safeTimesBooked(OffSeasonPackage item) {
        return item.getTimesBooked() == null ? 0 : item.getTimesBooked();
    }

    private OffSeasonPackageResponse toResponse(OffSeasonPackage item) {
        Integer remaining = item.getMaxBookings() == null ? null
                : Math.max(0, item.getMaxBookings() - safeTimesBooked(item));
        String fallbackImage = item.getHotel() != null ? item.getHotel().getLongImage() : null;
        String creatorName = item.getCreatedBy() == null ? null
                : ((safe(item.getCreatedBy().getFirstName()) + " " + safe(item.getCreatedBy().getLastName())).trim());
        return OffSeasonPackageResponse.builder()
                .id(item.getId()).scope(item.getScope())
                .hotelId(item.getHotel() == null ? null : item.getHotel().getId())
                .hotelName(item.getHotel() == null ? "All hotels" : item.getHotel().getName())
                .branchId(item.getBranch() == null ? null : item.getBranch().getId())
                .branchName(item.getBranch() == null ? null : item.getBranch().getName())
                .code(item.getCode()).name(item.getName()).summary(item.getSummary())
                .description(item.getDescription()).inclusions(item.getInclusions())
                .eligibleRoomTypes(item.getEligibleRoomTypes()).termsAndConditions(item.getTermsAndConditions())
                .imageUrl(item.getImageUrl() == null ? fallbackImage : item.getImageUrl())
                .discountType(item.getDiscountType()).discountValue(item.getDiscountValue())
                .discountCurrency(item.getDiscountCurrency()).maxDiscountAmount(item.getMaxDiscountAmount())
                .minimumRoomSubtotal(item.getMinimumRoomSubtotal())
                .bookingStartDate(item.getBookingStartDate()).bookingEndDate(item.getBookingEndDate())
                .stayStartDate(item.getStayStartDate()).stayEndDate(item.getStayEndDate())
                .minimumNights(item.getMinimumNights()).maximumNights(item.getMaximumNights())
                .minimumAdvanceDays(item.getMinimumAdvanceDays()).maxBookings(item.getMaxBookings())
                .timesBooked(safeTimesBooked(item)).remainingBookings(remaining)
                .featured(item.getFeatured()).active(item.getActive())
                .createdById(item.getCreatedBy() == null ? null : item.getCreatedBy().getId())
                .createdByName(creatorName).createdAt(item.getCreatedAt()).updatedAt(item.getUpdatedAt())
                .build();
    }

    private OffSeasonPackageQuoteResponse toQuoteResponse(OffSeasonPackage item, QuoteResult result,
                                                          BigDecimal original, String currency) {
        return OffSeasonPackageQuoteResponse.builder()
                .packageId(item.getId()).packageCode(item.getCode()).packageName(item.getName())
                .eligible(result.eligible()).message(result.message())
                .originalRoomPrice(original.setScale(2, RoundingMode.HALF_UP))
                .discountAmount(result.discountAmount()).roomPriceAfterDiscount(result.finalPrice())
                .currency(currency.toUpperCase(Locale.ROOT)).build();
    }

    private QuoteResult invalid(String message) {
        return new QuoteResult(false, message, BigDecimal.ZERO, null);
    }

    private String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) return new ArrayList<>();
        LinkedHashSet<String> cleaned = new LinkedHashSet<>();
        values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim).forEach(cleaned::add);
        return new ArrayList<>(cleaned);
    }

    private String safe(String value) { return value == null ? "" : value; }

    private record Scope(Hotel hotel, Branch branch) {}
    private record QuoteResult(boolean eligible, String message, BigDecimal discountAmount, BigDecimal finalPrice) {}
    public record PackageApplication(OffSeasonPackage packageOffer, BigDecimal discountAmount, BigDecimal finalPrice) {}
}
