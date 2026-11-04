package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.ChatRoomDto;
import com.secondlife.secondlife.entity.ChatRoom;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.mapper.ChatMapper;
import com.secondlife.secondlife.repository.ChatMessageRepository;
import com.secondlife.secondlife.repository.ChatRoomRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.dto.request.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import com.secondlife.secondlife.mapper.MapperTestSupport;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    private ChatRoomRepository chatRoomRepository;
    private ChatMessageRepository chatMessageRepository;
    private PostRepository postRepository;
    private UserRepository userRepository;
    private SimpMessagingTemplate messagingTemplate;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatRoomRepository = mock(ChatRoomRepository.class);
        chatMessageRepository = mock(ChatMessageRepository.class);
        postRepository = mock(PostRepository.class);
        userRepository = mock(UserRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        chatService = new com.secondlife.secondlife.service.impl.ChatServiceImpl(chatRoomRepository, chatMessageRepository, postRepository, userRepository, messagingTemplate, MapperTestSupport.mapper(ChatMapper.class));
    }

    @Test
    void getOrCreateRoom_returnsExistingRoomWhenFound() {
        UUID postId = UUID.randomUUID();
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();

        User seller = new User();
        seller.setId(sellerId);
        seller.setEmail("seller@example.com");

        User buyer = new User();
        buyer.setId(buyerId);
        buyer.setEmail("buyer@example.com");

        Post post = new Post();
        post.setId(postId);
        post.setTitle("Test Item");
        post.setUser(seller);

        ChatRoom existingRoom = new ChatRoom();
        existingRoom.setId(UUID.randomUUID());
        existingRoom.setPost(post);
        existingRoom.setBuyer(buyer);
        existingRoom.setSeller(seller);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(chatRoomRepository.findFirstByPostIdAndBuyerIdAndSellerIdOrderByCreatedAtAsc(postId, buyerId, sellerId))
                .thenReturn(Optional.of(existingRoom));

        ChatRoomDto result = chatService.getOrCreateRoom(postId, buyerId);

        assertNotNull(result);
        assertEquals(existingRoom.getId(), result.id());
        verify(chatRoomRepository, never()).save(any(ChatRoom.class));
    }

    @Test
    void getUserRooms_deduplicatesRoomsForSamePostAndBuyer() {
        UUID userId = UUID.randomUUID();
        UUID partnerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();

        User me = new User();
        me.setId(userId);
        me.setEmail("me@example.com");

        User partner = new User();
        partner.setId(partnerId);
        partner.setEmail("partner@example.com");

        Post post = new Post();
        post.setId(postId);
        post.setTitle("Test Item");
        post.setUser(partner);

        ChatRoom room1 = new ChatRoom();
        room1.setId(UUID.randomUUID());
        room1.setPost(post);
        room1.setBuyer(me);
        room1.setSeller(partner);

        ChatRoom room2Duplicate = new ChatRoom();
        room2Duplicate.setId(UUID.randomUUID());
        room2Duplicate.setPost(post);
        room2Duplicate.setBuyer(me);
        room2Duplicate.setSeller(partner);

        when(chatRoomRepository.findUserChatRooms(userId)).thenReturn(List.of(room1, room2Duplicate));

        List<ChatRoomDto> rooms = chatService.getUserRooms(userId);

        assertEquals(1, rooms.size());
        assertEquals(room1.getId(), rooms.get(0).id());
    }
    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "RESERVED", "SOLD", "PAYMENT_PENDING", "REJECTED"})
    void participantsReadListingDataInAllRoomResponses(String status) {
        ChatRoom room = room(status);
        UUID postId = room.getPost().getId(), buyerId = room.getBuyer().getId(), sellerId = room.getSeller().getId();
        when(postRepository.findById(postId)).thenReturn(Optional.of(room.getPost()));
        when(chatRoomRepository.findFirstByPostIdAndBuyerIdAndSellerIdOrderByCreatedAtAsc(postId, buyerId, sellerId))
                .thenReturn(Optional.of(room));
        when(chatRoomRepository.findUserChatRooms(buyerId)).thenReturn(List.of(room));
        when(chatRoomRepository.findUserChatRooms(sellerId)).thenReturn(List.of(room));

        for (ChatRoomDto dto : List.of(chatService.getRoom(postId, buyerId), chatService.getOrCreateRoom(postId, buyerId),
                chatService.getUserRooms(buyerId).getFirst(), chatService.getUserRooms(sellerId).getFirst())) {
            assertEquals(room.getId(), dto.id());
            assertEquals(postId, dto.postId());
            assertEquals("Product", dto.postTitle());
            assertEquals("https://example.test/product.jpg", dto.postImageUrl());
            assertEquals(new BigDecimal("150000"), dto.postPrice());
            assertEquals(status, dto.postStatus());
            assertEquals(buyerId, dto.buyerId());
            assertEquals(sellerId, dto.sellerId());
            assertEquals("buyer@example.test", dto.buyerName());
            assertEquals("seller@example.test", dto.sellerName());
            assertEquals("New Conversation", dto.lastMessage());
            assertEquals(room.getUpdatedAt(), dto.updatedAt());
            var json = new ObjectMapper().valueToTree(dto);
            for (String field : List.of("id", "postId", "postTitle", "postImageUrl", "buyerId", "buyerName",
                    "buyerAvatar", "sellerId", "sellerName", "sellerAvatar", "lastMessage", "updatedAt")) {
                assertTrue(json.has(field), field);
            }
        }
        verify(chatRoomRepository, never()).save(any());
        verifyNoInteractions(messagingTemplate);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SOLD", "PAYMENT_PENDING", "DRAFT", "REJECTED", "PENDING"})
    void outsiderCannotCreateRoomToReadNonPublicListing(String status) {
        ChatRoom room = room(status);
        when(postRepository.findById(room.getPost().getId())).thenReturn(Optional.of(room.getPost()));
        assertNull(chatService.getRoom(room.getPost().getId(), UUID.randomUUID()));
        assertThrows(ForbiddenException.class,
                () -> chatService.getOrCreateRoom(room.getPost().getId(), UUID.randomUUID()));
        verify(chatRoomRepository, never()).save(any());
        verifyNoInteractions(userRepository, messagingTemplate);
    }

    @Test
    void outsiderCannotReadOrSendRoomMessages() {
        ChatRoom room = room("SOLD");
        when(chatRoomRepository.findById(room.getId())).thenReturn(Optional.of(room));
        UUID outsider = UUID.randomUUID();
        assertThrows(ForbiddenException.class, () -> chatService.getMessages(room.getId(), outsider));
        assertThrows(ForbiddenException.class,
                () -> chatService.sendMessage(room.getId(), outsider, new SendMessageRequest("Hello")));
        verifyNoInteractions(chatMessageRepository, userRepository, messagingTemplate);
    }

    @Test
    void missingOptionalListingFieldsRemainNull() {
        ChatRoom room = room("SOLD");
        room.getPost().setImageUrl(null);
        room.getPost().setPrice(null);
        when(chatRoomRepository.findUserChatRooms(room.getBuyer().getId())).thenReturn(List.of(room));
        var dto = chatService.getUserRooms(room.getBuyer().getId()).getFirst();
        assertNull(dto.postImageUrl());
        assertNull(dto.postPrice());
        assertEquals("SOLD", dto.postStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "RESERVED"})
    void creatingPublicRoomReturnsListingMetadata(String status) {
        ChatRoom fixture = room(status);
        when(postRepository.findById(fixture.getPost().getId())).thenReturn(Optional.of(fixture.getPost()));
        when(userRepository.findById(fixture.getBuyer().getId())).thenReturn(Optional.of(fixture.getBuyer()));
        when(chatRoomRepository.save(any(ChatRoom.class))).thenAnswer(call -> {
            ChatRoom created = call.getArgument(0); created.setId(UUID.randomUUID()); return created;
        });
        var result = chatService.getOrCreateRoom(fixture.getPost().getId(), fixture.getBuyer().getId());
        assertEquals(new BigDecimal("150000"), result.postPrice());
        assertEquals(status, result.postStatus());
        assertEquals(fixture.getBuyer().getId(), result.buyerId());
        verify(chatRoomRepository).save(any(ChatRoom.class));
    }

    private ChatRoom room(String status) {
        var seller = new User(); seller.setId(UUID.randomUUID()); seller.setEmail("seller@example.test");
        var buyer = new User(); buyer.setId(UUID.randomUUID()); buyer.setEmail("buyer@example.test");
        var post = new Post(); post.setId(UUID.randomUUID()); post.setUser(seller); post.setStatus(status);
        post.setTitle("Product"); post.setPrice(new BigDecimal("150000")); post.setImageUrl("https://example.test/product.jpg");
        var room = new ChatRoom(); room.setId(UUID.randomUUID()); room.setPost(post); room.setBuyer(buyer); room.setSeller(seller);
        room.setUpdatedAt(Instant.parse("2026-11-04T01:00:00Z"));
        return room;
    }

}
