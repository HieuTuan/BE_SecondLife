package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.TopupPackage;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TopupPackageRepository extends JpaRepository<TopupPackage, UUID> {
    List<TopupPackage> findByActiveTrueOrderByNameAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select p from TopupPackage p where p.id = :id")
    Optional<TopupPackage> findByIdForPurchase(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from TopupPackage p where p.id = :id")
    Optional<TopupPackage> findByIdForUpdate(@Param("id") UUID id);
}
