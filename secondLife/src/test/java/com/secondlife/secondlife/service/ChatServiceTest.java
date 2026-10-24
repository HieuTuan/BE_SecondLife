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
}
