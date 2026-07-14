package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.dto.HotelRequestDto;
import com.project.Backend_BookMyHotel.service.HotelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/hotel")
public class HotelController {

    @Autowired
    private HotelService hotelService;

    @GetMapping("/all")
    private ResponseEntity<?> getAllHotels(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size)
    {
        return hotelService.getAllHotels(page,size);
    }


    @GetMapping("/{id}")
    private ResponseEntity<?> getHotelById(@RequestParam Long id) {
        return hotelService.getHotelById(id);
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    private ResponseEntity<?> createHotel(HotelRequestDto request){
        return hotelService.createHotel(request);
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> updateHotel(Long id,HotelRequestDto request){
        return hotelService.updateHotel(id, request);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> deleteHotel(Long id){
        return hotelService.deleteHotel(id);
    }
}
