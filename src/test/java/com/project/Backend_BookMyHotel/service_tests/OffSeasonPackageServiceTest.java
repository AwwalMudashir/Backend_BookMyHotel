package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.*;
import com.project.Backend_BookMyHotel.dto.*;
import com.project.Backend_BookMyHotel.repository.*;
import com.project.Backend_BookMyHotel.service.ExchangeRateService;
import com.project.Backend_BookMyHotel.service.OffSeasonPackageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OffSeasonPackageServiceTest {
    @Mock private OffSeasonPackageRepository packageRepository;
    @Mock private HotelRepository hotelRepository;
    @Mock private BranchRepository branchRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private ExchangeRateService exchangeRateService;

    private OffSeasonPackageService service;
    private Hotel hotel;
    private Branch branch;
    private Room room;

    @BeforeEach
    void setUp() {
        service = new OffSeasonPackageService(packageRepository, hotelRepository, branchRepository,
                roomRepository, exchangeRateService);
        hotel = new Hotel();
        hotel.setId(10L);
        hotel.setName("Hilton Dubai");
        branch = new Branch();
        branch.setId(20L);
        branch.setHotel(hotel);
        branch.setName("Hilton Dubai Marina");
        room = new Room();
        room.setId(30L);
        room.setBranch(branch);
        room.setRoomType("Deluxe");
        room.setCurrency("USD");
        lenient().when(exchangeRateService.requireSupportedCurrency(any())).thenAnswer(invocation -> invocation.getArgument(0).toString().toUpperCase());
        lenient().when(exchangeRateService.convert(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void percentagePackageProducesExpectedDiscountForEligibleOffSeasonStay() {
        OffSeasonPackage offer = validPackage();
        when(packageRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(roomRepository.findById(30L)).thenReturn(Optional.of(room));

        OffSeasonPackageQuoteResponse result = service.quote(new OffSeasonPackageQuoteRequest(
                1L, 30L, LocalDate.now().plusDays(20), LocalDate.now().plusDays(23),
                new BigDecimal("600.00"), "USD"));

        assertTrue(result.getEligible());
        assertEquals(new BigDecimal("120.00"), result.getDiscountAmount());
        assertEquals(new BigDecimal("480.00"), result.getRoomPriceAfterDiscount());
        System.out.println("PACKAGE QUOTE PASS: 20% of USD 600.00 = USD 120.00; final room price = USD 480.00");
    }

    @Test
    void stayOutsidePackageWindowIsRejectedWithClearReason() {
        OffSeasonPackage offer = validPackage();
        when(packageRepository.findById(1L)).thenReturn(Optional.of(offer));
        when(roomRepository.findById(30L)).thenReturn(Optional.of(room));

        OffSeasonPackageQuoteResponse result = service.quote(new OffSeasonPackageQuoteRequest(
                1L, 30L, offer.getStayEndDate(), offer.getStayEndDate().plusDays(2),
                new BigDecimal("600.00"), "USD"));

        assertFalse(result.getEligible());
        assertTrue(result.getMessage().contains("complete stay"));
        System.out.println("PACKAGE DATE VALIDATION PASS: a stay extending beyond the off-season window was rejected.");
    }

    @Test
    void hotelManagerCannotCreatePlatformWidePackage() {
        User manager = new User();
        manager.setRole(Role.HOTEL_MANAGER);
        manager.setManagedHotel(hotel);
        OffSeasonPackageRequest request = request(PackageScope.GLOBAL, null, null);
        when(packageRepository.findByCodeIgnoreCase("QUIET20")).thenReturn(Optional.empty());

        AccessDeniedException error = assertThrows(AccessDeniedException.class, () -> service.create(request, manager));
        assertTrue(error.getMessage().contains("Only administrators"));
        verify(packageRepository, never()).save(any());
        System.out.println("PACKAGE RBAC PASS: hotel manager was blocked from creating a platform-wide package.");
    }

    @Test
    void hotelManagerCanCreatePackageForOwnHotel() {
        User manager = new User();
        manager.setId(7L);
        manager.setRole(Role.HOTEL_MANAGER);
        manager.setManagedHotel(hotel);
        OffSeasonPackageRequest request = request(PackageScope.HOTEL, hotel.getId(), null);
        when(packageRepository.findByCodeIgnoreCase("QUIET20")).thenReturn(Optional.empty());
        when(hotelRepository.findById(hotel.getId())).thenReturn(Optional.of(hotel));
        when(packageRepository.save(any())).thenAnswer(invocation -> {
            OffSeasonPackage saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        OffSeasonPackageResponse result = service.create(request, manager);

        assertEquals(PackageScope.HOTEL, result.getScope());
        assertEquals(hotel.getId(), result.getHotelId());
        assertNull(result.getBranchId());
        System.out.println("PACKAGE OWNERSHIP PASS: manager created an all-branches package for hotel ID 10 only.");
    }

    @Test
    void reservingLastAvailablePackagePlacePreventsFurtherReservations() {
        OffSeasonPackage offer = validPackage();
        offer.setMaxBookings(1);
        offer.setTimesBooked(0);
        when(packageRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(offer));
        when(packageRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.reserveAndApply(1L, room, LocalDate.now().plusDays(20), LocalDate.now().plusDays(23),
                new BigDecimal("500.00"), "USD");
        assertEquals(1, offer.getTimesBooked());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                service.reserveAndApply(1L, room, LocalDate.now().plusDays(20), LocalDate.now().plusDays(23),
                        new BigDecimal("500.00"), "USD"));
        assertTrue(error.getMessage().contains("booking limit"));
        System.out.println("PACKAGE CAPACITY PASS: the final place was reserved and a second reservation was rejected.");
    }

    private OffSeasonPackage validPackage() {
        return OffSeasonPackage.builder()
                .id(1L).scope(PackageScope.HOTEL).hotel(hotel).code("QUIET20")
                .name("Quiet Season Escape").summary("Save during quieter dates")
                .discountType(DiscountType.PERCENTAGE).discountValue(new BigDecimal("20.00"))
                .discountCurrency("USD").bookingStartDate(LocalDate.now().minusDays(1))
                .bookingEndDate(LocalDate.now().plusDays(10)).stayStartDate(LocalDate.now().plusDays(15))
                .stayEndDate(LocalDate.now().plusDays(60)).minimumNights(2).minimumAdvanceDays(0)
                .eligibleRoomTypes(List.of("Deluxe")).inclusions(List.of("Breakfast"))
                .timesBooked(0).active(true).featured(true).build();
    }

    private OffSeasonPackageRequest request(PackageScope scope, Long hotelId, Long branchId) {
        return new OffSeasonPackageRequest(scope, hotelId, branchId, "QUIET20", "Quiet Season Escape",
                "Save during quieter travel dates", "A complete off-season package", List.of("Breakfast"),
                List.of("Deluxe"), "Subject to availability", null, DiscountType.PERCENTAGE,
                new BigDecimal("20.00"), "USD", null, null, LocalDate.now(), LocalDate.now().plusDays(10),
                LocalDate.now().plusDays(15), LocalDate.now().plusDays(60), 2, 7, 0, 20, true);
    }
}
