package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.dto.RoomResponseDto;
import com.project.Backend_BookMyHotel.repository.BookingRepository;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.RoomAvailabilityRepository;
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
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class RoomServiceTest {

    @Mock
    private RoomRepository roomRepo;

    @Mock
    private BranchRepository branchRepo;

    @Mock
    private BookingRepository bookingRepo;

    @Mock
    private RoomAvailabilityRepository availabilityRepo;

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
        room.setActive(true);
    }

    @Test
    void getRoomById_withNumericId_resolvesById() {
        Mockito.when(roomRepo.findById(100L)).thenReturn(Optional.of(room));

        ResponseEntity<?> res = roomService.getRoomById("100");
        assertEquals(200, res.getStatusCode().value());
        RoomResponseDto body = (RoomResponseDto) res.getBody();
        assertEquals(100L, body.getRoomNumber());
    }

    @Test
    void getRoomById_withRoomIdString_resolvesByRoomId() {
        Mockito.when(roomRepo.findByRoomId("ABcd1234")).thenReturn(Optional.of(room));

        ResponseEntity<?> res = roomService.getRoomById("ABcd1234");
        assertEquals(200, res.getStatusCode().value());
        RoomResponseDto body = (RoomResponseDto) res.getBody();
        assertEquals("ABcd1234", body.getRoomId());
    }

    @Test
    void deleteRoom_withFuturePendingOrConfirmedBooking_returnsConflict() {
        Mockito.when(roomRepo.findById(100L)).thenReturn(Optional.of(room));
        Mockito.when(bookingRepo.existsByRoom_IdAndStatusInAndCheckOutAfter(
                        Mockito.eq(100L), Mockito.anyCollection(), Mockito.any(LocalDate.class)))
                .thenReturn(true);

        ResponseEntity<?> response = roomService.deleteRoom(10L, "100");

        assertEquals(409, response.getStatusCode().value());
        assertTrue(room.getActive());
        Mockito.verify(roomRepo, Mockito.never()).save(Mockito.any(Room.class));
        Mockito.verify(availabilityRepo, Mockito.never()).deleteByRoomId(Mockito.anyLong());
    }

    @Test
    void deleteRoom_withoutFutureActiveBooking_softDeletesRoomAndAvailabilityOnly() {
        Mockito.when(roomRepo.findById(100L)).thenReturn(Optional.of(room));
        Mockito.when(bookingRepo.existsByRoom_IdAndStatusInAndCheckOutAfter(
                        Mockito.eq(100L), Mockito.anyCollection(), Mockito.any(LocalDate.class)))
                .thenReturn(false);

        ResponseEntity<?> response = roomService.deleteRoom(10L, "100");

        assertEquals(200, response.getStatusCode().value());
        assertFalse(room.getActive());
        Mockito.verify(availabilityRepo).deleteByRoomId(100L);
        Mockito.verify(roomRepo).save(room);
        Mockito.verify(roomRepo, Mockito.never()).delete(Mockito.any(Room.class));
    }

    @Test
    void getRoomById_whenRoomIsInactive_returnsNotFound() {
        room.setActive(false);
        Mockito.when(roomRepo.findById(100L)).thenReturn(Optional.of(room));

        ResponseEntity<?> response = roomService.getRoomById("100");

        assertEquals(404, response.getStatusCode().value());
    }
}
