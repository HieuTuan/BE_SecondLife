package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.HighValueInspectionPolicy;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import java.util.Optional;

public interface HighValueInspectionPolicyRepository extends JpaRepository<HighValueInspectionPolicy, Short> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from HighValueInspectionPolicy p where p.id = 1")
    Optional<HighValueInspectionPolicy> findForUpdate();
}
