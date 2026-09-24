package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.UserCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserCreditRepository extends JpaRepository<UserCredit, UUID> {
    Optional<UserCredit> findByUserId(UUID userId);
}
