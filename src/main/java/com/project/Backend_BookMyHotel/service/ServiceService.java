package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.CreateServiceRequest;
import com.project.Backend_BookMyHotel.dto.ServiceResponse;
import com.project.Backend_BookMyHotel.dto.UpdateServiceRequest;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import com.project.Backend_BookMyHotel.repository.ServiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ServiceService {

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private BranchRepository branchRepository;

    @Autowired
    private HotelRepository hotelRepository;

    @Transactional(readOnly = true)
    public ResponseEntity<?> getServicesByBranch(Long branchId) {
        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NoSuchElementException("Branch not found with ID: " + branchId));

        List<ServiceResponse> services = serviceRepository
                .findAvailableForBranch(branch.getHotel().getId(), branchId)
                .stream()
                .map(this::toServiceResponse)
                .toList();

        return ResponseEntity.ok(services);
    }

    @Transactional(readOnly = true)
    public ResponseEntity<?> getServicesForManagement(Long requestedHotelId, User actor) {
        Long hotelId;
        if (actor.isAdmin()) {
            if (requestedHotelId == null) {
                return ResponseEntity.ok(serviceRepository.findByActiveTrueOrderByHotelNameAscNameAsc()
                        .stream().map(this::toServiceResponse).toList());
            }
            hotelId = requestedHotelId;
        } else {
            if (actor.getManagedHotel() == null) {
                return ResponseEntity.badRequest().body("You are not assigned to manage a hotel.");
            }
            hotelId = actor.getManagedHotel().getId();
            if (requestedHotelId != null && !requestedHotelId.equals(hotelId)) {
                return forbidden("You can only view services for the hotel you manage.");
            }
        }

        if (!hotelRepository.existsById(hotelId)) {
            throw new NoSuchElementException("Hotel not found with ID: " + hotelId);
        }

        return ResponseEntity.ok(serviceRepository.findByHotelIdAndActiveTrueOrderByNameAsc(hotelId)
                .stream().map(this::toServiceResponse).toList());
    }

    @Transactional
    public ResponseEntity<?> createService(CreateServiceRequest request, User actor) {
        if (!actor.isAdmin() && actor.getManagedHotel() == null) {
            return ResponseEntity.badRequest().body("You are not assigned to manage a hotel.");
        }
        Scope scope = resolveScope(request.hotelId(), request.branchId(), request.allBranches(), actor);
        if (scope.error() != null) return scope.error();

        com.project.Backend_BookMyHotel.domain.Service service = new com.project.Backend_BookMyHotel.domain.Service();
        applyValues(service, scope, request.name(), request.description(), request.price(), request.serviceType());
        service.setActive(true);

        return ResponseEntity.status(HttpStatus.CREATED).body(toServiceResponse(serviceRepository.save(service)));
    }

    @Transactional
    public ResponseEntity<?> updateService(Long serviceId, UpdateServiceRequest request, User actor) {
        if (!actor.isAdmin() && actor.getManagedHotel() == null) {
            return ResponseEntity.badRequest().body("You are not assigned to manage a hotel.");
        }
        com.project.Backend_BookMyHotel.domain.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NoSuchElementException("Service not found with ID: " + serviceId));

        if (!canManageHotel(actor, serviceHotelId(service))) {
            return forbidden("You can only update services for the hotel you manage.");
        }

        Scope scope = resolveScope(request.hotelId(), request.branchId(), request.allBranches(), actor);
        if (scope.error() != null) return scope.error();

        applyValues(service, scope, request.name(), request.description(), request.price(), request.serviceType());
        return ResponseEntity.ok(toServiceResponse(serviceRepository.save(service)));
    }

    @Transactional
    public ResponseEntity<?> deactivateService(Long serviceId, User actor) {
        if (!actor.isAdmin() && actor.getManagedHotel() == null) {
            return ResponseEntity.badRequest().body("You are not assigned to manage a hotel.");
        }
        com.project.Backend_BookMyHotel.domain.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new NoSuchElementException("Service not found with ID: " + serviceId));

        if (!canManageHotel(actor, serviceHotelId(service))) {
            return forbidden("You can only delete services for the hotel you manage.");
        }

        service.setActive(false);
        serviceRepository.save(service);
        return ResponseEntity.noContent().build();
    }

    private Scope resolveScope(Long requestedHotelId, Long branchId, boolean allBranches, User actor) {
        Hotel managedHotel = actor.getManagedHotel();

        if (allBranches) {
            if (!actor.isAdmin() && managedHotel == null) {
                return Scope.error(ResponseEntity.badRequest().body("You are not assigned to manage a hotel."));
            }
            if (!actor.isAdmin() && requestedHotelId != null && !requestedHotelId.equals(managedHotel.getId())) {
                return Scope.error(forbidden("You can only create services for the hotel you manage."));
            }

            Long hotelId = actor.isAdmin() ? requestedHotelId : managedHotel.getId();
            if (hotelId == null) {
                return Scope.error(ResponseEntity.badRequest().body("Hotel ID is required for an all-branches service."));
            }
            Hotel hotel = hotelRepository.findById(hotelId)
                    .orElseThrow(() -> new NoSuchElementException("Hotel not found with ID: " + hotelId));
            return Scope.valid(hotel, null);
        }

        if (branchId == null) {
            return Scope.error(ResponseEntity.badRequest().body("Select a branch or choose all branches."));
        }

        Branch branch = branchRepository.findById(branchId)
                .orElseThrow(() -> new NoSuchElementException("Branch not found with ID: " + branchId));
        Hotel hotel = branch.getHotel();

        if (requestedHotelId != null && !requestedHotelId.equals(hotel.getId())) {
            return Scope.error(ResponseEntity.badRequest().body("The selected branch does not belong to the selected hotel."));
        }
        if (!canManageHotel(actor, hotel.getId())) {
            return Scope.error(forbidden("The selected branch does not belong to the hotel you manage."));
        }

        return Scope.valid(hotel, branch);
    }

    private Long serviceHotelId(com.project.Backend_BookMyHotel.domain.Service service) {
        if (service.getHotel() != null) return service.getHotel().getId();
        return service.getBranch() != null ? service.getBranch().getHotel().getId() : null;
    }

    private boolean canManageHotel(User actor, Long hotelId) {
        return actor.isAdmin()
                || (hotelId != null && actor.getManagedHotel() != null
                && actor.getManagedHotel().getId().equals(hotelId));
    }

    private void applyValues(com.project.Backend_BookMyHotel.domain.Service service, Scope scope,
                             String name, String description, java.math.BigDecimal price,
                             com.project.Backend_BookMyHotel.dto.ServiceType serviceType) {
        service.setHotel(scope.hotel());
        service.setBranch(scope.branch());
        service.setName(name.trim());
        service.setDescription(description == null || description.isBlank() ? null : description.trim());
        service.setPrice(price);
        service.setServiceType(serviceType);
    }

    private ResponseEntity<?> forbidden(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message);
    }

    public ServiceResponse toServiceResponse(com.project.Backend_BookMyHotel.domain.Service service) {
        Hotel hotel = service.getHotel() != null
                ? service.getHotel()
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

    private record Scope(Hotel hotel, Branch branch, ResponseEntity<?> error) {
        static Scope valid(Hotel hotel, Branch branch) {
            return new Scope(hotel, branch, null);
        }

        static Scope error(ResponseEntity<?> error) {
            return new Scope(null, null, error);
        }
    }
}
