package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.SystemOrderFunds;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SystemOrderFundsRepository extends JpaRepository<SystemOrderFunds, UUID> { }
