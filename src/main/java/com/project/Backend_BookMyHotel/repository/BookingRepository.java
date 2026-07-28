package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
        SELECT b FROM Booking b 
        WHERE b.room.id = :roomId 
          AND b.status = :status 
          AND b.checkIn < :endDate 
          AND b.checkOut > :startDate
    """)
    List<Booking> findOverlappingBookings(
            @Param("roomId") Long roomId,
            @Param("status") BookingStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    Booking findByReference(String reference);

    Page<Booking> findByUserId(Long userId, Pageable pageable);

    Page<Booking> findByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);
    // Hotel Manager Queries (Filters through Room -> Branch -> Hotel)
    Page<Booking> findByRoomBranchHotelId(Long hotelId, Pageable pageable);
    Page<Booking> findByRoomBranchHotelIdAndStatus(Long hotelId, BookingStatus status, Pageable pageable);
}
