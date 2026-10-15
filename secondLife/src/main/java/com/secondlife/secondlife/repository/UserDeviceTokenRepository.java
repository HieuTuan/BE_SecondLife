package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.UserDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDeviceTokenRepository extends JpaRepository<UserDeviceToken, UUID> {

    List<UserDeviceToken> findByUserId(UUID userId);

    Optional<UserDeviceToken> findByToken(String token);

    @Modifying
    @Query("DELETE FROM UserDeviceToken t WHERE t.token = :token")
    void deleteByToken(@Param("token") String token);

    @Modifying
    @Query("DELETE FROM UserDeviceToken t WHERE t.user.id = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
