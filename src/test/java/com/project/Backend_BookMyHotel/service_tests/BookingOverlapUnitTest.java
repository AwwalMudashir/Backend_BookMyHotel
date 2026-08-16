package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import com.project.Backend_BookMyHotel.dto.CreateBookingRequest;
import com.project.Backend_BookMyHotel.dto.Role;
import com.project.Backend_BookMyHotel.dto.RoomPriceResponse;
import com.project.Backend_BookMyHotel.repository.BookingRepository;
import com.project.Backend_BookMyHotel.repository.RoomRepository;
import com.project.Backend_BookMyHotel.repository.UserRepository;
import com.project.Backend_BookMyHotel.service.BookingService;
import com.project.Backend_BookMyHotel.service.RoomAvailabilityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit test evidence for report test case TC-U02: booking-overlap rejection. */
@ExtendWith(MockitoExtension.class)
class BookingOverlapUnitTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoomAvailabilityService availabilityService;

    @InjectMocks
    private BookingService bookingService;

    @Test
    void secondBookingForSameRoomAndDatesIsRejected() {
        LocalDate checkIn = LocalDate.now().plusDays(10);
        LocalDate checkOut = checkIn.plusDays(2);

        Hotel hotel = new Hotel();
        hotel.setId(1L);

        Branch branch = new Branch();
        branch.setId(10L);
        branch.setHotel(hotel);
        branch.setCurrency("GBP");

        Room room = new Room();
        room.setId(100L);
        room.setRoomId("RM-100");
        room.setBranch(branch);
        room.setActive(true);

        User customer = new User();
        customer.setId(5L);
        customer.setEmail("guest@example.com");
        customer.setRole(Role.CUSTOMER);

        Authentication authentication = Mockito.mock(Authentication.class);
        Mockito.when(authentication.getName()).thenReturn(customer.getEmail());
        Mockito.when(userRepository.findByEmail(customer.getEmail())).thenReturn(customer);
        Mockito.when(roomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        Mockito.when(availabilityService.calculateTotalPrice(room.getId(), checkIn, checkOut))
                .thenReturn(RoomPriceResponse.builder()
                        .roomId(room.getId())
                        .checkIn(checkIn)
                        .checkOut(checkOut)
                        .totalNights(2)
                        .totalPrice(BigDecimal.valueOf(300))
                        .currency("GBP")
                        .isAvailable(true)
                        .breakdown(List.of())
                        .build());
        Mockito.when(bookingRepository.save(Mockito.any(Booking.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateBookingRequest request = new CreateBookingRequest(room.getId(), checkIn, checkOut, null);

        Mockito.when(bookingRepository.findOverlappingBookings(
                        room.getId(), BookingStatus.PENDING, checkIn, checkOut))
                .thenReturn(new ArrayList<>());
        Mockito.when(bookingRepository.findOverlappingBookings(
                        room.getId(), BookingStatus.CONFIRMED, checkIn, checkOut))
                .thenReturn(new ArrayList<>());

        ResponseEntity<?> firstAttempt = bookingService.createBooking(authentication, request);
        assertEquals(HttpStatus.CREATED, firstAttempt.getStatusCode());

        Booking existingBooking = Booking.builder()
                .id(999L)
                .room(room)
                .status(BookingStatus.PENDING)
                .checkIn(checkIn)
                .checkOut(checkOut)
                .build();
        Mockito.when(bookingRepository.findOverlappingBookings(
                        room.getId(), BookingStatus.PENDING, checkIn, checkOut))
                .thenReturn(new ArrayList<>(List.of(existingBooking)));

        ResponseEntity<?> secondAttempt = bookingService.createBooking(authentication, request);

        assertEquals(HttpStatus.BAD_REQUEST, secondAttempt.getStatusCode());
        assertTrue(((String) secondAttempt.getBody()).contains("not available"));
        Mockito.verify(bookingRepository, Mockito.times(1)).save(Mockito.any(Booking.class));
    }
}
