package com.project.Backend_BookMyHotel.repository;

import com.project.Backend_BookMyHotel.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long>, JpaSpecificationExecutor<Room> {
    List<Room> findByBranchId(Long branchId);

    List<Room> findByBranchIdAndActiveTrue(Long branchId);

    Optional<Room> findByRoomType(String roomType);


    // Find by the new public-facing room identifier (room.roomId)
    Optional<Room> findByRoomId(String roomId);

    @Query(value = """
            SELECT DISTINCT COALESCE(NULLIF(UPPER(r.currency), ''), UPPER(b.currency))
            FROM rooms r
            JOIN branches b ON b.id = r.branch_id
            WHERE r.is_active = true
              AND (:city IS NULL OR LOWER(CAST(b.city AS text)) = LOWER(:city))
              AND (:country IS NULL OR LOWER(CAST(b.country AS text)) = LOWER(:country))
              AND (:filterByHotels = false OR b.hotel_id IN (:hotelIds))
            """, nativeQuery = true)
    List<String> findDistinctEffectiveCurrencies(
            @Param("city") String city,
            @Param("country") String country,
            @Param("filterByHotels") boolean filterByHotels,
            @Param("hotelIds") List<Long> hotelIds);
}
