package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.SustainabilityTag;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.Role;
import com.project.Backend_BookMyHotel.dto.SustainabilityTagRequest;
import com.project.Backend_BookMyHotel.dto.SustainabilityTagResponse;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import com.project.Backend_BookMyHotel.repository.SustainabilityTagRepository;
import com.project.Backend_BookMyHotel.service.SustainabilityTagService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SustainabilityTagServiceTest {
    @Mock SustainabilityTagRepository tagRepository;
    @Mock HotelRepository hotelRepository;
    @Mock BranchRepository branchRepository;
    @InjectMocks SustainabilityTagService tagService;

    @Test
    void managerCreatesHotelWideTagForAssignedHotel() {
        Hotel hotel = hotel(7L, "Assigned Hotel");
        User manager = manager(hotel);
        when(hotelRepository.findById(7L)).thenReturn(Optional.of(hotel));
        when(tagRepository.save(any(SustainabilityTag.class))).thenAnswer(invocation -> {
            SustainabilityTag tag = invocation.getArgument(0);
            tag.setId(11L);
            return tag;
        });

        ResponseEntity<?> response = tagService.create(
                new SustainabilityTagRequest(null, null, true, "Solar powered", "Uses renewable energy"),
                manager);

        SustainabilityTagResponse body = (SustainabilityTagResponse) response.getBody();
        assertEquals(201, response.getStatusCode().value());
        assertEquals(7L, body.hotelId());
        assertEquals(true, body.allBranches());
    }

    @Test
    void managerCannotTargetAnotherHotel() {
        User manager = manager(hotel(7L, "Assigned Hotel"));

        assertThrows(AccessDeniedException.class, () -> tagService.create(
                new SustainabilityTagRequest(99L, null, true, "Solar powered", null), manager));
    }

    private Hotel hotel(Long id, String name) {
        Hotel hotel = new Hotel();
        hotel.setId(id);
        hotel.setName(name);
        return hotel;
    }

    private User manager(Hotel hotel) {
        User user = new User();
        user.setRole(Role.HOTEL_MANAGER);
        user.setManagedHotel(hotel);
        return user;
    }
}
