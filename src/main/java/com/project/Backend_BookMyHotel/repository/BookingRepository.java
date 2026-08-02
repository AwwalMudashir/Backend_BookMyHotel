package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.Booking;
import com.project.Backend_BookMyHotel.dto.BookingStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long>, JpaSpecificationExecutor<Booking> {

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

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    // Eligibility check for review submission: has this user completed a confirmed stay at a
    // room belonging to this branch, with checkout already in the past?
    boolean existsByUserIdAndRoomBranchIdAndStatusAndCheckOutBefore(
            Long userId, Long branchId, BookingStatus status, LocalDate date);

    Page<Booking> findByUserId(Long userId, Pageable pageable);

    Page<Booking> findByUserIdAndStatus(Long userId, BookingStatus status, Pageable pageable);
    // Hotel Manager Queries (Filters through Room -> Branch -> Hotel)
    Page<Booking> findByRoomBranchHotelId(Long hotelId, Pageable pageable);
    Page<Booking> findByRoomBranchHotelIdAndStatus(Long hotelId, BookingStatus status, Pageable pageable);

    // Analytics: bookings whose check-in falls within [startDate, endDate], scoped to one hotel's
    // rooms (through Room -> Branch -> Hotel) or, when hotelId is null, across every hotel — used
    // for both the per-hotel and platform-wide KPI summaries so there's one query to keep in sync.
    @Query("""
        SELECT b FROM Booking b
        WHERE (:hotelId IS NULL OR b.room.branch.hotel.id = :hotelId)
          AND b.status = :status
          AND b.checkIn BETWEEN :startDate AND :endDate
    """)
    List<Booking> findByHotelAndStatusAndCheckInBetween(
            @Param("hotelId") Long hotelId,
            @Param("status") BookingStatus status,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // Analytics: every booking (any status) actually occupying a room at this hotel on the given
    // date — i.e. checkIn <= date < checkOut, not just bookings that start that day.
    @Query("""
        SELECT b FROM Booking b
        WHERE b.room.branch.hotel.id = :hotelId
          AND b.checkIn <= :date
          AND b.checkOut > :date
    """)
    List<Booking> findByHotelIdAndDateInStay(
            @Param("hotelId") Long hotelId,
            @Param("date") LocalDate date
    );
}
