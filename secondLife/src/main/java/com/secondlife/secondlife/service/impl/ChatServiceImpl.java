package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.SendMessageRequest;
import com.secondlife.secondlife.dto.response.ChatMessageDto;
import com.secondlife.secondlife.dto.response.ChatRoomDto;
import com.secondlife.secondlife.entity.ChatMessage;
import com.secondlife.secondlife.entity.ChatRoom;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.ChatMapper;
import com.secondlife.secondlife.repository.ChatMessageRepository;
import com.secondlife.secondlife.repository.ChatRoomRepository;
import com.secondlife.secondlife.repository.NegotiationRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.ChatService;

import lombok.RequiredArgsConstructor;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service("chatService")
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final String SYSTEM_PREFIX = "SYSTEM:";

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMapper chatMapper;
    private final NegotiationRepository negotiations;
    private final ObjectMapper objectMapper;

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
                    // Existing participants retain history; outsiders cannot open a room to read private listings.
                    if (!"ACTIVE".equals(post.getStatus()) && !"RESERVED".equals(post.getStatus())) {
                        throw new ForbiddenException("Cannot create a conversation for a non-public listing");
                    }
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
            var messages = chatMessageRepository.findLatestByRoomIds(rooms.stream().map(ChatRoom::getId).toList());
            var currentOffers = currentOfferContents(messages);
            for (ChatMessage message : messages) {
                lastMessages.put(message.getChatRoom().getId(),
                        currentOffers.getOrDefault(message.getId(), message.getMessageContent()));
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
            throw new ForbiddenException("Not authorized to view this room");
        }

        var messages = chatMessageRepository.findByChatRoomIdOrderBySentAtAsc(roomId);
        var currentOffers = currentOfferContents(messages);
        return messages.stream().map(message -> {
            var dto = chatMapper.toMessage(message);
            var current = currentOffers.get(message.getId());
            return current == null ? dto
                    : new ChatMessageDto(dto.id(), dto.conversationId(), dto.senderId(), current, dto.sentAt());
        }).toList();
    }

    private Map<UUID, String> currentOfferContents(List<ChatMessage> messages) {
        var offers = new ArrayList<OfferMessage>();
        for (ChatMessage message : messages) {
            String content = message.getMessageContent();
            if (content == null || !content.startsWith(SYSTEM_PREFIX)) continue;
            try {
                var parsed = objectMapper.readTree(content.substring(SYSTEM_PREFIX.length()));
                if (parsed instanceof ObjectNode payload && "OFFER".equals(payload.path("type").asText())) {
                    var negotiationId = UUID.fromString(payload.path("negotiationId").asText());
                    offers.add(new OfferMessage(message, negotiationId, payload));
                }
            } catch (JacksonException | IllegalArgumentException ignored) {
                // Older messages also include plain text and locally generated offer IDs.
            }
        }
        if (offers.isEmpty()) return Map.of();

        var ids = offers.stream().map(OfferMessage::negotiationId).collect(Collectors.toSet());
        var states = negotiations.findChatOfferStates(ids).stream()
                .collect(Collectors.toMap(NegotiationRepository.ChatOfferState::getId, Function.identity()));
        Map<UUID, String> contents = new HashMap<>();
        for (OfferMessage offer : offers) {
            var state = states.get(offer.negotiationId());
            var room = offer.message().getChatRoom();
            if (state == null || !room.getPost().getId().equals(state.getPostId())
                    || !room.getBuyer().getId().equals(state.getBuyerId())) {
                continue;
            }
            offer.payload().put("status", state.getStatus().name());
            offer.payload().put("price", state.getOfferedPrice());
            contents.put(offer.message().getId(), SYSTEM_PREFIX + " " + objectMapper.writeValueAsString(offer.payload()));
        }
        return contents;
    }

    private record OfferMessage(ChatMessage message, UUID negotiationId, ObjectNode payload) {
    }

    @Transactional
    public ChatMessageDto sendMessage(UUID roomId, UUID senderId, SendMessageRequest request) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new NotFoundException("Room not found"));

        if (!room.getBuyer().getId().equals(senderId) && !room.getSeller().getId().equals(senderId)) {
            throw new ForbiddenException("Not authorized to send message to this room");
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
            var message = lastMsg.get();
            lastMessage = currentOfferContents(List.of(message))
                    .getOrDefault(message.getId(), message.getMessageContent());
        }

        return chatMapper.toRoom(room, lastMessage);
    }

}
