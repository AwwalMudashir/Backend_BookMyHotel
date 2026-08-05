package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.Promotion;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.*;
import com.project.Backend_BookMyHotel.repository.BookingRepository;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import com.project.Backend_BookMyHotel.repository.PromotionRepository;
import com.project.Backend_BookMyHotel.repository.RoomRepository;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.PromotionService;
import com.project.Backend_BookMyHotel.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.test.database.replace=none", "spring.profiles.active=test"})
@Testcontainers
class SearchServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private SearchService searchService;

    @Autowired
    private PromotionService promotionService;

    @Autowired
    private HotelRepository hotelRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PromotionRepository promotionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        promotionRepository.deleteAll();
        roomRepository.deleteAll();
        branchRepository.deleteAll();
        hotelRepository.deleteAll();
        userRepository.deleteAll();
        if (cacheManager.getCache("availability") != null) {
            cacheManager.getCache("availability").clear();
        }
    }

    @Test
    void searchExcludesRoomWhenBookingOverlapsRequestedDates() {
        Hotel hotel = createHotel("Ocean View");
        Branch branch = createBranch(hotel, "Lagos", "NG");
        Room room = createRoom(branch, "Deluxe", new BigDecimal("120.00"));

        User user = createUser();
        Booking booking = new Booking();
        booking.setUser(user);
        booking.setRoom(room);
        booking.setCheckIn(LocalDate.of(2026, 7, 10));
        booking.setCheckOut(LocalDate.of(2026, 7, 14));
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setTotalPrice(new BigDecimal("480.00"));
        bookingRepository.save(booking);

        ResponseEntity<?> response = searchService.searchAvailableRooms(
                LocalDate.of(2026, 7, 12),
                LocalDate.of(2026, 7, 15),
                "Lagos",
                "NG",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                10,
                null
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Page<?> page = (Page<?>) response.getBody();
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void secondIdenticalSearchIsServedFromCache() {
        Hotel hotel = createHotel("Cache Hotel");
        Branch branch = createBranch(hotel, "Abuja", "NG");
        Room room = createRoom(branch, "Suite", new BigDecimal("200.00"));

        ResponseEntity<?> first = searchService.searchAvailableRooms(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3),
                "Abuja",
                "NG",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                10,
                null
        );

        roomRepository.delete(room);

        ResponseEntity<?> second = searchService.searchAvailableRooms(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3),
                "Abuja",
                "NG",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                10,
                null
        );

        assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(second.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(first.getBody()).isInstanceOf(Page.class);
        assertThat(second.getBody()).isInstanceOf(Page.class);

        assertThat(((Page<?>) first.getBody()).getContent()).hasSize(1);
        assertThat(((Page<?>) second.getBody()).getContent()).hasSize(1);
    }

    @Test
    void searchWithoutFilterCurrencyComparesPriceRawAcrossBranchCurrencies() {
        Hotel hotel = createHotel("Multi Currency Hotel");
        Branch londonBranch = createBranch(hotel, "London", "UK", "GBP");
        Branch dubaiBranch = createBranch(hotel, "Dubai", "AE", "AED");
        createRoom(londonBranch, "Deluxe", new BigDecimal("150.00"));
        createRoom(dubaiBranch, "Deluxe", new BigDecimal("150.00"));

        ResponseEntity<?> response = searchService.searchAvailableRooms(
                null, null, null, null,
                new BigDecimal("100.00"), new BigDecimal("200.00"), null,
                null, null, null, null,
                0, 10, null
        );

        // Backward-compatible (buggy) behaviour when no filterCurrency is given: 150 GBP and
        // 150 AED are treated as equally "within 100-200", despite being very different values.
        Page<?> page = (Page<?>) response.getBody();
        assertThat(page.getContent()).hasSize(2);
    }

    @Test
    void searchWithFilterCurrencyConvertsPriceRangePerBranchCurrency() {
        Hotel hotel = createHotel("Multi Currency Hotel");
        Branch londonBranch = createBranch(hotel, "London", "UK", "GBP");
        Branch dubaiBranch = createBranch(hotel, "Dubai", "AE", "AED");
        createRoom(londonBranch, "Deluxe", new BigDecimal("150.00"));
        createRoom(dubaiBranch, "Deluxe", new BigDecimal("150.00"));

        ResponseEntity<?> response = searchService.searchAvailableRooms(
                null, null, null, null,
                new BigDecimal("100.00"), new BigDecimal("200.00"), "GBP",
                null, null, null, null,
                0, 10, null
        );

        // 100-200 GBP converts to roughly 470-941 AED (via the fixed GBP/AED test rates), so the
        // 150 AED room correctly falls outside the range while the 150 GBP room stays inside it.
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Page<?> page = (Page<?>) response.getBody();
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void findDistinctCurrenciesRespectsOptionalLocationFilters() {
        Hotel hotel = createHotel("Filter Currency Hotel");
        createBranch(hotel, "Berlin", "DE", "EUR");
        createBranch(hotel, "Berlin", "DE", "GBP");

        java.util.List<String> currencies = branchRepository.findDistinctCurrencies("Berlin", "DE");

        assertThat(currencies).containsExactlyInAnyOrder("EUR", "GBP");
    }

    @Test
    void searchByEcoFriendlyTagReturnsOnlyTaggedRooms() {
        Hotel hotel = createHotel("Green Hotel");
        Branch branch = createBranch(hotel, "Berlin", "DE");
        Room ecoRoom = createRoom(branch, "Deluxe", new BigDecimal("120.00"));
        ecoRoom.setTags(java.util.Set.of(RoomTag.ECO_FRIENDLY));
        roomRepository.save(ecoRoom);
        createRoom(branch, "Standard", new BigDecimal("90.00"));

        ResponseEntity<?> response = searchService.searchAvailableRooms(
                null, null, "Berlin", "DE",
                null, null, null,
                null, null, null, java.util.Set.of(RoomTag.ECO_FRIENDLY),
                0, 10, null
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        Page<?> page = (Page<?>) response.getBody();
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void promoValidationRejectsExpiredWrongHotelAndMaxedOutCode() {
        Hotel hotel = createHotel("Promo Hotel");
        Hotel otherHotel = createHotel("Other Hotel");
        Branch branch = createBranch(hotel, "Kano", "NG");
        createRoom(branch, "Standard", new BigDecimal("80.00"));

        Promotion expired = createPromotion(hotel, "EXPIRED", LocalDate.now().minusDays(10), LocalDate.now().minusDays(1), 10, true);
        Promotion wrongHotel = createPromotion(otherHotel, "WRONGHOTEL", LocalDate.now().minusDays(1), LocalDate.now().plusDays(5), 10, true);
        Promotion maxedOut = createPromotion(hotel, "MAXED", LocalDate.now().minusDays(1), LocalDate.now().plusDays(5), 1, true);
        maxedOut.setTimesUsed(1);
        promotionRepository.save(maxedOut);

        PromotionBreakdownResponse expiredResponse = promotionService.applyPromotion(expired.getCode(), new BigDecimal("100.00"), hotel.getId());
        PromotionBreakdownResponse wrongHotelResponse = promotionService.applyPromotion(wrongHotel.getCode(), new BigDecimal("100.00"), hotel.getId());
        PromotionBreakdownResponse maxedOutResponse = promotionService.applyPromotion(maxedOut.getCode(), new BigDecimal("100.00"), hotel.getId());

        assertThat(expiredResponse.getMessage()).contains("expired");
        assertThat(wrongHotelResponse.getMessage()).asString().contains("selected hotel");
        assertThat(maxedOutResponse.getMessage()).asString().contains("maximum usage");
    }

    private Hotel createHotel(String name) {
        Hotel hotel = new Hotel();
        hotel.setName(name);
        return hotelRepository.save(hotel);
    }

    private Branch createBranch(Hotel hotel, String city, String country) {
        return createBranch(hotel, city, country, "USD");
    }

    private Branch createBranch(Hotel hotel, String city, String country, String currency) {
        Branch branch = new Branch();
        branch.setHotel(hotel);
        branch.setCity(city);
        branch.setCountry(country);
        branch.setCurrency(currency);
        branch.setName(city + " Branch");
        return branchRepository.save(branch);
    }

    private Room createRoom(Branch branch, String roomType, BigDecimal price) {
        Room room = new Room();
        room.setBranch(branch);
        room.setRoomType(roomType);
        room.setPricePerNight(price);
        room.setMaxOccupancy(2);
        room.setAmenities(Map.of("wifi", true));
        return roomRepository.save(room);
    }

    private User createUser() {
        User user = new User();
        user.setEmail("user" + System.nanoTime() + "@example.com");
        user.setPassword("secret");
        user.setRole(Role.CUSTOMER);
        user.setUserId("user-" + System.nanoTime());
        return userRepository.save(user);
    }

    private Promotion createPromotion(Hotel hotel, String code, LocalDate validFrom, LocalDate validTo, Integer maxUses, boolean active) {
        Promotion promotion = Promotion.builder()
                .hotel(hotel)
                .code(code)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(new BigDecimal("10.00"))
                .validFrom(validFrom)
                .validTo(validTo)
                .maxUses(maxUses)
                .active(active)
                .build();
        return promotionRepository.save(promotion);
    }
}
