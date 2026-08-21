package com.project.Backend_BookMyHotel.specification;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import com.project.Backend_BookMyHotel.dto.RoomTag;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Fetch;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RoomSpecification {

    // Per-branch-currency min/max, already converted from the caller's filterCurrency. Either
    // bound may be null, mirroring how a bare minPrice/maxPrice is optional.
    public record CurrencyPriceRange(BigDecimal min, BigDecimal max) {}

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
        Set<Long> hotelIds = hotelId == null ? Set.of() : Set.of(hotelId);
        return buildSearchSpec(checkIn, checkOut, city, country, minPrice, maxPrice, roomType, maxOccupancy, hotelIds, null, null);
    }

    // priceRangesByCurrency: when non-empty, price filtering is done per branch.currency using
    // these pre-converted ranges instead of comparing minPrice/maxPrice directly against
    // pricePerNight — rooms are priced in their branch's native currency, so a raw comparison
    // across branches with different currencies (e.g. GBP vs AED) is meaningless. Pass null/empty
    // to fall back to the raw, currency-unaware comparison (backward-compatible default).
    // tags: when non-empty, only rooms carrying EVERY requested tag match (AND semantics) —
    // e.g. filtering for [ECO_FRIENDLY, WORK_FRIENDLY] returns rooms that are both, not either.
    public static Specification<Room> buildSearchSpec(
            LocalDate checkIn,
            LocalDate checkOut,
            String city,
            String country,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String roomType,
            Integer maxOccupancy,
            Set<Long> hotelIds,
            Map<String, CurrencyPriceRange> priceRangesByCurrency,
            Set<RoomTag> tags
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

            // Soft-deleted rooms remain in PostgreSQL for historical bookings and analytics,
            // but must never appear in public room discovery.
            predicates.add(cb.isTrue(root.get("active")));

            // 2. City & Country Filters
            if (city != null && !city.trim().isEmpty()) {
                predicates.add(cb.equal(lowerText(cb, branchJoin.get("city")), city.toLowerCase().trim()));
            }
            if (country != null && !country.trim().isEmpty()) {
                predicates.add(cb.equal(lowerText(cb, branchJoin.get("country")), country.toLowerCase().trim()));
            }

            // 3. Price Filters
            if (priceRangesByCurrency != null) {
                List<Predicate> currencyPredicates = new ArrayList<>();
                Expression<String> effectiveCurrency = cb.upper(
                        cb.<String>coalesce().value(root.get("currency")).value(branchJoin.get("currency")));
                for (Map.Entry<String, CurrencyPriceRange> entry : priceRangesByCurrency.entrySet()) {
                    CurrencyPriceRange range = entry.getValue();
                    List<Predicate> rangePredicates = new ArrayList<>();
                    rangePredicates.add(cb.equal(effectiveCurrency, entry.getKey()));
                    if (range.min() != null) {
                        rangePredicates.add(cb.greaterThanOrEqualTo(root.get("pricePerNight"), range.min()));
                    }
                    if (range.max() != null) {
                        rangePredicates.add(cb.lessThanOrEqualTo(root.get("pricePerNight"), range.max()));
                    }
                    currencyPredicates.add(cb.and(rangePredicates.toArray(new Predicate[0])));
                }
                predicates.add(currencyPredicates.isEmpty()
                        ? cb.disjunction()
                        : cb.or(currencyPredicates.toArray(new Predicate[0])));
            } else {
                if (minPrice != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("pricePerNight"), minPrice));
                }
                if (maxPrice != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("pricePerNight"), maxPrice));
                }
            }

            // 4. Room Type, Occupancy, Hotel ID Filters
            if (roomType != null && !roomType.trim().isEmpty()) {
                predicates.add(cb.equal(cb.lower(root.get("roomType")), roomType.toLowerCase().trim()));
            }
            if (maxOccupancy != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxOccupancy"), maxOccupancy));
            }
            if (hotelIds != null && !hotelIds.isEmpty()) {
                predicates.add(hotelJoin.get("id").in(hotelIds));
            }

            // 5. Room Tag Filters — a separate join per requested tag, each constrained to that
            // one tag value, so a room only survives when it has a matching row for every join
            // (AND semantics), not just any one of them.
            if (tags != null && !tags.isEmpty()) {
                for (RoomTag tag : tags) {
                    Join<Room, RoomTag> tagJoin = root.join("tags", JoinType.INNER);
                    predicates.add(cb.equal(tagJoin, tag));
                }
            }

            // 6. CRITICAL OVERLAP CONDITION: Exclude rooms with overlapping CONFIRMED bookings
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

    private static Expression<String> lowerText(jakarta.persistence.criteria.CriteriaBuilder cb, Path<?> path) {
        return cb.lower(path.as(String.class));
    }
}
