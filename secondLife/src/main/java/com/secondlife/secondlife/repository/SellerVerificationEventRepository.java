package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.SellerVerificationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SellerVerificationEventRepository extends JpaRepository<SellerVerificationEvent, UUID> {

    List<SellerVerificationEvent> findByVerificationIdOrderByCreatedAtAsc(UUID verificationId);
}
