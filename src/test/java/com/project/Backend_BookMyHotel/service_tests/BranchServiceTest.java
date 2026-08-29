package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.dto.ServiceResponse;
import com.project.Backend_BookMyHotel.dto.RoomResponse;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.ServiceRepository;
import com.project.Backend_BookMyHotel.service.BranchService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class BranchServiceTest {

    @Mock
    private BranchRepository branchRepo;

    @Mock
    private ServiceRepository serviceRepo;

    @InjectMocks
    private BranchService branchService;

    @Test
    void getRoomsByBranchId_IncludesPublicRoomIdForManagerSelection() {
        Hotel hotel = new Hotel();
        hotel.setId(5L);

        Branch branch = new Branch();
        branch.setId(10L);
        branch.setHotel(hotel);
        branch.setCurrency("USD");

        Room room = new Room();
        room.setId(39L);
        room.setRoomId("Rm8Xa21Q");
        room.setRoomType("Deluxe");
        room.setPricePerNight(BigDecimal.valueOf(220));
        room.setActive(true);
        room.setBranch(branch);
        branch.setRooms(List.of(room));

        Mockito.when(branchRepo.findByIdWithRooms(10L)).thenReturn(Optional.of(branch));

        List<RoomResponse> result = branchService.getRoomsByBranchId(10L);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals(39L, result.get(0).getId());
        Assertions.assertEquals("Rm8Xa21Q", result.get(0).getRoomId());
        Assertions.assertEquals("Deluxe", result.get(0).getRoomType());
        System.out.println("PASS: manager room list returned public room ID Rm8Xa21Q for Deluxe room.");
    }

    @Test
    void getServicesByBranchId_Success_ReturnsMappedServiceResponses() {
        Branch branch = new Branch();
        branch.setId(10L);
        Hotel hotel = new Hotel();
        hotel.setId(5L);
        branch.setHotel(hotel);

        com.project.Backend_BookMyHotel.domain.Service spa = new com.project.Backend_BookMyHotel.domain.Service();
        spa.setId(1L);
        spa.setBranch(branch);
        spa.setHotel(hotel);
        spa.setName("Spa");
        spa.setPrice(BigDecimal.valueOf(50));
        branch.setServices(List.of(spa));

        Mockito.when(branchRepo.findById(10L)).thenReturn(Optional.of(branch));
        Mockito.when(serviceRepo.findAvailableForBranch(5L, 10L)).thenReturn(List.of(spa));

        List<ServiceResponse> result = branchService.getServicesByBranchId(10L);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals("Spa", result.get(0).getName());
        Assertions.assertEquals(10L, result.get(0).getBranchId());
    }

    @Test
    void getServicesByBranchId_WhenBranchNotFound_ThrowsEntityNotFoundException() {
        Mockito.when(branchRepo.findById(999L)).thenReturn(Optional.empty());

        Assertions.assertThrows(EntityNotFoundException.class,
                () -> branchService.getServicesByBranchId(999L));
    }
}
