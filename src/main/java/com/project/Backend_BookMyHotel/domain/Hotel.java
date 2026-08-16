package com.project.Backend_BookMyHotel.domain;

import com.project.Backend_BookMyHotel.util.PublicIdGenerator;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "hotels")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Hotel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", unique = true, nullable = false)
    private String publicId;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "star_rating")
    private Integer starRating;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "long_image")
    private String longImage;

    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL)
    private List<Branch> branches;

    @OneToMany(mappedBy = "hotel", cascade = CascadeType.ALL)
    private List<Promotion> promotions;

    @PrePersist
    protected void generatePublicId() {
        if (this.publicId == null) {
            this.publicId = PublicIdGenerator.generate();
        }
    }
}