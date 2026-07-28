package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.dto.RoomSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

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
            Long hotelId,
            int page,
            int size,
            String sort
    ) {
        // The actual (cacheable) DB fetch lives in RoomSearchCacheService and only ever returns a
        // plain list + count. Page/Pageable/ResponseEntity are built fresh here on every call —
        // cheap, in-memory work — so nothing Jackson can't safely round-trip ever goes into Redis.
        RoomSearchCacheService.CachedSearchResult cached = roomSearchCacheService.fetchSearchResults(
                checkIn, checkOut, city, country, minPrice, maxPrice, filterCurrency,
                roomType, maxOccupancy, hotelId, page, size, sort
        );

        Pageable pageable = PageRequest.of(page, size, RoomSearchCacheService.parseSortParameter(sort));
        Page<RoomSearchResult> result = new PageImpl<>(cached.content(), pageable, cached.totalElements());

        return ResponseEntity.ok(result);
    }
}
