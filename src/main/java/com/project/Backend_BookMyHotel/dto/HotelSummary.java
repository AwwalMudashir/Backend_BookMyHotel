package com.project.Backend_BookMyHotel.dto;

public interface HotelSummary {
    Long getId();
    String getName();
    String getDescription();
    Integer getStarRating();
    String getLogoUrl();
    String getLongImage();
}