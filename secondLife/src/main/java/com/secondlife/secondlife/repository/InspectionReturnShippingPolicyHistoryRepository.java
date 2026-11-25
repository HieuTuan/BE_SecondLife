package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.InspectionReturnShippingPolicyHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InspectionReturnShippingPolicyHistoryRepository extends JpaRepository<InspectionReturnShippingPolicyHistory, Long> {
    Page<InspectionReturnShippingPolicyHistory> findAllByOrderByChangedAtDescIdDesc(Pageable pageable);
}
