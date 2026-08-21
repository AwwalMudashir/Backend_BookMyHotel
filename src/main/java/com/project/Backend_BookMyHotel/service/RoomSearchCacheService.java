package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.dto.RoomSearchResult;
import com.project.Backend_BookMyHotel.dto.RoomTag;
import com.project.Backend_BookMyHotel.repository.RoomRepository;
import com.project.Backend_BookMyHotel.specification.RoomSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Holds only the room-search DB fetch. Split out from SearchService specifically so the
// @Cacheable method's return type never includes Page/Pageable/ResponseEntity — those are
// immutable, constructor-less types that Jackson can serialize (via getters) but cannot safely
// deserialize back out of Redis, which is what was silently corrupting cached search results.
@Service
public class RoomSearchCacheService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private ExchangeRateService exchangeRateService;

    @Transactional(readOnly = true)
    @Cacheable(
            value = "availability",
            key = "(#checkIn != null ? #checkIn.toString() : '') + ':' + " +
                    "(#checkOut != null ? #checkOut.toString() : '') + ':' + " +
                    "(#city != null ? #city : '') + ':' + " +
                    "(#country != null ? #country : '') + ':' + " +
                    "(#minPrice != null ? #minPrice.toString() : '') + ':' + " +
                    "(#maxPrice != null ? #maxPrice.toString() : '') + ':' + " +
                    "(#filterCurrency != null ? #filterCurrency : '') + ':' + " +
                    "(#roomType != null ? #roomType : '') + ':' + " +
                    "(#maxOccupancy != null ? #maxOccupancy.toString() : '') + ':' + " +
                    "(#hotelIds != null && !#hotelIds.isEmpty() ? #hotelIds.toString() : '') + ':' + " +
                    // Set iteration order is only stable within a single running JVM, not across
                    // restarts — worst case a restart splits what should be one cache entry into
                    // two, which self-heals via the 5-minute TTL. Not worth a sorted-key dance for.
                    "(#tags != null && !#tags.isEmpty() ? #tags.toString() : '') + ':' + " +
                    "#page + ':' + #size + ':' + #sort"
    )
    public CachedSearchResult fetchSearchResults(
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
        Pageable pageable = PageRequest.of(page, size, parseSortParameter(sort));

        Map<String, RoomSpecification.CurrencyPriceRange> priceRangesByCurrency =
                buildPriceRangesByCurrency(city, country, hotelIds, minPrice, maxPrice, filterCurrency);

        Specification<Room> spec = RoomSpecification.buildSearchSpec(
                checkIn, checkOut, city, country, minPrice, maxPrice, roomType, maxOccupancy, hotelIds,
                priceRangesByCurrency, tags
        );

        Page<Room> roomPage = roomRepository.findAll(spec, pageable);

        long numberOfNights = 1;
        if (checkIn != null && checkOut != null) {
            numberOfNights = ChronoUnit.DAYS.between(checkIn, checkOut);
            if (numberOfNights <= 0) {
                numberOfNights = 1;
            }
        }
        final long nights = numberOfNights;

        List<RoomSearchResult> content = roomPage.getContent().stream()
                .map(room -> mapToSearchResult(room, nights))
                .toList();

        return new CachedSearchResult(content, roomPage.getTotalElements());
    }

    // Rooms are priced in their own branch's currency, and a search can span branches with
    // different currencies (e.g. no city/country filter). So minPrice/maxPrice - expressed in
    // filterCurrency - get converted into every currency actually in play among the candidate
    // branches, giving the specification a currency-aware range to compare each room against.
    // Returns null when no currency-aware filtering applies, in which case the specification
    // falls back to its original raw (currency-unaware) comparison for backward compatibility.
    private Map<String, RoomSpecification.CurrencyPriceRange> buildPriceRangesByCurrency(
            String city, String country, Set<Long> hotelIds,
            BigDecimal minPrice, BigDecimal maxPrice, String filterCurrency
    ) {
        if (filterCurrency == null || filterCurrency.isBlank() || (minPrice == null && maxPrice == null)) {
            return null;
        }

        String normalizedFilterCurrency = filterCurrency.trim().toUpperCase(Locale.ROOT);
        exchangeRateService.requireSupportedCurrency(normalizedFilterCurrency);
        // Blank strings must become null here, matching how the specification itself treats a
        // blank city/country as "no filter" rather than "match branches with an empty city".
        String normalizedCity = (city != null && !city.trim().isEmpty()) ? city.trim() : null;
        String normalizedCountry = (country != null && !country.trim().isEmpty()) ? country.trim() : null;
        boolean filterByHotels = hotelIds != null && !hotelIds.isEmpty();
        List<Long> scopedHotelIds = filterByHotels ? List.copyOf(hotelIds) : List.of(-1L);
        List<String> currencies = roomRepository.findDistinctEffectiveCurrencies(
                normalizedCity, normalizedCountry, filterByHotels, scopedHotelIds);

        Map<String, RoomSpecification.CurrencyPriceRange> priceRangesByCurrency = new HashMap<>();
        for (String currency : currencies) {
            if (currency == null || currency.isBlank()) {
                continue;
            }
            String normalizedCurrency = currency.trim().toUpperCase(Locale.ROOT);
            // Legacy rooms may contain a currency the provider cannot convert. They must not
            // abort an otherwise valid search; they are simply excluded while a price filter is active.
            if (!exchangeRateService.isSupportedCurrency(normalizedCurrency)) {
                continue;
            }
            BigDecimal convertedMin = minPrice != null
                    ? exchangeRateService.convert(minPrice, normalizedFilterCurrency, normalizedCurrency)
                    : null;
            BigDecimal convertedMax = maxPrice != null
                    ? exchangeRateService.convert(maxPrice, normalizedFilterCurrency, normalizedCurrency)
                    : null;
            priceRangesByCurrency.put(normalizedCurrency,
                    new RoomSpecification.CurrencyPriceRange(convertedMin, convertedMax));
        }

        return priceRangesByCurrency;
    }

    // Same custom-sort-key mapping SearchService always used; exposed as static so SearchService
    // can build the same Sort/Pageable for its final PageImpl without re-running the cached query.
    public static Sort parseSortParameter(String sort) {
        if (sort == null || sort.trim().isEmpty()) {
            return Sort.by(Sort.Direction.ASC, "pricePerNight");
        }

        String trimmed = sort.trim();
        String[] parts = trimmed.contains(",") ? trimmed.split(",", 2) : trimmed.split("_", 2);
        String property = parts[0].trim();
        Sort.Direction direction = (parts.length > 1 && parts[1].trim().equalsIgnoreCase("desc"))
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        if ("rating".equalsIgnoreCase(property)) {
            property = "branch.hotel.starRating";
        } else {
            property = "pricePerNight";
        }

        return Sort.by(direction, property);
    }

    private RoomSearchResult mapToSearchResult(Room room, long nights) {
        Branch branch = room != null ? room.getBranch() : null;
        Hotel hotel = branch != null ? branch.getHotel() : null;

        BigDecimal pricePerNight = room != null ? room.getPricePerNight() : null;
        if (pricePerNight == null) {
            pricePerNight = BigDecimal.ZERO;
        }

        BigDecimal totalPrice = pricePerNight.multiply(BigDecimal.valueOf(nights));
        Map<String, Object> amenities = room != null && room.getAmenities() != null
                ? Map.copyOf(room.getAmenities())
                : Collections.emptyMap();
        Set<RoomTag> tags = room != null && room.getTags() != null
                ? Set.copyOf(room.getTags())
                : Collections.emptySet();

        // Choose the first non-null image (if any) as the thumbnail for search results
        String thumbnail = null;
        if (room != null && room.getImages() != null && !room.getImages().isEmpty()) {
            thumbnail = room.getImages().stream().filter(java.util.Objects::nonNull).findFirst().orElse(null);
        }

        return RoomSearchResult.builder()
                .roomId(room != null ? room.getId() : null)
                .hotelName(hotel != null ? hotel.getName() : "Unknown")
                .branchCity(branch != null ? branch.getCity() : "")
                .roomType(room != null ? room.getRoomType() : "")
                .pricePerNight(pricePerNight)
                .totalPrice(totalPrice)
                .currency(room != null && room.getCurrency() != null ? room.getCurrency() : (branch != null ? branch.getCurrency() : "USD"))
                .amenities(amenities)
                .tags(tags)
                .available(true)
                .thumbnail(thumbnail)
                .build();
    }

    // Plain record (list + count) — deliberately the only shape this service ever hands to the
    // @Cacheable proxy, since a record's canonical constructor is exactly what Jackson needs to
    // rebuild it from cached JSON, unlike Page/PageImpl/ResponseEntity.
    public record CachedSearchResult(List<RoomSearchResult> content, long totalElements) {}
}
