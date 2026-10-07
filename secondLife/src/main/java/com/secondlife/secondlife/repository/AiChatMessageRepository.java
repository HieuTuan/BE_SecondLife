package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.AiChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, UUID> {
    List<AiChatMessage> findBySessionIdOrderBySentAtAsc(UUID sessionId);
    @org.springframework.data.jpa.repository.Query("select count(m) from AiChatMessage m where m.session.user.id = :userId and m.role = 'USER' and m.sentAt >= :since")
    long countRecentUserMessages(@org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("since") java.time.Instant since);
}
