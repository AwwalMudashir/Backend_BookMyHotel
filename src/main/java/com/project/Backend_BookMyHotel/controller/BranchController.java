package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.dto.BranchRequestDto;
import com.project.Backend_BookMyHotel.dto.HotelRequestDto;
import com.project.Backend_BookMyHotel.service.BranchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/branch")
public class BranchController {
    @Autowired
    private BranchService branchService;

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> getBranches(){
        return branchService.getBranches();
    }

    @PostMapping("/byHotel/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> getBranchesByHotel(Long id){
        return branchService.getBranchesByHotel(id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> getBranchById(Long id){
        return branchService.getBranchById(id);
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> createBranch(Long hotelId, BranchRequestDto request){
        return branchService.createBranch(hotelId, request);
    }

    @PutMapping("/update")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> updateBranch(Long hotelId, Long branchId, BranchRequestDto request){
        return branchService.updateBranch(hotelId, branchId, request);
    }

    @DeleteMapping("/delete")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> deleteBranch(Long hotelId, Long branchId){
        return branchService.deleteBranch(hotelId, branchId);
    }


}
