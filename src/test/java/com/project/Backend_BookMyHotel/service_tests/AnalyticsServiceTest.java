package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.dto.BookingResponse;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import com.project.Backend_BookMyHotel.repository.BookingRepository;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import com.project.Backend_BookMyHotel.repository.ReviewRepository;
import com.project.Backend_BookMyHotel.service.AnalyticsService;
import com.project.Backend_BookMyHotel.service.ExchangeRateService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class AnalyticsServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private HotelRepository hotelRepository;

    @Mock
    private ExchangeRateService exchangeRateService;

    @InjectMocks
    private AnalyticsService analyticsService;

    private Hotel hotel;
    private Booking confirmedBooking;

    @BeforeEach
    void setUp() {
        hotel = new Hotel();
        hotel.setId(1000L);
        hotel.setName("Grand Hotel");

        Branch branch = new Branch();
        branch.setId(10L);
        branch.setHotel(hotel);
        branch.setCurrency("GBP");

        Room room = new Room();
        room.setId(100L);
        room.setBranch(branch);

        confirmedBooking = Booking.builder()
                .id(50L)
                .room(room)
                .checkIn(LocalDate.of(2026, 1, 5))
                .checkOut(LocalDate.of(2026, 1, 8)) // 3 nights
                .status(BookingStatus.CONFIRMED)
                .totalPrice(BigDecimal.valueOf(300))
                .build();

        // Keep these unit tests deterministic: treat the sample GBP amount as an equivalent
        // USD amount while testing analytics arithmetic, not the external exchange-rate logic.
        Mockito.lenient().when(exchangeRateService.convert(
                        Mockito.any(BigDecimal.class), Mockito.eq("GBP"), Mockito.eq("USD")))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void getBranchAverageRating_RoundsToTwoDecimalPlaces() {
        Mockito.when(reviewRepository.findAverageRatingByBranchId(10L)).thenReturn(4.3333333);

        BigDecimal average = analyticsService.getBranchAverageRating(10L);

        Assertions.assertEquals(0, BigDecimal.valueOf(4.33).compareTo(average));
    }

    @Test
    void getBranchAverageRating_WhenNoReviews_ReturnsZeroNotNull() {
        Mockito.when(reviewRepository.findAverageRatingByBranchId(10L)).thenReturn(null);

        BigDecimal average = analyticsService.getBranchAverageRating(10L);

        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(average));
    }

    @Test
    void updateBranchAverageRating_RecomputesFromRepository() {
        Mockito.when(reviewRepository.findAverageRatingByBranchId(10L)).thenReturn(5.0);

        BigDecimal average = analyticsService.updateBranchAverageRating(10L);

        Assertions.assertEquals(0, BigDecimal.valueOf(5.00).compareTo(average));
    }

    @Test
    void getRoomNightsBooked_SumsNightsAcrossConfirmedBookingsInRange() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        Mockito.when(bookingRepository.findByHotelAndStatusAndCheckInBetween(1000L, BookingStatus.CONFIRMED, start, end))
                .thenReturn(List.of(confirmedBooking));

        long nights = analyticsService.getRoomNightsBooked(1000L, start, end);

        Assertions.assertEquals(3, nights);
    }

    @Test
    void getRoomRevenue_SumsTotalPriceAcrossConfirmedBookingsInRange() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        Mockito.when(bookingRepository.findByHotelAndStatusAndCheckInBetween(1000L, BookingStatus.CONFIRMED, start, end))
                .thenReturn(List.of(confirmedBooking));

        BigDecimal revenue = analyticsService.getRoomRevenue(1000L, start, end);

        Assertions.assertEquals(0, BigDecimal.valueOf(300).compareTo(revenue));
    }

    @Test
    void getAverageDailyRate_IsRevenueDividedByRoomNights() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        Mockito.when(bookingRepository.findByHotelAndStatusAndCheckInBetween(1000L, BookingStatus.CONFIRMED, start, end))
                .thenReturn(List.of(confirmedBooking));

        BigDecimal adr = analyticsService.getAverageDailyRate(1000L, start, end);

        // 300 revenue / 3 nights = 100.00
        Assertions.assertEquals(0, BigDecimal.valueOf(100).compareTo(adr));
    }

    @Test
    void getAverageDailyRate_WhenNoRoomNights_ReturnsZeroInsteadOfDividingByZero() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        Mockito.when(bookingRepository.findByHotelAndStatusAndCheckInBetween(1000L, BookingStatus.CONFIRMED, start, end))
                .thenReturn(List.of());

        BigDecimal adr = analyticsService.getAverageDailyRate(1000L, start, end);

        Assertions.assertEquals(0, BigDecimal.ZERO.compareTo(adr));
    }

    @Test
    void getBookingsByDate_MapsBookingsOccupyingThatDateToBookingResponses() {
        LocalDate date = LocalDate.of(2026, 1, 6);
        Mockito.when(bookingRepository.findByHotelIdAndDateInStay(1000L, date))
                .thenReturn(List.of(confirmedBooking));

        List<BookingResponse> result = analyticsService.getBookingsByDate(1000L, date);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(50L, result.get(0).getId());
    }

    @Test
    void getHotelAnalytics_WhenHotelMissing_ThrowsNoSuchElementException() {
        Mockito.when(hotelRepository.findById(9999L)).thenReturn(Optional.empty());

        Assertions.assertThrows(java.util.NoSuchElementException.class,
                () -> analyticsService.getHotelAnalytics(9999L, null, null));
    }

    @Test
    void getHotelAnalytics_WhenDatesOmitted_DefaultsToTrailing30DayWindow() {
        Mockito.when(hotelRepository.findById(1000L)).thenReturn(Optional.of(hotel));
        Mockito.when(bookingRepository.findByHotelAndStatusAndCheckInBetween(
                        Mockito.eq(1000L), Mockito.eq(BookingStatus.CONFIRMED), Mockito.any(), Mockito.any()))
                .thenReturn(List.of());

        ResponseEntity<?> response = analyticsService.getHotelAnalytics(1000L, null, null);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        com.project.Backend_BookMyHotel.dto.HotelAnalyticsResponse body =
                (com.project.Backend_BookMyHotel.dto.HotelAnalyticsResponse) response.getBody();
        Assertions.assertNotNull(body);
        Assertions.assertEquals(LocalDate.now(), body.getEndDate());
        Assertions.assertEquals(LocalDate.now().minusDays(29), body.getStartDate());
        Assertions.assertEquals("Grand Hotel", body.getHotelName());
    }

    @Test
    void getOverallSummary_PassesNullHotelIdToAggregateAcrossAllHotels() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);
        Mockito.when(bookingRepository.findByHotelAndStatusAndCheckInBetween(null, BookingStatus.CONFIRMED, start, end))
                .thenReturn(List.of(confirmedBooking));

        ResponseEntity<?> response = analyticsService.getOverallSummary(start, end);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        com.project.Backend_BookMyHotel.dto.AnalyticsSummaryResponse body =
                (com.project.Backend_BookMyHotel.dto.AnalyticsSummaryResponse) response.getBody();
        Assertions.assertNotNull(body);
        Assertions.assertEquals(3, body.getRoomNightsBooked());
        Assertions.assertEquals(0, BigDecimal.valueOf(300).compareTo(body.getRevenue()));
    }
}
