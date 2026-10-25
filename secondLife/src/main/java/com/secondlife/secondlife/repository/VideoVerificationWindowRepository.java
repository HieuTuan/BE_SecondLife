package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.VideoVerificationWindow;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface VideoVerificationWindowRepository extends JpaRepository<VideoVerificationWindow, String> {
    @Modifying
    @Query(value = """
            INSERT INTO video_verification_windows(id, started_at, calls)
            VALUES (:id, :startedAt, 0)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    void initialize(@Param("id") String id, @Param("startedAt") Instant startedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from VideoVerificationWindow w where w.id=:id")
    Optional<VideoVerificationWindow> findByIdForUpdate(@Param("id") String id);
}
