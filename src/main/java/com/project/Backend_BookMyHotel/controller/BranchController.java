package com.project.Backend_BookMyHotel.controller;

import com.project.Backend_BookMyHotel.dto.BranchRequestDto;
import com.project.Backend_BookMyHotel.dto.BranchReviewsResponse;
import com.project.Backend_BookMyHotel.dto.HotelRequestDto;
import com.project.Backend_BookMyHotel.dto.RoomResponse;
import com.project.Backend_BookMyHotel.dto.ServiceResponse;
import com.project.Backend_BookMyHotel.service.BranchService;
import com.project.Backend_BookMyHotel.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping({"/branch", "/branches"})
public class BranchController {
    @Autowired
    private BranchService branchService;

    @Autowired
    private ReviewService reviewService;

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> getBranches(){
        return branchService.getBranches();
    }

    @PostMapping("/byHotel/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> getBranchesByHotel(@PathVariable Long id){
        return branchService.getBranchesByHotel(id);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> getBranchById(@PathVariable Long id){
        return branchService.getBranchById(id);
    }

    @GetMapping("/{branchId}/rooms")
    public ResponseEntity<List<RoomResponse>> getRoomsByBranch(
            @PathVariable Long branchId) {
        List<RoomResponse> rooms = branchService.getRoomsByBranchId(branchId);
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/{branchId}/reviews")
    public ResponseEntity<BranchReviewsResponse> getReviewsByBranch(
            @PathVariable Long branchId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        BranchReviewsResponse reviews = reviewService.getReviewsByBranch(branchId, PageRequest.of(page, size));
        return ResponseEntity.ok(reviews);
    }

    @GetMapping("/{branchId}/services")
    public ResponseEntity<List<ServiceResponse>> getServicesByBranch(
            @PathVariable Long branchId) {
        List<ServiceResponse> services = branchService.getServicesByBranchId(branchId);
        return ResponseEntity.ok(services);
    }

    @PostMapping("/create")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> createBranch(@RequestBody BranchRequestDto request){
        return branchService.createBranch(request.getHotelId(), request);
    }

    @PutMapping("/update/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> updateBranch(@PathVariable Long id, @RequestBody BranchRequestDto request){
        return branchService.updateBranch(null, id, request);
    }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    private ResponseEntity<?> deleteBranch(@PathVariable Long id){
        return branchService.deleteBranch(null, id);
    }


}
