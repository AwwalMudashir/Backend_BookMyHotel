package com.project.Backend_BookMyHotel.dto;

import com.project.Backend_BookMyHotel.domain.User;

/**
 * Stable, serialization-safe identity returned by every authentication endpoint.
 * Domain entities and their relationships deliberately do not cross this boundary.
 */
public record SessionUserDto(
        Long id,
        String userId,
        String email,
        String firstName,
        String lastName,
        String gender,
        Role role,
        ManagedHotelDto managedHotel,
        Integer ecoPoints,
        Boolean emailNotifications
) {
    public static SessionUserDto from(User user) {
        ManagedHotelDto managedHotel = user.getManagedHotel() == null
                ? null
                : new ManagedHotelDto(user.getManagedHotel().getId());

        return new SessionUserDto(
                user.getId(),
                user.getUserId(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getGender(),
                user.getRole(),
                managedHotel,
                user.getEcoPoints() == null ? 0 : user.getEcoPoints(),
                Boolean.TRUE.equals(user.getEmailNotifications())
        );
    }

    public record ManagedHotelDto(Long id) {}
}
