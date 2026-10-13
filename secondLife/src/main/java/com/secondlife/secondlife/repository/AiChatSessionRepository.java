package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.AiChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiChatSessionRepository extends JpaRepository<AiChatSession, UUID> {
    List<AiChatSession> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<AiChatSession> findByPostIdAndUser_Id(UUID postId, UUID userId);
}
