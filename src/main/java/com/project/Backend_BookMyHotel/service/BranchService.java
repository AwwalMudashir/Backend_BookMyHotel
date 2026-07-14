package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.dto.BranchRequestDto;
import com.project.Backend_BookMyHotel.dto.BranchResponseDto;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BranchService {

    @Autowired
    private BranchRepository branchRepo;

    @Autowired
    private HotelRepository hotelRepo;

    public ResponseEntity<?> getBranches() {
        List<Branch> branches = branchRepo.findAll();
        return ResponseEntity.ok(branches);
    }

    public ResponseEntity<?> getBranchesByHotel(Long hotelId) {
        if (!hotelRepo.existsById(hotelId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Hotel brand with ID " + hotelId + " does not exist.");
        }

        List<Branch> branches = branchRepo.findByHotelId(hotelId);
        List<BranchResponseDto> dtoList = branches.stream()
                .map(this::mapToBranchResponseDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(dtoList);
    }


    public ResponseEntity<?> getBranchById(Long branchId) {
        Optional<Branch> branchOpt = branchRepo.findById(branchId);
        if (branchOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Branch with ID " + branchId + " not found.");
        }

        return ResponseEntity.ok(mapToBranchResponseDto(branchOpt.get()));
    }

    @Transactional
    public ResponseEntity<?> createBranch(Long hotelId, BranchRequestDto request) {
        // Defensive Check: Input validation
        if (request.getCity() == null || request.getCity().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Error: City name is required.");
        }

        Optional<Hotel> hotelOpt = hotelRepo.findById(hotelId);
        if (hotelOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Cannot create branch. Parent Hotel brand with ID " + hotelId + " not found.");
        }

        Branch branch = new Branch();
        branch.setHotel(hotelOpt.get());
        branch.setCity(request.getCity().trim());
        branch.setCountry(request.getCountry() != null ? request.getCountry().trim() : null);
        branch.setAddress(request.getAddress());
        branch.setCheckOutTime(request.getCheckOutTime());

        Branch savedBranch = branchRepo.save(branch);
        return new ResponseEntity<>(savedBranch,HttpStatus.CREATED);
    }

    @Transactional
    public ResponseEntity<?> updateBranch(Long hotelId, Long branchId, BranchRequestDto request) {
        // Step A: Does the parent hotel brand exist?
        if (!hotelRepo.existsById(hotelId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Hotel brand with ID " + hotelId + " does not exist.");
        }

        // Step B: Does the target branch location exist?
        Optional<Branch> branchOpt = branchRepo.findById(branchId);
        if (branchOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Branch with ID " + branchId + " not found.");
        }

        Branch branch = branchOpt.get();

        // Verify that this branch actually belongs to the hotel passed in the request
        if (!branch.getHotel().getId().equals(hotelId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Error: Security restriction. Branch ID " + branchId + " does not belong to Hotel ID " + hotelId + ".");
        }

        if (request.getCity() != null && !request.getCity().trim().isEmpty()) {
            branch.setCity(request.getCity().trim());
        }
        if (request.getCountry() != null) {
            branch.setCountry(request.getCountry().trim());
        }
        branch.setAddress(request.getAddress());
        branch.setCheckOutTime(request.getCheckOutTime());

        Branch updatedBranch = branchRepo.save(branch);
        return ResponseEntity.ok(mapToBranchResponseDto(updatedBranch));
    }


    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteBranch(Long hotelId, Long branchId) {
        Optional<Branch> branchOpt = branchRepo.findById(branchId);
        if (branchOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Branch with ID " + branchId + " not found.");
        }

        Branch branch = branchOpt.get();

        if (!branch.getHotel().getId().equals(hotelId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Error: Security restriction. Cannot delete. Branch ID " + branchId + " does not belong to Hotel ID " + hotelId + ".");
        }

        branchRepo.delete(branch);
        return ResponseEntity.ok("Success: Branch location with ID " + branchId + " has been completely removed.");
    }

    private BranchResponseDto mapToBranchResponseDto(Branch branch) {
        BranchResponseDto dto = new BranchResponseDto();
        dto.setId(branch.getId());
        dto.setCity(branch.getCity());
        dto.setCountry(branch.getCountry());
        dto.setAddress(branch.getAddress());
        dto.setCheckOutTime(branch.getCheckOutTime());

        if (branch.getHotel() != null) {
            dto.setHotelId(branch.getHotel().getId());
            dto.setHotelName(branch.getHotel().getName());
        }
        return dto;
    }

}
