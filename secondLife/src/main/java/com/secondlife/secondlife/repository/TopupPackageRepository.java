package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.TopupPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TopupPackageRepository extends JpaRepository<TopupPackage, UUID> {
}
