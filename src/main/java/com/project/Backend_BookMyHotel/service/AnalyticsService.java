package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.dto.AnalyticsSummaryResponse;
import com.project.Backend_BookMyHotel.dto.BookingResponse;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import com.project.Backend_BookMyHotel.dto.HotelAnalyticsResponse;
import com.project.Backend_BookMyHotel.repository.BookingRepository;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import com.project.Backend_BookMyHotel.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class AnalyticsService {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Cacheable(value = "branch-ratings", key = "#branchId")
    public BigDecimal getBranchAverageRating(Long branchId) {
        return computeAverage(branchId);
    }

    // Called right after a review is created or deleted so the cache reflects the new average
    // immediately. @CachePut (unlike @Cacheable) always runs the method body and overwrites
    // whatever was cached, rather than skipping on a hit.
    @CachePut(value = "branch-ratings", key = "#branchId")
    public BigDecimal updateBranchAverageRating(Long branchId) {
        return computeAverage(branchId);
    }

    private BigDecimal computeAverage(Long branchId) {
        Double average = reviewRepository.findAverageRatingByBranchId(branchId);
        return average != null
                ? BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    // Room-nights sold: for every CONFIRMED booking that checked in within [startDate, endDate],
    // the full length of that stay (checkOut - checkIn), summed. hotelId == null aggregates across
    // every hotel on the platform (used by the overall summary endpoint).
    @Transactional(readOnly = true)
    public long getRoomNightsBooked(Long hotelId, LocalDate startDate, LocalDate endDate) {
        return bookingRepository.findByHotelAndStatusAndCheckInBetween(hotelId, BookingStatus.CONFIRMED, startDate, endDate)
                .stream()
                .mapToLong(b -> ChronoUnit.DAYS.between(b.getCheckIn(), b.getCheckOut()))
                .sum();
    }

    // Sum of totalPrice across the same CONFIRMED, check-in-in-range booking set as above.
    // NOTE: summed as stored, in each booking's own branch currency — no conversion is applied.
    // A hotel chain (or hotelId == null, the platform-wide summary) can mix branches priced in
    // different currencies (GBP, USD, AED...), so this figure is only meaningful when every
    // booking in range shares one currency. Converting would need a point-in-time exchange rate
    // per booking, which is out of scope for this metric as specified.
    @Transactional(readOnly = true)
    public BigDecimal getRoomRevenue(Long hotelId, LocalDate startDate, LocalDate endDate) {
        return bookingRepository.findByHotelAndStatusAndCheckInBetween(hotelId, BookingStatus.CONFIRMED, startDate, endDate)
                .stream()
                .map(Booking::getTotalPrice)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional(readOnly = true)
    public BigDecimal getAverageDailyRate(Long hotelId, LocalDate startDate, LocalDate endDate) {
        long roomNights = getRoomNightsBooked(hotelId, startDate, endDate);
        BigDecimal revenue = getRoomRevenue(hotelId, startDate, endDate);
        return computeAdr(revenue, roomNights);
    }

    // All bookings (any status — this one isn't confirmed-only) actually occupying a room at this
    // hotel on the given date.
    @Transactional(readOnly = true)
    public List<BookingResponse> getBookingsByDate(Long hotelId, LocalDate date) {
        return bookingRepository.findByHotelIdAndDateInStay(hotelId, date).stream()
                .map(this::toBookingResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getOverallSummary(LocalDate startDate, LocalDate endDate) {
        LocalDate[] range = resolveDateRange(startDate, endDate);
        long roomNights = getRoomNightsBooked(null, range[0], range[1]);
        BigDecimal revenue = getRoomRevenue(null, range[0], range[1]);

        return ResponseEntity.ok(AnalyticsSummaryResponse.builder()
                .startDate(range[0])
                .endDate(range[1])
                .roomNightsBooked(roomNights)
                .revenue(revenue)
                .averageDailyRate(computeAdr(revenue, roomNights))
                .build());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getHotelAnalytics(Long hotelId, LocalDate startDate, LocalDate endDate) {
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new NoSuchElementException("Hotel not found with ID: " + hotelId));

        LocalDate[] range = resolveDateRange(startDate, endDate);
        long roomNights = getRoomNightsBooked(hotelId, range[0], range[1]);
        BigDecimal revenue = getRoomRevenue(hotelId, range[0], range[1]);

        return ResponseEntity.ok(HotelAnalyticsResponse.builder()
                .hotelId(hotel.getId())
                .hotelName(hotel.getName())
                .startDate(range[0])
                .endDate(range[1])
                .roomNightsBooked(roomNights)
                .revenue(revenue)
                .averageDailyRate(computeAdr(revenue, roomNights))
                .build());
    }

    private BigDecimal computeAdr(BigDecimal revenue, long roomNights) {
        return roomNights == 0 ? BigDecimal.ZERO : revenue.divide(BigDecimal.valueOf(roomNights), 2, RoundingMode.HALF_UP);
    }

    // No startDate/endDate given defaults to a trailing 30-day window ending today — a reasonable
    // "recent activity" default for a KPI dashboard opened with no filters set yet.
    private LocalDate[] resolveDateRange(LocalDate startDate, LocalDate endDate) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        LocalDate start = startDate != null ? startDate : end.minusDays(29);
        return new LocalDate[]{start, end};
    }

    private BookingResponse toBookingResponse(Booking booking) {
        return BookingResponse.builder()
                .id(booking.getId())
                .roomId(booking.getRoom() != null ? booking.getRoom().getId() : null)
                .userId(booking.getUser() != null ? booking.getUser().getId() : null)
                .checkIn(booking.getCheckIn())
                .checkOut(booking.getCheckOut())
                .status(booking.getStatus())
                .totalPrice(booking.getTotalPrice())
                .ecoPointsEarned(booking.getEcoPointsEarned())
                .createdAt(booking.getCreatedAt())
                .build();
    }
}
