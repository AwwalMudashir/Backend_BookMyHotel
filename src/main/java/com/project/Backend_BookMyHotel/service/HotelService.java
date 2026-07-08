package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.dto.BranchSummaryDto;
import com.project.Backend_BookMyHotel.dto.HotelDetailDto;
import com.project.Backend_BookMyHotel.dto.HotelSummary;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class HotelService {

    @Autowired
    private HotelRepository hotelRepo;

    public ResponseEntity<Page<HotelSummary>> getAllHotels(int page, int size, String sortBy) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(sortBy).ascending());

        return ResponseEntity.ok(hotelRepo.findBy(pageable));
    }

    /**
     * Requirement 2: Full hotel details with branches included
     */
    public HotelDetailDto getHotelById(Long id) {
        Hotel hotel = hotelRepo.findByBranchId(id).orElseThrow(() -> new NoSuchElementException("Hotel ID Not Found"));
        return mapToHotelDetailDto(hotel);
    }

    // --- Helper Mapping Methods ---
    private HotelDetailDto mapToHotelDetailDto(Hotel hotel) {
        HotelDetailDto dto = new HotelDetailDto();
        dto.setId(hotel.getId());
        dto.setName(hotel.getName());
        dto.setDescription(hotel.getDescription());
        dto.setStarRating(hotel.getStarRating());
        dto.setLogoUrl(hotel.getLogoUrl());

        if (hotel.getBranches() != null) {
            dto.setBranches(hotel.getBranches().stream().map(branch -> {
                BranchSummaryDto bDto = new BranchSummaryDto();
                bDto.setId(branch.getId());
                bDto.setCity(branch.getCity());
                bDto.setCountry(branch.getCountry());
                bDto.setAddress(branch.getAddress());
                return bDto;
            }).collect(Collectors.toList()));
        }

        return dto;
    }
}
