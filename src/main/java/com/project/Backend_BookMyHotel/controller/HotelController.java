package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.dto.BranchResponse;
import com.project.Backend_BookMyHotel.dto.HotelRequestDto;
import com.project.Backend_BookMyHotel.service.BranchService;
import com.project.Backend_BookMyHotel.service.HotelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/hotel")
public class HotelController {

    @Autowired
    private HotelService hotelService;

    @Autowired
    private BranchService branchService;

    @GetMapping("/all")
    public ResponseEntity<?> getAllHotels(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size)
    {
        return hotelService.getAllHotels(page,size);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getHotelById(@RequestParam Long id) {
        return hotelService.getHotelById(id);
    }

    @GetMapping("/{id}/branches")
    public ResponseEntity<List<BranchResponse>> getBranchesByHotel(
            @PathVariable Long id) {
        List<BranchResponse> branches = branchService.getBranchesByHotelId(id);
        return ResponseEntity.ok(branches);
    }

    @PostMapping("/create")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createHotel(HotelRequestDto request){
        return hotelService.createHotel(request);
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> updateHotel(Long id,HotelRequestDto request){
        return hotelService.updateHotel(id, request);
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<?> deleteHotel(@RequestParam Long id){
        return hotelService.deleteHotel(id);
    }
}
