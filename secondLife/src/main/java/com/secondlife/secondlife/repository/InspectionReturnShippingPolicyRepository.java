package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionReturnShippingPolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface InspectionReturnShippingPolicyRepository extends JpaRepository<InspectionReturnShippingPolicy, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from InspectionReturnShippingPolicy p where p.id = :id")
    Optional<InspectionReturnShippingPolicy> findByIdForUpdate(@Param("id") short id);
}
