package com.project.Backend_BookMyHotel.specification;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RoomSpecification {

    public static Specification<Room> buildSearchSpec(
            LocalDate checkIn,
            LocalDate checkOut,
            String city,
            String country,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String roomType,
            Integer maxOccupancy,
            Long hotelId
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. JOIN FETCH Room -> Branch -> Hotel (Only on data query, not count query)
            if (Long.class != query.getResultType() && long.class != query.getResultType()) {
                Fetch<Room, Branch> branchFetch = root.fetch("branch", JoinType.INNER);
                branchFetch.fetch("hotel", JoinType.INNER);
            }

            Join<Room, Branch> branchJoin = root.join("branch", JoinType.INNER);
            Join<Branch, Hotel> hotelJoin = branchJoin.join("hotel", JoinType.INNER);

            // 2. City & Country Filters
            if (city != null && !city.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(branchJoin.get("city")), city.toLowerCase().trim()));
            }
            if (country != null && !country.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(branchJoin.get("country")), country.toLowerCase().trim()));
            }

            // 3. Price Filters
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("pricePerNight"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("pricePerNight"), maxPrice));
            }

            // 4. Room Type, Occupancy, Hotel ID Filters
            if (roomType != null && !roomType.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("roomType")), roomType.toLowerCase().trim()));
            }
            if (maxOccupancy != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxOccupancy"), maxOccupancy));
            }
            if (hotelId != null) {
                predicates.add(cb.equal(hotelJoin.get("id"), hotelId));
            }

            // 5. CRITICAL OVERLAP CONDITION: Exclude rooms with overlapping CONFIRMED bookings
            if (checkIn != null && checkOut != null) {
                Subquery<Long> bookingSubquery = query.subquery(Long.class);
                Root<Booking> bookingRoot = bookingSubquery.from(Booking.class);

                bookingSubquery.select(bookingRoot.get("id"))
                        .where(
                                cb.equal(bookingRoot.get("room"), root),
                                cb.equal(bookingRoot.get("status"), BookingStatus.CONFIRMED),
                                cb.lessThan(bookingRoot.get("checkIn"), checkOut),
                                cb.greaterThan(bookingRoot.get("checkOut"), checkIn)
                        );

                predicates.add(cb.not(cb.exists(bookingSubquery)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
            // Takes an array or list of individual Predicate objects and combines them with logical AND operators into a single parent predicate:
        };
    }
}