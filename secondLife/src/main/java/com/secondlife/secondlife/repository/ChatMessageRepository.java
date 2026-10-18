package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.ChatMessage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    @Query(value = """
            SELECT DISTINCT ON (room_id) * FROM chat_messages
            WHERE room_id IN (:roomIds) ORDER BY room_id, sent_at DESC
            """, nativeQuery = true)
    List<ChatMessage> findLatestByRoomIds(@Param("roomIds") Collection<UUID> roomIds);

    List<ChatMessage> findByChatRoomIdOrderBySentAtAsc(UUID chatRoomId);
    Optional<ChatMessage> findFirstByChatRoomIdOrderBySentAtDesc(UUID chatRoomId);
}
