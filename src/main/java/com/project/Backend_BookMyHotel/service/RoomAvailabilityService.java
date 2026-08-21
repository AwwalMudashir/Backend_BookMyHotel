package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.domain.RoomAvailability;
import com.project.Backend_BookMyHotel.dto.AvailabilityCalendar;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import com.project.Backend_BookMyHotel.dto.RoomPriceResponse;
import com.project.Backend_BookMyHotel.dto.SetRoomAvailabilityRequest;
import com.project.Backend_BookMyHotel.repository.BookingRepository;
import com.project.Backend_BookMyHotel.repository.RoomAvailabilityRepository;
import com.project.Backend_BookMyHotel.repository.RoomRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RoomAvailabilityService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private RoomAvailabilityRepository availabilityRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Transactional(readOnly = true)
    public AvailabilityCalendar generateAvailabilityCalendar(Long roomId, LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must be on or after startDate.");
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("Room not found with id: " + roomId));
        String currency = roomCurrency(room);

        // 1. Fetch custom price or maintenance overrides from DB
        List<RoomAvailability> overrides = availabilityRepository
                .findByRoomIdAndDateBetween(roomId, startDate, endDate);

        Map<LocalDate, RoomAvailability> overrideMap = overrides.stream()
                .collect(Collectors.toMap(RoomAvailability::getDate, Function.identity(), (a, b) -> a));

        // 2. Fetch active bookings for this date window. Include both CONFIRMED and PENDING
        // so the calendar immediately reflects bookings that have been created but not yet
        // confirmed/paid, preventing a user from seeing a date as selectable while it is
        // reserved by someone else.
        List<Booking> confirmedBookings = bookingRepository
                .findOverlappingBookings(roomId, BookingStatus.CONFIRMED, startDate, endDate);
        List<Booking> pendingBookings = bookingRepository
                .findOverlappingBookings(roomId, BookingStatus.PENDING, startDate, endDate);
        // Combine into a single list used for availability checks
        List<Booking> overlappingBookings = new ArrayList<>();
        overlappingBookings.addAll(confirmedBookings);
        overlappingBookings.addAll(pendingBookings);

        List<AvailabilityCalendar.DailyAvailability> availabilityDays = new ArrayList<>();

        // 3. Evaluate each date in the window
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            final LocalDate date = current;

            // Check if date falls inside any active booking (confirmed or pending)
            boolean isBooked = overlappingBookings.stream().anyMatch(b ->
                    !date.isBefore(b.getCheckIn()) && date.isBefore(b.getCheckOut()));

            boolean isAvailable = !isBooked;
            BigDecimal dailyRate = room.getPricePerNight();

            // Apply custom override if present
            if (overrideMap.containsKey(date)) {
                RoomAvailability override = overrideMap.get(date);
                if (Boolean.FALSE.equals(override.getIsAvailable())) {
                    isAvailable = false; // E.g., room blocked for maintenance
                }
                if (override.getDailyRate() != null) {
                    dailyRate = override.getDailyRate(); // Custom holiday price
                }
            }

            availabilityDays.add(AvailabilityCalendar.DailyAvailability.builder()
                    .date(date)
                    .isAvailable(isAvailable)
                    .dailyRate(dailyRate)
                    .currency(currency)
                    .build());

            current = current.plusDays(1);
        }

        return AvailabilityCalendar.builder()
                .roomId(roomId)
                .days(availabilityDays)
                .build();
    }

    /**
     * Lets a hotel manager manually override the rate for a specific date.
     * e.g. £220 on a bank holiday instead of the standard £180.
     */
    @Transactional
    public void updateDailyRate(Long roomId, LocalDate date, BigDecimal newRate, String reason) {
        RoomAvailability ra = availabilityRepository
                .findByRoomIdAndDate(roomId, date)
                .orElseGet(() -> {
                    // Create it first if it doesn't exist
                    Room room = roomRepository.findById(roomId)
                            .orElseThrow(() -> new NoSuchElementException("Room not found"));
                    RoomAvailability newRa = new RoomAvailability();
                    newRa.setRoom(room);
                    newRa.setReason(reason);
                    newRa.setDate(date);
                    newRa.setIsAvailable(true);
                    newRa.setDailyRate(room.getPricePerNight());
                    return newRa;
                });

        ra.setDailyRate(newRate);
        availabilityRepository.save(ra);
    }

    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public void setRoomAvailability(Long roomId, SetRoomAvailabilityRequest request) {
        if (request.endDate().isBefore(request.startDate())) {
            throw new IllegalArgumentException("End date cannot be before start date.");
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new NoSuchElementException("Room not found with ID: " + roomId));

        List<RoomAvailability> recordsToSave = new ArrayList<>();
        LocalDate current = request.startDate();

        // Loop through each date in the manager's requested range (inclusive)
        while (!current.isAfter(request.endDate())) {
            LocalDate dateToUpdate = current;

            RoomAvailability availability = availabilityRepository
                    .findByRoomIdAndDate(roomId, dateToUpdate)
                    .orElseGet(() -> {
                        RoomAvailability ra = new RoomAvailability();
                        ra.setRoom(room);
                        ra.setDate(dateToUpdate);
                        return ra;
                    });

            if (request.isAvailable() != null) {
                availability.setIsAvailable(request.isAvailable());
            }
            if (request.customPrice() != null) {
                availability.setDailyRate(request.customPrice());
            }

            if (request.reason() != null) {
                availability.setReason(request.reason());
            }

            recordsToSave.add(availability);
            current = current.plusDays(1);
        }

        availabilityRepository.saveAll(recordsToSave);
    }

    @Transactional(readOnly = true)
    public RoomPriceResponse calculateTotalPrice(Long roomId, LocalDate checkIn, LocalDate checkOut) {
        return calculateTotalPrice(roomId, checkIn, checkOut, null);
    }

    @Transactional(readOnly = true)
    public RoomPriceResponse calculateTotalPrice(Long roomId, LocalDate checkIn, LocalDate checkOut,
                                                 String targetCurrency) {
        // 1. Validation
        if (checkIn == null || checkOut == null || !checkOut.isAfter(checkIn)) {
            throw new IllegalArgumentException("checkOut date must be strictly after checkIn date or checkOut and checkIn cannot be null.");
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new EntityNotFoundException("Room not found with id: " + roomId));
        String currency = roomCurrency(room);

        LocalDate lastNight = checkOut.minusDays(1);

        // 2. Load custom price overrides for the nights stayed
        List<RoomAvailability> overrides = availabilityRepository
                .findByRoomIdAndDateBetween(roomId, checkIn, lastNight);

        Map<LocalDate, RoomAvailability> overrideMap = overrides.stream()
                .collect(Collectors.toMap(RoomAvailability::getDate, Function.identity(), (a, b) -> a));

        // 3. Load active bookings to check if any night in this range is already booked.
        // Include both CONFIRMED and PENDING so pricing will reflect nights that are already
        // reserved by someone else even if payment is still pending.
        List<Booking> confirmedBookings = bookingRepository
                .findOverlappingBookings(roomId, BookingStatus.CONFIRMED, checkIn, checkOut);
        List<Booking> pendingBookings = bookingRepository
                .findOverlappingBookings(roomId, BookingStatus.PENDING, checkIn, checkOut);
        List<Booking> overlappingBookings = new ArrayList<>();
        overlappingBookings.addAll(confirmedBookings);
        overlappingBookings.addAll(pendingBookings);

        BigDecimal totalPrice = BigDecimal.ZERO;
        boolean entireStayAvailable = true;
        List<RoomPriceResponse.NightlyPriceBreakdown> breakdown = new ArrayList<>();

        // 4. Iterate night by night (checkIn inclusive -> checkOut exclusive)
        LocalDate current = checkIn;
        while (current.isBefore(checkOut)) {
            final LocalDate date = current;

            // Check if this night overlaps with a confirmed or pending booking
            boolean isBooked = overlappingBookings.stream().anyMatch(b ->
                    !date.isBefore(b.getCheckIn()) && date.isBefore(b.getCheckOut()));

            boolean dayAvailable = !isBooked;
            BigDecimal dailyRate = room.getPricePerNight();

            // Apply custom daily rate or maintenance block if override exists
            if (overrideMap.containsKey(date)) {
                RoomAvailability override = overrideMap.get(date);
                if (Boolean.FALSE.equals(override.getIsAvailable())) {
                    dayAvailable = false; // Blocked for maintenance
                }
                if (override.getDailyRate() != null) {
                    dailyRate = override.getDailyRate(); // Dynamic holiday price
                }
            }

            if (!dayAvailable) {
                entireStayAvailable = false;
            }

            totalPrice = totalPrice.add(dailyRate);

            breakdown.add(RoomPriceResponse.NightlyPriceBreakdown.builder()
                    .date(date)
                    .price(dailyRate)
                    .isAvailable(dayAvailable)
                    .build());

            current = current.plusDays(1);
        }

        long totalNights = ChronoUnit.DAYS.between(checkIn, checkOut);
        String normalizedTargetCurrency = targetCurrency == null || targetCurrency.isBlank()
            ? null : targetCurrency.trim().toUpperCase(Locale.ROOT);
        if (normalizedTargetCurrency != null && !normalizedTargetCurrency.equalsIgnoreCase(currency)) {
            totalPrice = exchangeRateService.convert(totalPrice, currency, normalizedTargetCurrency);
            breakdown = breakdown.stream()
                .map(nightly -> RoomPriceResponse.NightlyPriceBreakdown.builder()
                    .date(nightly.getDate())
                    .price(exchangeRateService.convert(nightly.getPrice(), currency, normalizedTargetCurrency))
                    .isAvailable(nightly.isAvailable())
                    .build())
                .collect(Collectors.toList());
        }

        return RoomPriceResponse.builder()
                .roomId(roomId)
                .checkIn(checkIn)
                .checkOut(checkOut)
                .totalNights(totalNights)
                .totalPrice(totalPrice)
                .currency(currency)
                .targetCurrency(normalizedTargetCurrency)
                .isAvailable(entireStayAvailable)
                .breakdown(breakdown)
                .build();
    }

    private String roomCurrency(Room room) {
        return room.getCurrency() != null && !room.getCurrency().isBlank()
                ? room.getCurrency()
                : room.getBranch().getCurrency();
    }
}
