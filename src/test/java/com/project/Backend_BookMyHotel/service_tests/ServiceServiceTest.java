package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.domain.User;
import com.project.Backend_BookMyHotel.dto.CreateServiceRequest;
import com.project.Backend_BookMyHotel.dto.ServiceResponse;
import com.project.Backend_BookMyHotel.dto.ServiceType;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.repository.ServiceRepository;
import com.project.Backend_BookMyHotel.service.ServiceService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class ServiceServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @Mock
    private BranchRepository branchRepository;

    @InjectMocks
    private ServiceService serviceService;

    private Hotel managedHotel;
    private Branch branch;
    private User manager;

    @BeforeEach
    void setUp() {
        managedHotel = new Hotel();
        managedHotel.setId(1000L);
        managedHotel.setName("Grand Hotel");

        branch = new Branch();
        branch.setId(10L);
        branch.setHotel(managedHotel);

        manager = new User();
        manager.setId(1L);
        manager.setManagedHotel(managedHotel);
    }

    @Test
    void getServicesByBranch_Success_ReturnsMappedServices() {
        com.project.Backend_BookMyHotel.domain.Service spa = new com.project.Backend_BookMyHotel.domain.Service();
        spa.setId(200L);
        spa.setBranch(branch);
        spa.setName("Spa Access");
        spa.setPrice(BigDecimal.valueOf(50));
        spa.setServiceType(ServiceType.SPA);

        Mockito.when(branchRepository.existsById(10L)).thenReturn(true);
        Mockito.when(serviceRepository.findByBranchId(10L)).thenReturn(List.of(spa));

        ResponseEntity<?> response = serviceService.getServicesByBranch(10L);

        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        List<ServiceResponse> body = (List<ServiceResponse>) response.getBody();
        Assertions.assertEquals(1, body.size());
        Assertions.assertEquals("Spa Access", body.get(0).getName());
        Assertions.assertEquals(10L, body.get(0).getBranchId());
    }

    @Test
    void getServicesByBranch_WhenBranchNotFound_ReturnsNotFound() {
        Mockito.when(branchRepository.existsById(999L)).thenReturn(false);

        ResponseEntity<?> response = serviceService.getServicesByBranch(999L);

        Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Mockito.verify(serviceRepository, Mockito.never()).findByBranchId(Mockito.anyLong());
    }

    @Test
    void createService_Success_PersistsServiceForManagersHotel() {
        Mockito.when(branchRepository.findById(10L)).thenReturn(Optional.of(branch));
        Mockito.when(serviceRepository.save(Mockito.any(com.project.Backend_BookMyHotel.domain.Service.class)))
                .thenAnswer(inv -> {
                    com.project.Backend_BookMyHotel.domain.Service saved = inv.getArgument(0);
                    saved.setId(300L);
                    return saved;
                });

        CreateServiceRequest request = new CreateServiceRequest(
                10L, "Airport Pickup", "One-way transfer", BigDecimal.valueOf(35), ServiceType.CAR_HIRE);

        ResponseEntity<?> response = serviceService.createService(request, manager);

        Assertions.assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ServiceResponse body = (ServiceResponse) response.getBody();
        Assertions.assertEquals(300L, body.getId());
        Assertions.assertEquals("Airport Pickup", body.getName());
        Assertions.assertEquals(10L, body.getBranchId());
        Assertions.assertEquals(ServiceType.CAR_HIRE, body.getServiceType());
    }

    @Test
    void createService_WhenManagerHasNoManagedHotel_ReturnsBadRequest() {
        manager.setManagedHotel(null);

        CreateServiceRequest request = new CreateServiceRequest(
                10L, "Airport Pickup", null, BigDecimal.valueOf(35), ServiceType.CAR_HIRE);

        ResponseEntity<?> response = serviceService.createService(request, manager);

        Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Mockito.verify(serviceRepository, Mockito.never()).save(Mockito.any(com.project.Backend_BookMyHotel.domain.Service.class));
    }

    @Test
    void createService_WhenBranchBelongsToDifferentHotel_ReturnsForbidden() {
        Hotel otherHotel = new Hotel();
        otherHotel.setId(2000L);
        branch.setHotel(otherHotel);

        Mockito.when(branchRepository.findById(10L)).thenReturn(Optional.of(branch));

        CreateServiceRequest request = new CreateServiceRequest(
                10L, "Airport Pickup", null, BigDecimal.valueOf(35), ServiceType.CAR_HIRE);

        ResponseEntity<?> response = serviceService.createService(request, manager);

        Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        Mockito.verify(serviceRepository, Mockito.never()).save(Mockito.any(com.project.Backend_BookMyHotel.domain.Service.class));
    }

    @Test
    void createService_WhenBranchNotFound_ThrowsNoSuchElementException() {
        Mockito.when(branchRepository.findById(999L)).thenReturn(Optional.empty());

        CreateServiceRequest request = new CreateServiceRequest(
                999L, "Airport Pickup", null, BigDecimal.valueOf(35), ServiceType.CAR_HIRE);

        Assertions.assertThrows(NoSuchElementException.class,
                () -> serviceService.createService(request, manager));
    }
}
