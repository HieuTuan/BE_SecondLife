package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.GeocodedLocationCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GeocodedLocationCacheRepository extends JpaRepository<GeocodedLocationCache, UUID> {
    Optional<GeocodedLocationCache> findByLatKeyAndLonKey(BigDecimal latKey, BigDecimal lonKey);
}
