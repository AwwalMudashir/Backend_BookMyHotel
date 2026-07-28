package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.domain.Branch;
import com.project.Backend_BookMyHotel.dto.ServiceResponse;
import com.project.Backend_BookMyHotel.repository.BranchRepository;
import com.project.Backend_BookMyHotel.service.BranchService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
public class BranchServiceTest {

    @Mock
    private BranchRepository branchRepo;

    @InjectMocks
    private BranchService branchService;

    @Test
    void getServicesByBranchId_Success_ReturnsMappedServiceResponses() {
        Branch branch = new Branch();
        branch.setId(10L);

        com.project.Backend_BookMyHotel.domain.Service spa = new com.project.Backend_BookMyHotel.domain.Service();
        spa.setId(1L);
        spa.setBranch(branch);
        spa.setName("Spa");
        spa.setPrice(BigDecimal.valueOf(50));
        branch.setServices(List.of(spa));

        Mockito.when(branchRepo.findByIdWithServices(10L)).thenReturn(Optional.of(branch));

        List<ServiceResponse> result = branchService.getServicesByBranchId(10L);

        Assertions.assertEquals(1, result.size());
        Assertions.assertEquals("Spa", result.get(0).getName());
        Assertions.assertEquals(10L, result.get(0).getBranchId());
    }

    @Test
    void getServicesByBranchId_WhenBranchNotFound_ThrowsEntityNotFoundException() {
        Mockito.when(branchRepo.findByIdWithServices(999L)).thenReturn(Optional.empty());

        Assertions.assertThrows(EntityNotFoundException.class,
                () -> branchService.getServicesByBranchId(999L));
    }
}
