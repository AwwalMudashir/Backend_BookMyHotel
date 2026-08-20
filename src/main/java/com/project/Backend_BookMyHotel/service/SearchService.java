package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.dto.RoomSearchResult;
import com.project.Backend_BookMyHotel.dto.RoomTag;
import com.project.Backend_BookMyHotel.exception.InvalidSearchRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.TreeSet;

@Service
public class SearchService {

    @Autowired
    private RoomSearchCacheService roomSearchCacheService;

    public ResponseEntity<?> searchAvailableRooms(
            LocalDate checkIn,
            LocalDate checkOut,
            String city,
            String country,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String filterCurrency,
            String roomType,
            Integer maxOccupancy,
            Set<Long> hotelIds,
            Set<RoomTag> tags,
            int page,
            int size,
            String sort
    ) {
        // The actual (cacheable) DB fetch lives in RoomSearchCacheService and only ever returns a
        // plain list + count. Page/Pageable/ResponseEntity are built fresh here on every call —
        // cheap, in-memory work — so nothing Jackson can't safely round-trip ever goes into Redis.
        // No try/catch here on purpose: letting an unexpected exception propagate to
        // GlobalExceptionHandler is what gets it logged with a real stack trace and a consistent
        // {status, message} JSON body — swallowing it locally just hides the real cause.
        validateSearchRequest(checkIn, checkOut, minPrice, maxPrice, maxOccupancy, page, size);
        Set<Long> normalizedHotelIds = hotelIds == null ? Set.of() : new TreeSet<>(hotelIds);

        RoomSearchCacheService.CachedSearchResult cached = roomSearchCacheService.fetchSearchResults(
                checkIn, checkOut, city, country, minPrice, maxPrice, filterCurrency,
                roomType, maxOccupancy, normalizedHotelIds, tags, page, size, sort
        );

        Pageable pageable = PageRequest.of(page, size, RoomSearchCacheService.parseSortParameter(sort));
        Page<RoomSearchResult> result = new PageImpl<>(cached.content(), pageable, cached.totalElements());

        return ResponseEntity.ok(result);
    }

    private void validateSearchRequest(
            LocalDate checkIn,
            LocalDate checkOut,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer maxOccupancy,
            int page,
            int size
    ) {
        if (checkIn != null && checkOut != null && !checkOut.isAfter(checkIn)) {
            throw new InvalidSearchRequestException("Check-out must be after check-in.");
        }
        if (minPrice != null && minPrice.signum() < 0) {
            throw new InvalidSearchRequestException("Minimum price cannot be negative.");
        }
        if (maxPrice != null && maxPrice.signum() < 0) {
            throw new InvalidSearchRequestException("Maximum price cannot be negative.");
        }
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new InvalidSearchRequestException("Minimum price cannot be greater than maximum price.");
        }
        if (maxOccupancy != null && maxOccupancy < 1) {
            throw new InvalidSearchRequestException("Guest count must be at least one.");
        }
        if (page < 0) {
            throw new InvalidSearchRequestException("Page number cannot be negative.");
        }
        if (size < 1 || size > 100) {
            throw new InvalidSearchRequestException("Page size must be between 1 and 100.");
        }
    }
}
