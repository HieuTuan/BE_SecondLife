package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.SystemWalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SystemWalletTransactionRepository extends JpaRepository<SystemWalletTransaction, Long> { }
