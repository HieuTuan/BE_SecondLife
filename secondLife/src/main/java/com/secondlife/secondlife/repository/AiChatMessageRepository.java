package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, UUID> {
    List<AiChatMessage> findBySessionIdOrderBySentAtAsc(UUID sessionId);
    @Query("select count(m) from AiChatMessage m where m.session.user.id = :userId and m.role = 'USER' and m.sentAt >= :since")
    long countRecentUserMessages(@Param("userId") UUID userId,
            @Param("since") Instant since);
}
