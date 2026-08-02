package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.Promotion;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.CreatePromotionRequest;
import com.project.Backend_BookMyHotel.dto.DiscountType;
import com.project.Backend_BookMyHotel.dto.PromotionResponse;
import com.project.Backend_BookMyHotel.dto.Role;
import com.project.Backend_BookMyHotel.dto.UpdatePromotionRequest;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import com.project.Backend_BookMyHotel.repository.PromotionRepository;
import com.project.Backend_BookMyHotel.service.PromotionService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class PromotionServiceTest {

    @Mock
    private PromotionRepository promotionRepository;

    @Mock
    private HotelRepository hotelRepository;

    @InjectMocks
    private PromotionService promotionService;

    private Hotel hotel;
    private User admin;
    private User managerOfHotel;
    private User managerOfOtherHotel;

    @BeforeEach
    void setUp() {
        hotel = new Hotel();
        hotel.setId(1000L);
        hotel.setName("Grand Hotel");

        Hotel otherHotel = new Hotel();
        otherHotel.setId(2000L);
        otherHotel.setName("Other Hotel");

        admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);

        managerOfHotel = new User();
        managerOfHotel.setId(2L);
        managerOfHotel.setRole(Role.HOTEL_MANAGER);
        managerOfHotel.setManagedHotel(hotel);

        managerOfOtherHotel = new User();
        managerOfOtherHotel.setId(3L);
        managerOfOtherHotel.setRole(Role.HOTEL_MANAGER);
        managerOfOtherHotel.setManagedHotel(otherHotel);
    }

    private CreatePromotionRequest validRequest() {
        return new CreatePromotionRequest(
                1000L, "SUMMER10", DiscountType.PERCENTAGE, BigDecimal.TEN,
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 8, 31),
                100, null, null
        );
    }

    @Test
    void createPromotion_AsAdmin_Succeeds() {
        Mockito.when(hotelRepository.findById(1000L)).thenReturn(Optional.of(hotel));
        Mockito.when(promotionRepository.findByCodeIgnoreCase("SUMMER10")).thenReturn(Optional.empty());
        Mockito.when(promotionRepository.save(Mockito.any(Promotion.class))).thenAnswer(inv -> {
            Promotion p = inv.getArgument(0);
            p.setId(500L);
            return p;
        });

        ResponseEntity<?> response = promotionService.createPromotion(validRequest(), admin);

        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        PromotionResponse body = (PromotionResponse) response.getBody();
        Assertions.assertNotNull(body);
        Assertions.assertEquals("SUMMER10", body.getCode());
        Assertions.assertEquals(1000L, body.getHotelId());
    }

    @Test
    void createPromotion_AsManagerOfOwnHotel_Succeeds() {
        Mockito.when(hotelRepository.findById(1000L)).thenReturn(Optional.of(hotel));
        Mockito.when(promotionRepository.findByCodeIgnoreCase("SUMMER10")).thenReturn(Optional.empty());
        Mockito.when(promotionRepository.save(Mockito.any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = promotionService.createPromotion(validRequest(), managerOfHotel);

        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void createPromotion_AsManagerOfDifferentHotel_ReturnsForbidden() {
        Mockito.when(hotelRepository.findById(1000L)).thenReturn(Optional.of(hotel));

        ResponseEntity<?> response = promotionService.createPromotion(validRequest(), managerOfOtherHotel);

        Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Mockito.verify(promotionRepository, Mockito.never()).save(Mockito.any(Promotion.class));
    }

    @Test
    void createPromotion_DuplicateCode_ReturnsConflict() {
        Mockito.when(hotelRepository.findById(1000L)).thenReturn(Optional.of(hotel));
        Mockito.when(promotionRepository.findByCodeIgnoreCase("SUMMER10"))
                .thenReturn(Optional.of(new Promotion()));

        ResponseEntity<?> response = promotionService.createPromotion(validRequest(), admin);

        Assertions.assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        Mockito.verify(promotionRepository, Mockito.never()).save(Mockito.any(Promotion.class));
    }

    @Test
    void createPromotion_ValidToBeforeValidFrom_ReturnsBadRequest() {
        Mockito.when(hotelRepository.findById(1000L)).thenReturn(Optional.of(hotel));
        Mockito.when(promotionRepository.findByCodeIgnoreCase("SUMMER10")).thenReturn(Optional.empty());

        CreatePromotionRequest badRange = new CreatePromotionRequest(
                1000L, "SUMMER10", DiscountType.PERCENTAGE, BigDecimal.TEN,
                LocalDate.of(2026, 8, 31), LocalDate.of(2026, 6, 1),
                null, null, null
        );

        ResponseEntity<?> response = promotionService.createPromotion(badRange, admin);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Mockito.verify(promotionRepository, Mockito.never()).save(Mockito.any(Promotion.class));
    }

    @Test
    void updatePromotion_OnlyOverwritesProvidedFields() {
        Promotion existing = Promotion.builder()
                .id(500L)
                .hotel(hotel)
                .code("SUMMER10")
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .validFrom(LocalDate.of(2026, 6, 1))
                .validTo(LocalDate.of(2026, 8, 31))
                .maxUses(100)
                .timesUsed(5)
                .active(true)
                .build();

        Mockito.when(promotionRepository.findById(500L)).thenReturn(Optional.of(existing));
        Mockito.when(promotionRepository.save(Mockito.any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

        // Only bumping discountValue — everything else should stay exactly as it was.
        UpdatePromotionRequest request = new UpdatePromotionRequest(null, BigDecimal.valueOf(15), null, null, null, null, null);

        ResponseEntity<?> response = promotionService.updatePromotion(500L, request, admin);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        PromotionResponse body = (PromotionResponse) response.getBody();
        Assertions.assertEquals(0, BigDecimal.valueOf(15).compareTo(body.getDiscountValue()));
        Assertions.assertEquals(DiscountType.PERCENTAGE, body.getDiscountType());
        Assertions.assertEquals(LocalDate.of(2026, 6, 1), body.getValidFrom());
        Assertions.assertEquals(100, body.getMaxUses());
    }

    @Test
    void updatePromotion_AsManagerOfDifferentHotel_ReturnsForbidden() {
        Promotion existing = Promotion.builder().id(500L).hotel(hotel).code("SUMMER10").build();
        Mockito.when(promotionRepository.findById(500L)).thenReturn(Optional.of(existing));

        UpdatePromotionRequest request = new UpdatePromotionRequest(null, BigDecimal.valueOf(15), null, null, null, null, null);
        ResponseEntity<?> response = promotionService.updatePromotion(500L, request, managerOfOtherHotel);

        Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Mockito.verify(promotionRepository, Mockito.never()).save(Mockito.any(Promotion.class));
    }

    @Test
    void deactivatePromotion_SetsActiveFalseAndSaves() {
        Promotion existing = Promotion.builder().id(500L).hotel(hotel).code("SUMMER10").active(true).build();
        Mockito.when(promotionRepository.findById(500L)).thenReturn(Optional.of(existing));
        Mockito.when(promotionRepository.save(Mockito.any(Promotion.class))).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<?> response = promotionService.deactivatePromotion(500L, admin);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertFalse(existing.getActive());
        PromotionResponse body = (PromotionResponse) response.getBody();
        Assertions.assertFalse(body.getActive());
    }

    @Test
    void listActivePromotions_WhenHotelMissing_ReturnsNotFound() {
        Mockito.when(hotelRepository.existsById(9999L)).thenReturn(false);

        ResponseEntity<?> response = promotionService.listActivePromotions(9999L);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void listActivePromotions_ReturnsOnlyActivePromotionsForThatHotel() {
        Promotion active = Promotion.builder().id(500L).hotel(hotel).code("SUMMER10").active(true).build();
        Mockito.when(hotelRepository.existsById(1000L)).thenReturn(true);
        Mockito.when(promotionRepository.findByHotelIdAndActiveTrue(1000L)).thenReturn(List.of(active));

        ResponseEntity<?> response = promotionService.listActivePromotions(1000L);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<PromotionResponse> body = (List<PromotionResponse>) response.getBody();
        Assertions.assertEquals(1, body.size());
        Assertions.assertEquals("SUMMER10", body.get(0).getCode());
    }
}
