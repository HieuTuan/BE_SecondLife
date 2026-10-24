package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.SendMessageRequest;
import com.secondlife.secondlife.dto.response.ChatMessageDto;
import com.secondlife.secondlife.dto.response.ChatRoomDto;
import com.secondlife.secondlife.entity.ChatMessage;
import com.secondlife.secondlife.entity.ChatRoom;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.ChatMapper;
import com.secondlife.secondlife.repository.ChatMessageRepository;
import com.secondlife.secondlife.repository.ChatRoomRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.ChatService;

import lombok.RequiredArgsConstructor;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service("chatService")
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMapper chatMapper;

    @Transactional(readOnly = true)
    public ChatRoomDto getRoom(UUID postId, UUID buyerId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
                
        return chatRoomRepository.findFirstByPostIdAndBuyerIdAndSellerIdOrderByCreatedAtAsc(postId, buyerId, post.getUser().getId())
                .map(this::toDto)
                .orElse(null);
    }

    @Transactional
    public ChatRoomDto getOrCreateRoom(UUID postId, UUID buyerId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
        
        if (post.getUser().getId().equals(buyerId)) {
            throw new IllegalArgumentException("Seller cannot buy their own item");
        }

        ChatRoom room = chatRoomRepository.findFirstByPostIdAndBuyerIdAndSellerIdOrderByCreatedAtAsc(postId, buyerId, post.getUser().getId())
                .orElseGet(() -> {
                    User buyer = userRepository.findById(buyerId)
                            .orElseThrow(() -> new NotFoundException("Buyer not found"));
                    ChatRoom newRoom = new ChatRoom();
                    newRoom.setPost(post);
                    newRoom.setBuyer(buyer);
                    newRoom.setSeller(post.getUser());
                    return chatRoomRepository.save(newRoom);
                });

        return toDto(room);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomDto> getUserRooms(UUID userId) {
        List<ChatRoom> rooms = chatRoomRepository.findUserChatRooms(userId);
        Map<UUID, String> lastMessages = new HashMap<>();
        if (!rooms.isEmpty()) {
            for (ChatMessage message : chatMessageRepository.findLatestByRoomIds(rooms.stream().map(ChatRoom::getId).toList())) {
                lastMessages.put(message.getChatRoom().getId(), message.getMessageContent());
            }
        }
        Map<String, ChatRoomDto> uniqueRooms = new LinkedHashMap<>();
        for (ChatRoom room : rooms) {
            UUID otherUserId = room.getBuyer().getId().equals(userId)
                    ? room.getSeller().getId()
                    : room.getBuyer().getId();
            String key = room.getPost().getId() + "_" + otherUserId;
            if (!uniqueRooms.containsKey(key)) {
                uniqueRooms.put(key, chatMapper.toRoom(room, lastMessages.getOrDefault(room.getId(), "New Conversation")));
            }
        }
        return new ArrayList<>(uniqueRooms.values());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(UUID roomId, UUID userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
        
        if (!room.getBuyer().getId().equals(userId) && !room.getSeller().getId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to view this room");
        }

        return chatMessageRepository.findByChatRoomIdOrderBySentAtAsc(roomId).stream()
                .map(chatMapper::toMessage)
                .collect(Collectors.toList());
    }

    @Transactional
    public ChatMessageDto sendMessage(UUID roomId, UUID senderId, SendMessageRequest request) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));

        if (!room.getBuyer().getId().equals(senderId) && !room.getSeller().getId().equals(senderId)) {
            throw new IllegalArgumentException("Not authorized to send message to this room");
        }

        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new NotFoundException("Sender not found"));

        ChatMessage message = new ChatMessage();
        message.setChatRoom(room);
        message.setSender(sender);
        message.setMessageContent(request.messageContent());
        message.setSentAt(Instant.now());
        message = chatMessageRepository.save(message);

        room.setUpdatedAt(Instant.now());
        chatRoomRepository.save(room);

        ChatMessageDto dto = chatMapper.toMessage(message);

        // Broadcast to both buyer and seller (so sender's other devices sync too)
        System.out.println("[ChatWS] Broadcasting message to Room: " + room.getId() + ", Buyer: " + room.getBuyer().getId() + ", Seller: " + room.getSeller().getId());
        
        messagingTemplate.convertAndSendToUser(
                room.getBuyer().getId().toString(),
                "/queue/messages",
                dto
        );
        messagingTemplate.convertAndSendToUser(
                room.getSeller().getId().toString(),
                "/queue/messages",
                dto
        );

        return dto;
    }

    @Transactional
    public void sendSystemMessage(UUID postId, UUID buyerId, String content) {
        ChatRoomDto roomDto = getOrCreateRoom(postId, buyerId);
        ChatRoom room = chatRoomRepository.findById(roomDto.id())
                .orElseThrow(() -> new NotFoundException("Room not found"));

        ChatMessage message = new ChatMessage();
        message.setChatRoom(room);
        message.setSender(room.getSeller()); // System messages can appear from seller or we need a system user. For now, use seller, or modify ChatMessage to support system messages.
        // Let's modify the messageContent to indicate it's a system message.
        message.setMessageContent("SYSTEM: " + content);
        message.setSentAt(Instant.now());
        message = chatMessageRepository.save(message);

        room.setUpdatedAt(Instant.now());
        chatRoomRepository.save(room);

        ChatMessageDto dto = chatMapper.toMessage(message);

        // Broadcast to buyer and seller
        messagingTemplate.convertAndSendToUser(room.getBuyer().getId().toString(), "/queue/messages", dto);
        messagingTemplate.convertAndSendToUser(room.getSeller().getId().toString(), "/queue/messages", dto);
    }

    private ChatRoomDto toDto(ChatRoom room) {
        String lastMessage = "New Conversation";
        Optional<ChatMessage> lastMsg = chatMessageRepository.findFirstByChatRoomIdOrderBySentAtDesc(room.getId());
        if (lastMsg.isPresent()) {
            lastMessage = lastMsg.get().getMessageContent();
        }

        return chatMapper.toRoom(room, lastMessage);
    }

}
