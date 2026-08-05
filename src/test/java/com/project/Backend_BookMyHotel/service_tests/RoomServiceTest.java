package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.dto.RoomResponseDto;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.RoomRepository;
import com.project.Backend_BookMyHotel.service.RoomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@ExtendWith(MockitoExtension.class)
public class RoomServiceTest {

    @Mock
    private RoomRepository roomRepo;

    @Mock
    private BranchRepository branchRepo;

    @InjectMocks
    private RoomService roomService;

    private Room room;
    private Branch branch;

    @BeforeEach
    void setUp() {
        branch = new Branch();
        branch.setId(10L);
        branch.setName("Central Branch");

        room = new Room();
        room.setId(100L);
        room.setBranch(branch);
        room.setRoomType("Deluxe");
        room.setPricePerNight(BigDecimal.valueOf(120));
        room.setRoomId("ABcd1234");
    }

    @Test
    void getRoomById_withNumericId_resolvesById() {
        Mockito.when(roomRepo.findById(100L)).thenReturn(Optional.of(room));

        ResponseEntity<?> res = roomService.getRoomById("100");
        assertEquals(200, res.getStatusCode());
        RoomResponseDto body = (RoomResponseDto) res.getBody();
        assertEquals(100L, body.getRoomNumber());
    }

    @Test
    void getRoomById_withRoomIdString_resolvesByRoomId() {
        Mockito.when(roomRepo.findById(100L)).thenReturn(Optional.empty());
        Mockito.when(roomRepo.findByRoomId("ABcd1234")).thenReturn(Optional.of(room));

        ResponseEntity<?> res = roomService.getRoomById("ABcd1234");
        assertEquals(200, res.getStatusCode());
        RoomResponseDto body = (RoomResponseDto) res.getBody();
        assertEquals("ABcd1234", body.getRoomId());
    }
}
