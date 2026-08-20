package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.dto.RoomTag;
import com.project.Backend_BookMyHotel.exception.InvalidSearchRequestException;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import com.project.Backend_BookMyHotel.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

@RestController
@RequestMapping("/search")
public class SearchController {
    @Autowired
    private SearchService searchService;

    @Autowired
    private HotelRepository hotelRepository;

    @GetMapping("/rooms")
    public ResponseEntity<?> searchAvailableRooms(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String filterCurrency,
            @RequestParam(required = false) String roomType,
            @RequestParam(required = false) Integer maxOccupancy,
            @RequestParam(required = false) String hotelId,
            @RequestParam(required = false) String hotelIds,
            @RequestParam(required = false) Set<RoomTag> tags,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "price,asc") String sort
    ) {

        Set<Long> requestedHotelIds = parseHotelIds(hotelId, hotelIds);

        return searchService.searchAvailableRooms(
                checkIn, checkOut, city, country, minPrice, maxPrice, filterCurrency,
                roomType, maxOccupancy, requestedHotelIds, tags, page, size, sort
        );
    }

    private Set<Long> parseHotelIds(String legacyHotelId, String hotelIds) {
        Set<Long> parsedIds = new LinkedHashSet<>();
        Arrays.stream(new String[]{legacyHotelId, hotelIds})
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> Arrays.stream(value.split(",")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(this::resolveHotelIdentifier)
                .forEach(parsedIds::add);
        return parsedIds;
    }

    private Long resolveHotelIdentifier(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // Legacy frontend filters used hotel-name slugs such as "four-seasons".
        }

        String nameFromSlug = value.replace('-', ' ').replace('_', ' ').trim();
        return hotelRepository.findByPublicId(value)
                .or(() -> hotelRepository.findByNameIgnoreCase(nameFromSlug))
                .map(hotel -> hotel.getId())
                .orElseThrow(() -> new InvalidSearchRequestException(
                        "No hotel matches the selected filter: " + value
                ));
    }
}
