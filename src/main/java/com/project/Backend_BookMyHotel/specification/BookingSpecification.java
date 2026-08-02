package com.project.Backend_BookMyHotel.specification;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class BookingSpecification {

    // Every filter is optional — omit a parameter to leave that dimension unconstrained. "date"
    // matches bookings actually occupying a room on that day (checkIn <= date < checkOut), the
    // same "who's in-house" semantics AnalyticsService.getBookingsByDate uses, not just arrivals.
    public static Specification<Booking> buildAdminFilterSpec(Long hotelId, LocalDate date, BookingStatus status) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (hotelId != null) {
                Join<Booking, Room> roomJoin = root.join("room", JoinType.INNER);
                Join<Room, Branch> branchJoin = roomJoin.join("branch", JoinType.INNER);
                Join<Branch, Hotel> hotelJoin = branchJoin.join("hotel", JoinType.INNER);
                predicates.add(cb.equal(hotelJoin.get("id"), hotelId));
            }

            if (date != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("checkIn"), date));
                predicates.add(cb.greaterThan(root.get("checkOut"), date));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
