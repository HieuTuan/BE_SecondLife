package com.secondlife.secondlife.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "geocoded_locations_cache",
       uniqueConstraints = @UniqueConstraint(name = "uq_geocoded_locations_cache_coords", columnNames = {"lat_key", "lon_key"}))
@Getter
@Setter
@NoArgsConstructor
public class GeocodedLocationCache {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @Column(name = "lat_key", precision = 7, scale = 4, nullable = false)
    private BigDecimal latKey;

    @Column(name = "lon_key", precision = 7, scale = 4, nullable = false)
    private BigDecimal lonKey;

    @Column(name = "formatted_address", length = 500, nullable = false)
    private String formattedAddress;

    @Column(name = "city", length = 50, nullable = false)
    private String city;

    @Column(name = "country", length = 10, nullable = false)
    private String country = "VN";

    @Column(name = "place_id", length = 255)
    private String placeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public GeocodedLocationCache(BigDecimal latKey, BigDecimal lonKey, String formattedAddress,
                                 String city, String country, String placeId) {
        this.latKey = latKey;
        this.lonKey = lonKey;
        this.formattedAddress = formattedAddress;
        this.city = city;
        this.country = country != null ? country : "VN";
        this.placeId = placeId;
        this.createdAt = Instant.now();
    }
}
