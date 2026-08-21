package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.config.AppConfig;
import com.project.Backend_BookMyHotel.controller.AvailabilityController;
import com.project.Backend_BookMyHotel.controller.BranchController;
import com.project.Backend_BookMyHotel.controller.PromotionController;
import com.project.Backend_BookMyHotel.dto.AvailabilityCalendar;
import com.project.Backend_BookMyHotel.dto.PromotionBreakdownResponse;
import com.project.Backend_BookMyHotel.dto.RoomPriceResponse;
import com.project.Backend_BookMyHotel.filter.JwtAuthenticationFilter;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.BranchService;
import com.project.Backend_BookMyHotel.service.PromotionService;
import com.project.Backend_BookMyHotel.service.ReviewService;
import com.project.Backend_BookMyHotel.service.RoomAvailabilityService;
import com.project.Backend_BookMyHotel.service.UserDataDetailsService;
import com.project.Backend_BookMyHotel.service.HotelManagementAccessService;
import com.project.Backend_BookMyHotel.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.cache.CacheManager;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {
        AvailabilityController.class,
        BranchController.class,
        PromotionController.class
})
@Import({AppConfig.class, JwtAuthenticationFilter.class, UserDataDetailsService.class})
class EndpointSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RoomAvailabilityService availabilityService;

    @MockitoBean
    private BranchService branchService;

    @MockitoBean
    private HotelManagementAccessService accessService;

    @MockitoBean
    private ReviewService reviewService;

    @MockitoBean
    private PromotionService promotionService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private CacheManager cacheManager;

    @BeforeEach
    void setUpPublicResponses() {
        when(availabilityService.generateAvailabilityCalendar(anyLong(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(AvailabilityCalendar.builder().roomId(39L).days(List.of()).build());
        when(availabilityService.calculateTotalPrice(anyLong(), any(LocalDate.class), any(LocalDate.class), any()))
                .thenReturn(RoomPriceResponse.builder()
                        .roomId(39L)
                        .totalPrice(BigDecimal.valueOf(250))
                        .currency("USD")
                        .isAvailable(true)
                        .build());
        doReturn(ResponseEntity.ok("branch")).when(branchService).getBranchById(7L);
        when(promotionService.applyPromotion(any(), any(), any()))
                .thenReturn(PromotionBreakdownResponse.builder().isError(false).build());
        doReturn(ResponseEntity.ok("created")).when(branchService).createBranch(isNull(), any());
    }

    @Test
    void anonymousVisitorCanReadAvailabilityCalendarAndPrice() throws Exception {
        mockMvc.perform(get("/availability/39/calendar")
                        .param("startDate", "2026-08-19")
                        .param("endDate", "2027-02-15"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/availability/39/price")
                        .param("checkIn", "2026-08-19")
                        .param("checkOut", "2026-08-21"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousVisitorCanReadBranchDetailsAndApplyPromotionQuote() throws Exception {
        mockMvc.perform(get("/branch/7"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/promotion/apply")
                        .contentType("application/json")
                        .content("{\"code\":\"SUMMER20\",\"totalPrice\":250,\"hotelId\":1}"))
                .andExpect(status().isOk());
    }

    @Test
    void anonymousVisitorCannotCreateBranch() throws Exception {
        mockMvc.perform(post("/branch/create")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "CUSTOMER")
    void customerCannotCreateBranch() throws Exception {
        mockMvc.perform(post("/branch/create")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ADMIN")
    void adminCanCreateBranch() throws Exception {
        mockMvc.perform(post("/branch/create")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
