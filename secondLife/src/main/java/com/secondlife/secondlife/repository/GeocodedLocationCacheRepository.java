package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.GeocodedLocationCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GeocodedLocationCacheRepository extends JpaRepository<GeocodedLocationCache, UUID> {
    @Transactional(readOnly = true,
            propagation = Propagation.REQUIRES_NEW)
    Optional<GeocodedLocationCache> findByLatKeyAndLonKey(BigDecimal latKey, BigDecimal lonKey);

    @Override
    @Transactional(
            propagation = Propagation.REQUIRES_NEW)
    <S extends GeocodedLocationCache> S save(S entity);
}
