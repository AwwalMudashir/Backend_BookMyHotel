package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.repository.BookingRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
public class BookingService {

    @Autowired
    private BookingRepository bookingRepo;

    /*
     What @CacheEvict does: The moment createBooking() or cancelBooking() runs successfully,
     @CacheEvict automatically wipes the "availability" bucket in Redis. The very next room
     search is forced to hit PostgreSQL, fetch the fresh room counts, and store the updated
     data back in Redis.
     */

    /*
    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public BookingResponse createBooking(CreateBookingRequest request) {
        // 1. Verify availability & process payment / save booking
        // 2. @CacheEvict triggers after this method executes successfully
        return new BookingResponse();
    }

    @Transactional
    @CacheEvict(value = "availability", allEntries = true)
    public void cancelBooking(Long bookingId) {
        // 1. Mark booking as CANCELLED or delete record
        // 2. @CacheEvict purges the "availability" cache in Redis
    }
    */
}
