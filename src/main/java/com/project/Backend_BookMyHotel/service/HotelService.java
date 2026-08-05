package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.dto.BranchSummaryDto;
import com.project.Backend_BookMyHotel.dto.HotelDetailDto;
import com.project.Backend_BookMyHotel.dto.HotelRequestDto;
import com.project.Backend_BookMyHotel.dto.HotelSummary;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
public class HotelService {

    @Autowired
    private HotelRepository hotelRepo;

    public ResponseEntity<Page<HotelSummary>> getAllHotels(int page, int size) {
        int adjustedPage = page > 0 ? page - 1 : 0;

        Pageable pageable = PageRequest.of(adjustedPage, size);
        return ResponseEntity.ok(hotelRepo.findBy(pageable));
    }

    public ResponseEntity<?> getHotelById(Long id) {
        Hotel hotel = hotelRepo.findById(id).orElseThrow(() -> new NoSuchElementException("Hotel ID Not Found"));
        return new ResponseEntity<>(mapToHotelDetailDto(hotel), HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<?> createHotel(HotelRequestDto request) {
        Hotel hotel = new Hotel();
        hotel.setName(request.getName());
        hotel.setDescription(request.getDescription());
        hotel.setStarRating(request.getStarRating());
        hotel.setLogoUrl(request.getLogoUrl());

        Hotel savedHotel = hotelRepo.save(hotel);
        return new ResponseEntity<>(mapToHotelDetailDto(savedHotel),HttpStatus.OK);
    }

    @Transactional
    public ResponseEntity<?> updateHotel(Long id, HotelRequestDto request) {
        Hotel hotel = hotelRepo.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Cannot update. Hotel not found with ID: " + id));

        hotel.setName(request.getName());
        hotel.setDescription(request.getDescription());
        hotel.setStarRating(request.getStarRating());
        hotel.setLogoUrl(request.getLogoUrl());

        Hotel updatedHotel = hotelRepo.save(hotel);
        return new ResponseEntity<>(mapToHotelDetailDto(updatedHotel),HttpStatus.OK);
    }


    @Transactional
    public ResponseEntity<?> deleteHotel(Long id) {
        if (!hotelRepo.existsById(id)) {
           return new ResponseEntity<>("Hotel ID doesn't exist", HttpStatus.BAD_REQUEST);
        }

        hotelRepo.deleteById(id);
        return new ResponseEntity<>("Hotel Deleted Successfully !", HttpStatus.OK);
    }

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
