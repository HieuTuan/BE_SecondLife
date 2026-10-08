package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.SendMessageRequest;
import com.secondlife.secondlife.dto.response.ChatMessageDto;
import com.secondlife.secondlife.dto.response.ChatRoomDto;
import com.secondlife.secondlife.entity.ChatMessage;
import com.secondlife.secondlife.entity.ChatRoom;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.ChatMessageRepository;
import com.secondlife.secondlife.repository.ChatRoomRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(readOnly = true)
    public ChatRoomDto getRoom(UUID postId, UUID buyerId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new NotFoundException("Post not found"));
                
        return chatRoomRepository.findByPostIdAndBuyerIdAndSellerId(postId, buyerId, post.getUser().getId())
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

        ChatRoom room = chatRoomRepository.findByPostIdAndBuyerIdAndSellerId(postId, buyerId, post.getUser().getId())
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
        return chatRoomRepository.findUserChatRooms(userId).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(UUID roomId, UUID userId) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));
        
        if (!room.getBuyer().getId().equals(userId) && !room.getSeller().getId().equals(userId)) {
            throw new IllegalArgumentException("Not authorized to view this room");
        }

        return chatMessageRepository.findByChatRoomIdOrderBySentAtAsc(roomId).stream()
                .map(this::toMessageDto)
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
        message.setMessageContent(request.getMessageContent());
        message.setSentAt(Instant.now());
        message = chatMessageRepository.save(message);

        room.setUpdatedAt(Instant.now());
        chatRoomRepository.save(room);

        ChatMessageDto dto = toMessageDto(message);

        // Broadcast to receiver
        UUID receiverId = room.getBuyer().getId().equals(senderId) ? room.getSeller().getId() : room.getBuyer().getId();
        messagingTemplate.convertAndSendToUser(
                receiverId.toString(),
                "/queue/messages",
                dto
        );

        return dto;
    }

    @Transactional
    public void sendSystemMessage(UUID postId, UUID buyerId, String content) {
        ChatRoomDto roomDto = getOrCreateRoom(postId, buyerId);
        ChatRoom room = chatRoomRepository.findById(roomDto.getId())
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

        ChatMessageDto dto = toMessageDto(message);

        // Broadcast to buyer and seller
        messagingTemplate.convertAndSendToUser(room.getBuyer().getId().toString(), "/queue/messages", dto);
        messagingTemplate.convertAndSendToUser(room.getSeller().getId().toString(), "/queue/messages", dto);
    }

    private ChatRoomDto toDto(ChatRoom room) {
        String lastMessage = "New Conversation";
        java.util.Optional<ChatMessage> lastMsg = chatMessageRepository.findFirstByChatRoomIdOrderBySentAtDesc(room.getId());
        if (lastMsg.isPresent()) {
            lastMessage = lastMsg.get().getMessageContent();
        }

        return ChatRoomDto.builder()
                .id(room.getId())
                .postId(room.getPost().getId())
                .postTitle(room.getPost().getTitle())
                .postImageUrl(room.getPost().getImageUrl())
                .buyerId(room.getBuyer().getId())
                .buyerName(room.getBuyer().getProfile() != null ? room.getBuyer().getProfile().getFullName() : room.getBuyer().getEmail())
                .buyerAvatar(room.getBuyer().getProfile() != null ? room.getBuyer().getProfile().getAvatarUrl() : null)
                .sellerId(room.getSeller().getId())
                .sellerName(room.getSeller().getProfile() != null ? room.getSeller().getProfile().getFullName() : room.getSeller().getEmail())
                .sellerAvatar(room.getSeller().getProfile() != null ? room.getSeller().getProfile().getAvatarUrl() : null)
                .lastMessage(lastMessage)
                .updatedAt(room.getUpdatedAt())
                .build();
    }

    private ChatMessageDto toMessageDto(ChatMessage message) {
        return ChatMessageDto.builder()
                .id(message.getId())
                .conversationId(message.getChatRoom().getId())
                .senderId(message.getSender().getId())
                .messageContent(message.getMessageContent())
                .sentAt(message.getSentAt())
                .build();
    }
}
