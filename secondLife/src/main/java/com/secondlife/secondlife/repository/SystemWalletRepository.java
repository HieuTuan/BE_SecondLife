package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.SystemWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface SystemWalletRepository extends JpaRepository<SystemWallet, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from SystemWallet w where w.id = :id")
    Optional<SystemWallet> findByIdForUpdate(@Param("id") String id);
}
