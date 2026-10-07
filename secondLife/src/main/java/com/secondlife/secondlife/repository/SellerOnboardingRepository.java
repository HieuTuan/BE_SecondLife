package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.SellerOnboarding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SellerOnboardingRepository extends JpaRepository<SellerOnboarding, UUID> {}
