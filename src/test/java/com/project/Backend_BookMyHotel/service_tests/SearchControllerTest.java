package com.project.Backend_BookMyHotel.service_tests;

import com.project.Backend_BookMyHotel.controller.SearchController;
import com.project.Backend_BookMyHotel.domain.Hotel;
import com.project.Backend_BookMyHotel.repository.HotelRepository;
import com.project.Backend_BookMyHotel.service.SearchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private SearchService searchService;

    @Mock
    private HotelRepository hotelRepository;

    @InjectMocks
    private SearchController searchController;

    @Test
    void searchRooms_ResolvesLegacyHotelSlugToDatabaseId() {
        Hotel fourSeasons = new Hotel();
        fourSeasons.setId(4L);
        fourSeasons.setName("Four Seasons");

        Mockito.when(hotelRepository.findByPublicId("four-seasons")).thenReturn(Optional.empty());
        Mockito.when(hotelRepository.findByNameIgnoreCase("four seasons")).thenReturn(Optional.of(fourSeasons));
        Mockito.doReturn(ResponseEntity.ok("matched")).when(searchService).searchAvailableRooms(
                        Mockito.isNull(), Mockito.isNull(), Mockito.isNull(), Mockito.isNull(),
                        Mockito.isNull(), Mockito.isNull(), Mockito.isNull(), Mockito.isNull(),
                        Mockito.isNull(), Mockito.eq(Set.of(4L)), Mockito.isNull(),
                        Mockito.eq(0), Mockito.eq(10), Mockito.eq("price,asc"));

        searchController.searchAvailableRooms(
                null, null, null, null, null, null, null, null, null,
                "four-seasons", null, null, 0, 10, "price,asc");

        Mockito.verify(searchService).searchAvailableRooms(
                Mockito.isNull(), Mockito.isNull(), Mockito.isNull(), Mockito.isNull(),
                Mockito.isNull(), Mockito.isNull(), Mockito.isNull(), Mockito.isNull(),
                Mockito.isNull(), Mockito.eq(Set.of(4L)), Mockito.isNull(),
                Mockito.eq(0), Mockito.eq(10), Mockito.eq("price,asc"));
    }
}
