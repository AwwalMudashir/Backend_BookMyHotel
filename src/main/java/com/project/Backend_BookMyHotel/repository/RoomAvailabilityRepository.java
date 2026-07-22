package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.RoomAvailability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface RoomAvailabilityRepository extends JpaRepository<RoomAvailability,Long> {
    List<RoomAvailability> findByRoomIdAndDateBetween(
            Long roomId, LocalDate startDate, LocalDate endDate
    );

    Optional<RoomAvailability> findByRoomIdAndDate(Long roomId, LocalDate date);

    // Used during booking to mark dates unavailable
    @Query("SELECT ra FROM RoomAvailability ra WHERE ra.room.id = :roomId " +
            "AND ra.date >= :checkIn AND ra.date < :checkOut")
    List<RoomAvailability> findByRoomIdAndDateRange(
            @Param("roomId") Long roomId,
            @Param("checkIn") LocalDate checkIn,
            @Param("checkOut") LocalDate checkOut
    );
}
