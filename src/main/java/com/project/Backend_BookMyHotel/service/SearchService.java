package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.dto.RoomSearchResult;
import com.project.Backend_BookMyHotel.repository.RoomRepository;
import com.project.Backend_BookMyHotel.specification.RoomSpecification;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class SearchService {

    @Autowired
    private RoomRepository roomRepository;

    @Transactional(readOnly = true)
    @Cacheable(
            value = "availability",
            key = "(#checkIn != null ? #checkIn.toString() : '') + ':' + " +
                    "(#checkOut != null ? #checkOut.toString() : '') + ':' + " +
                    "(#city != null ? #city : '') + ':' + " +
                    "(#country != null ? #country : '') + ':' + " +
                    "(#minPrice != null ? #minPrice.toString() : '') + ':' + " +
                    "(#maxPrice != null ? #maxPrice.toString() : '') + ':' + " +
                    "(#roomType != null ? #roomType : '') + ':' + " +
                    "(#maxOccupancy != null ? #maxOccupancy.toString() : '') + ':' + " +
                    "(#hotelId != null ? #hotelId.toString() : '') + ':' + " +
                    "#page + ':' + #size + ':' + #sort"
    )
    public ResponseEntity<?> searchAvailableRooms(
            LocalDate checkIn,
            LocalDate checkOut,
            String city,
            String country,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String roomType,
            Integer maxOccupancy,
            Long hotelId,
            int page,
            int size,
            String sort
    ) {
        // Parse sorting directive (e.g., "pricePerNight,asc" or "rating,desc")
        Sort sortObj = parseSortParameter(sort);
        Pageable pageable = PageRequest.of(page, size, sortObj);

        try{
            // Build criteria specification
            Specification<Room> spec = RoomSpecification.buildSearchSpec(
                    checkIn, checkOut, city, country, minPrice, maxPrice, roomType, maxOccupancy, hotelId
            );

            // Execute query
            Page<Room> roomPage = roomRepository.findAll(spec, pageable);

            // Calculate stay duration (default to 1 night if dates are omitted)
            long numberOfNights = 1;
            if (checkIn != null && checkOut != null) {
                numberOfNights = ChronoUnit.DAYS.between(checkIn, checkOut);
                if (numberOfNights <= 0) {
                    numberOfNights = 1;
                }
            }

            final long nights = numberOfNights;

            // Map to response DTO
            Page<RoomSearchResult> result = roomPage.map(room -> RoomSearchResult.builder()
                    .roomId(room.getId())
                    .hotelName(room.getBranch().getHotel().getName())
                    .branchCity(room.getBranch().getCity())
                    .roomType(room.getRoomType())
                    .pricePerNight(room.getPricePerNight())
                    .totalPrice(room.getPricePerNight().multiply(BigDecimal.valueOf(nights)))
                    .currency(room.getBranch().getCurrency())
                    .amenities(room.getAmenities())
                    .available(true)
                    .build());

            return ResponseEntity.ok(result);
        } catch (Exception e){
            System.out.println("Error With building Search Spec");
            return ResponseEntity.internalServerError().body("Error With building Search Spec");
        }
    }

    private Sort parseSortParameter(String sort) {
        if (sort == null || sort.trim().isEmpty()) {
            return Sort.by(Sort.Direction.ASC, "pricePerNight");
        }

        String[] parts = sort.split(",");
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && parts[1].equalsIgnoreCase("desc"))
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        // Map custom sort properties to entity fields
        if ("price".equalsIgnoreCase(property)) {
            property = "pricePerNight";
        } else if ("rating".equalsIgnoreCase(property)) {
            property = "branch.hotel.rating";
        }

        return Sort.by(direction, property);
    }
}
