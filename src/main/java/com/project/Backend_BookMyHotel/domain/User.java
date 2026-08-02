package com.project.Backend_BookMyHotel.domain;

import com.project.Backend_BookMyHotel.dto.Role;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private String userId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    private String gender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Booking> bookings;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Review> reviews;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "managed_hotel_id", nullable = true)
    private Hotel managedHotel;

    @Column(name = "is_active")
    private Character isActive;

    @Column(name = "eco_points")
    private Integer ecoPoints = 0;

    // Google's stable per-account "sub" claim. Null for users who only ever signed up with
    // email/password. Kept separate from email so a Google login can find (and link onto) an
    // existing password account by email on first use, then use this column for every login after.
    @Column(name = "google_id", unique = true)
    private String googleId;

    public boolean isAdmin() {
        return this.role == Role.ADMIN;
    }

    public boolean isHotelManager() {
        return this.role == Role.HOTEL_MANAGER;
    }

    public boolean isCustomer() {
        return this.role == Role.CUSTOMER;
    }
}