package com.project.Backend_BookMyHotel.service;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.CreateServiceRequest;
import com.project.Backend_BookMyHotel.dto.ServiceResponse;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
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

    public ResponseEntity<?> getServicesByBranch(Long branchId) {
        if (!branchRepository.existsById(branchId)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Branch not found with ID: " + branchId);
        }

        List<ServiceResponse> services = serviceRepository.findByBranchId(branchId).stream()
                .map(this::toServiceResponse)
                .toList();

        return ResponseEntity.ok(services);
    }

    @Transactional
    public ResponseEntity<?> createService(CreateServiceRequest request, User manager) {
        if (manager.getManagedHotel() == null) {
            return ResponseEntity.badRequest().body("You are not assigned to manage a hotel.");
        }

        Branch branch = branchRepository.findById(request.branchId())
                .orElseThrow(() -> new NoSuchElementException("Branch not found with ID: " + request.branchId()));

        // The service being created must belong to a branch of the hotel this manager actually
        // manages — otherwise any hotel manager could add paid services to a competitor's branch.
        if (!branch.getHotel().getId().equals(manager.getManagedHotel().getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Branch " + branch.getId() + " does not belong to the hotel you manage.");
        }

        // Fully-qualified to avoid clashing with the org.springframework.stereotype.Service import above.
        com.project.Backend_BookMyHotel.domain.Service service = new com.project.Backend_BookMyHotel.domain.Service();
        service.setBranch(branch);
        service.setName(request.name().trim());
        service.setDescription(request.description());
        service.setPrice(request.price());
        service.setServiceType(request.serviceType());

        com.project.Backend_BookMyHotel.domain.Service saved = serviceRepository.save(service);

        return ResponseEntity.status(HttpStatus.CREATED).body(toServiceResponse(saved));
    }

    private ServiceResponse toServiceResponse(com.project.Backend_BookMyHotel.domain.Service service) {
        return ServiceResponse.builder()
                .id(service.getId())
                .branchId(service.getBranch().getId())
                .name(service.getName())
                .description(service.getDescription())
                .price(service.getPrice())
                .serviceType(service.getServiceType())
                .build();
    }
}
