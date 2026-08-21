package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.SustainabilityTag;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.SustainabilityTagRequest;
import com.project.Backend_BookMyHotel.dto.SustainabilityTagResponse;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import com.project.Backend_BookMyHotel.repository.SustainabilityTagRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

@Service
public class SustainabilityTagService {
    private final SustainabilityTagRepository tagRepository;
    private final HotelRepository hotelRepository;
    private final BranchRepository branchRepository;

    public SustainabilityTagService(SustainabilityTagRepository tagRepository,
                                    HotelRepository hotelRepository,
                                    BranchRepository branchRepository) {
        this.tagRepository = tagRepository;
        this.hotelRepository = hotelRepository;
        this.branchRepository = branchRepository;
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getForManagement(Long requestedHotelId, User actor) {
        if (actor.isAdmin() && requestedHotelId == null) {
            return ResponseEntity.ok(tagRepository.findByActiveTrueOrderByHotelNameAscNameAsc()
                    .stream().map(this::toResponse).toList());
        }
        Long hotelId = resolveHotelId(requestedHotelId, actor);
        return ResponseEntity.ok(tagRepository.findByHotelIdAndActiveTrueOrderByNameAsc(hotelId)
                .stream().map(this::toResponse).toList());
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getForBranch(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NoSuchElementException("Branch not found with ID: " + branchId));
        return ResponseEntity.ok(tagRepository.findAvailableForBranch(branch.getHotel().getId(), branchId)
                .stream().map(this::toResponse).toList());
    }

    @Transactional
    public ResponseEntity<?> create(SustainabilityTagRequest request, User actor) {
        Scope scope = resolveScope(request, actor);
        SustainabilityTag tag = new SustainabilityTag();
        apply(tag, scope, request);
        tag.setActive(true);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(tagRepository.save(tag)));
    }

    @Transactional
    public ResponseEntity<?> update(Long tagId, SustainabilityTagRequest request, User actor) {
        SustainabilityTag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new NoSuchElementException("Sustainability tag not found with ID: " + tagId));
        requireHotelAccess(actor, tag.getHotel().getId());
        Scope scope = resolveScope(request, actor);
        apply(tag, scope, request);
        return ResponseEntity.ok(toResponse(tagRepository.save(tag)));
    }

    @Transactional
    public ResponseEntity<?> deactivate(Long tagId, User actor) {
        SustainabilityTag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new NoSuchElementException("Sustainability tag not found with ID: " + tagId));
        requireHotelAccess(actor, tag.getHotel().getId());
        tag.setActive(false);
        tagRepository.save(tag);
        return ResponseEntity.noContent().build();
    }

    private Scope resolveScope(SustainabilityTagRequest request, User actor) {
        Long hotelId = resolveHotelId(request.hotelId(), actor);
        Hotel hotel = hotelRepository.findById(hotelId)
                .orElseThrow(() -> new NoSuchElementException("Hotel not found with ID: " + hotelId));
        if (request.allBranches()) {
            return new Scope(hotel, null);
        }
        if (request.branchId() == null) {
            throw new IllegalArgumentException("Select a branch or choose all branches.");
        }
        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> new NoSuchElementException("Branch not found with ID: " + request.branchId()));
        if (!branch.getHotel().getId().equals(hotelId)) {
            throw new IllegalArgumentException("The selected branch does not belong to the selected hotel.");
        }
        return new Scope(hotel, branch);
    }

    private Long resolveHotelId(Long requestedHotelId, User actor) {
        if (actor.isAdmin()) {
            if (requestedHotelId == null) {
                throw new IllegalArgumentException("Hotel ID is required.");
            }
            return requestedHotelId;
        }
        if (actor.getManagedHotel() == null) {
            throw new IllegalStateException("Your manager account is not assigned to a hotel.");
        }
        Long hotelId = actor.getManagedHotel().getId();
        if (requestedHotelId != null && !requestedHotelId.equals(hotelId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only manage sustainability tags for your assigned hotel.");
        }
        return hotelId;
    }

    private void requireHotelAccess(User actor, Long hotelId) {
        if (!actor.isAdmin() && (actor.getManagedHotel() == null
                || !actor.getManagedHotel().getId().equals(hotelId))) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You can only manage sustainability tags for your assigned hotel.");
        }
    }

    private void apply(SustainabilityTag tag, Scope scope, SustainabilityTagRequest request) {
        tag.setHotel(scope.hotel());
        tag.setBranch(scope.branch());
        tag.setName(request.name().trim());
        tag.setDescription(request.description() == null || request.description().isBlank()
                ? null : request.description().trim());
    }

    private SustainabilityTagResponse toResponse(SustainabilityTag tag) {
        return SustainabilityTagResponse.builder()
                .id(tag.getId())
                .hotelId(tag.getHotel().getId())
                .hotelName(tag.getHotel().getName())
                .branchId(tag.getBranch() == null ? null : tag.getBranch().getId())
                .branchName(tag.getBranch() == null ? null : tag.getBranch().getName())
                .allBranches(tag.getBranch() == null)
                .name(tag.getName())
                .description(tag.getDescription())
                .active(Boolean.TRUE.equals(tag.getActive()))
                .build();
    }

    private record Scope(Hotel hotel, Branch branch) {}
}
