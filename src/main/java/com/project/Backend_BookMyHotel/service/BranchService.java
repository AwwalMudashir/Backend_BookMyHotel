package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.Room;
import com.project.Backend_BookMyHotel.dto.*;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import com.project.Backend_BookMyHotel.repository.ServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class BranchService {

    @Autowired
    private BranchRepository branchRepo;

    @Autowired
    private HotelRepository hotelRepo;

    @Autowired
    private ServiceRepository serviceRepo;

    @Autowired
    private ExchangeRateService exchangeRateService;

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

    public List<BranchResponse> getBranchesByHotelId(Long hotelId) {
        List<Branch> branches = branchRepo.findByHotelId(hotelId);
        return branches.stream()
                .map(this::toBranchResponse)
                .collect(Collectors.toList());
    }

    public List<RoomResponse> getRoomsByBranchId(Long branchId) {
        Branch branch = branchRepo.findByIdWithRooms(branchId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Branch not found with id: " + branchId));

        return branch.getRooms().stream()
                .filter(room -> !Boolean.FALSE.equals(room.getActive()))
                .map(this::toRoomResponse)
                .collect(Collectors.toList());
    }

    public List<ServiceResponse> getServicesByBranchId(Long branchId) {
        Branch branch = branchRepo.findById(branchId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Branch not found with id: " + branchId));

        return serviceRepo.findAvailableForBranch(branch.getHotel().getId(), branchId).stream()
                .map(this::toServiceResponse)
                .collect(Collectors.toList());
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
        branch.setName(request.getName());
        branch.setHotel(hotelOpt.get());
        branch.setCity(request.getCity().trim());
        branch.setCountry(request.getCountry() != null ? request.getCountry().trim() : null);
        branch.setAddress(request.getAddress());
        branch.setCurrency(exchangeRateService.requireSupportedCurrency(request.getCurrency()));
        branch.setCheckInTime(request.getCheckInTime());
        branch.setCheckOutTime(request.getCheckOutTime());
        branch.setEcoCertified(request.getEcoCertified() != null ? request.getEcoCertified() : false);
        branch.setEcoTags(request.getEcoTags());
        branch.setEcoScore(request.getEcoScore());

        Branch savedBranch = branchRepo.save(branch);
        return new ResponseEntity<>(savedBranch,HttpStatus.CREATED);
    }

    @Transactional
    public ResponseEntity<?> updateBranch(Long hotelId, Long branchId, BranchRequestDto request) {
        // Step B: Does the target branch location exist?
        Optional<Branch> branchOpt = branchRepo.findById(branchId);
        if (branchOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Branch with ID " + branchId + " not found.");
        }

        Branch branch = branchOpt.get();

        // Verify that this branch actually belongs to the hotel passed in the request (if provided)
        if (hotelId != null && !branch.getHotel().getId().equals(hotelId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Error: Security restriction. Branch ID " + branchId + " does not belong to Hotel ID " + hotelId + ".");
        }

        if (request.getCity() != null && !request.getCity().trim().isEmpty()) {
            branch.setCity(request.getCity().trim());
        }
        if (request.getCountry() != null) {
            branch.setCountry(request.getCountry().trim());
        }
        if (request.getCurrency() != null && !request.getCurrency().isBlank()) {
            branch.setCurrency(exchangeRateService.requireSupportedCurrency(request.getCurrency()));
        }

        branch.setName(request.getName());
        branch.setAddress(request.getAddress());
        branch.setCheckInTime(request.getCheckInTime());
        branch.setCheckOutTime(request.getCheckOutTime());

        if (request.getEcoCertified() != null) {
            branch.setEcoCertified(request.getEcoCertified());
        }
        if (request.getEcoTags() != null) {
            branch.setEcoTags(request.getEcoTags());
        }
        if (request.getEcoScore() != null) {
            branch.setEcoScore(request.getEcoScore());
        }

        Branch updatedBranch = branchRepo.save(branch);
        return ResponseEntity.ok(mapToBranchResponseDto(updatedBranch));
    }

    @Transactional
    @PreAuthorize("hasAnyAuthority('ADMIN','HOTEL_MANAGER')")
    public ResponseEntity<?> deleteBranch(Long hotelId, Long branchId) {
        Optional<Branch> branchOpt = branchRepo.findById(branchId);
        if (branchOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Error: Branch with ID " + branchId + " not found.");
        }

        Branch branch = branchOpt.get();

        if (hotelId != null && !branch.getHotel().getId().equals(hotelId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Error: Security restriction. Cannot delete. Branch ID " + branchId + " does not belong to Hotel ID " + hotelId + ".");
        }

        branchRepo.delete(branch);
        return ResponseEntity.ok("Success: Branch location with ID " + branchId + " has been completely removed.");
    }

    private BranchResponseDto mapToBranchResponseDto(Branch branch) {
        BranchResponseDto dto = new BranchResponseDto();
        dto.setId(branch.getId());
        dto.setName(branch.getName());
        dto.setCity(branch.getCity());
        dto.setCountry(branch.getCountry());
        dto.setAddress(branch.getAddress());
        dto.setCurrency(branch.getCurrency());
        dto.setCheckOutTime(branch.getCheckOutTime());
        dto.setEcoCertified(branch.getEcoCertified());
        dto.setEcoTags(branch.getEcoTags());
        dto.setEcoScore(branch.getEcoScore());

        if (branch.getHotel() != null) {
            dto.setHotelId(branch.getHotel().getId());
            dto.setHotelName(branch.getHotel().getName());
        }
        return dto;
    }

    private BranchResponse toBranchResponse(Branch branch) {
        return BranchResponse.builder()
                .id(branch.getId())
                .hotelId(branch.getHotel().getId())
                .name(branch.getName())
                .city(branch.getCity())
                .address(branch.getAddress())
                .currency(branch.getCurrency())
                .build();
    }

    private RoomResponse toRoomResponse(Room room) {
        return RoomResponse.builder()
                .id(room.getId())
                .branchId(room.getBranch().getId())
                .roomType(room.getRoomType())
                .description(room.getDescription())
                .maxOccupancy(room.getMaxOccupancy())
                .pricePerNight(room.getPricePerNight())
                .currency(room.getCurrency() != null && !room.getCurrency().isBlank()
                        ? room.getCurrency() : room.getBranch().getCurrency())
                .amenities(room.getAmenities())
                .images(room.getImages())
                .tags(room.getTags())
                .build();
    }

    // Fully-qualified to avoid clashing with the org.springframework.stereotype.Service import above
    private ServiceResponse toServiceResponse(com.project.Backend_BookMyHotel.domain.Service service) {
        Hotel hotel = service.getHotel() != null ? service.getHotel()
                : service.getBranch() != null ? service.getBranch().getHotel() : null;
        return ServiceResponse.builder()
                .id(service.getId())
                .hotelId(hotel != null ? hotel.getId() : null)
                .hotelName(hotel != null ? hotel.getName() : null)
                .branchId(service.getBranch() != null ? service.getBranch().getId() : null)
                .branchName(service.getBranch() != null ? service.getBranch().getName() : null)
                .allBranches(service.getBranch() == null)
                .name(service.getName())
                .description(service.getDescription())
                .price(service.getPrice())
                .serviceType(service.getServiceType())
                .active(service.getActive())
                .build();
    }
}
