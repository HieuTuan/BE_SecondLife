package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.SendMessageRequest;
import com.secondlife.secondlife.dto.response.ChatMessageDto;
import com.secondlife.secondlife.dto.response.ChatRoomDto;
import java.util.List;
import java.util.UUID;

public interface ChatService {
    ChatRoomDto getRoom(UUID postId, UUID buyerId);

    ChatRoomDto getOrCreateRoom(UUID postId, UUID buyerId);

    List<ChatRoomDto> getUserRooms(UUID userId);

    List<ChatMessageDto> getMessages(UUID roomId, UUID userId);

    ChatMessageDto sendMessage(UUID roomId, UUID senderId, SendMessageRequest request);

    void sendSystemMessage(UUID postId, UUID buyerId, String content);

}
