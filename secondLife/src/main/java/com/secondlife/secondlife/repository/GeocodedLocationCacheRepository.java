package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.GeocodedLocationCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GeocodedLocationCacheRepository extends JpaRepository<GeocodedLocationCache, UUID> {
    @org.springframework.transaction.annotation.Transactional(readOnly = true,
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    Optional<GeocodedLocationCache> findByLatKeyAndLonKey(BigDecimal latKey, BigDecimal lonKey);

    @Override
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    <S extends GeocodedLocationCache> S save(S entity);
}
