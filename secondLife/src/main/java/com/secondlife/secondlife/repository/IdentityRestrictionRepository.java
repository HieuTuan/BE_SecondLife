package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.IdentityRestriction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface IdentityRestrictionRepository extends JpaRepository<IdentityRestriction, UUID> {
    @Query("SELECT r FROM IdentityRestriction r WHERE r.documentNumberHash = :hash " +
            "AND r.revokedAt IS NULL AND (r.expiresAt IS NULL OR r.expiresAt > :now)")
    List<IdentityRestriction> findActiveByDocumentHash(@Param("hash") String hash, @Param("now") Instant now);
}
