package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.ChatMessageDto;
import com.secondlife.secondlife.dto.response.ChatRoomDto;
import com.secondlife.secondlife.entity.ChatMessage;
import com.secondlife.secondlife.entity.ChatRoom;
import com.secondlife.secondlife.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

@Component
public class ChatMapper {
    private final Delegate delegate;

    public ChatMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public ChatRoomDto toRoom(ChatRoom room, String lastMessage) {
        return delegate.toRoom(room, lastMessage);
    }

    public ChatMessageDto toMessage(ChatMessage message) {
        return delegate.toMessage(message);
    }

    protected String partyName(User user) {
        return delegate.partyName(user);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "postId", source = "room.post.id")
        @Mapping(target = "postTitle", source = "room.post.title")
        @Mapping(target = "postImageUrl", source = "room.post.imageUrl")
        @Mapping(target = "buyerId", source = "room.buyer.id")
        @Mapping(target = "sellerId", source = "room.seller.id")
        @Mapping(target = "buyerName", expression = "java(partyName(room.getBuyer()))")
        @Mapping(target = "sellerName", expression = "java(partyName(room.getSeller()))")
        @Mapping(target = "buyerAvatar", source = "room.buyer.profile.avatarUrl")
        @Mapping(target = "sellerAvatar", source = "room.seller.profile.avatarUrl")
        @Mapping(target = "lastMessage", source = "lastMessage")
        ChatRoomDto toRoom(ChatRoom room, String lastMessage);

        @Mapping(target = "conversationId", source = "chatRoom.id")
        @Mapping(target = "senderId", source = "sender.id")
        ChatMessageDto toMessage(ChatMessage message);

        default String partyName(User user) {
            if (user == null) return null;
            return user.getProfile() != null ? user.getProfile().getFullName() : user.getEmail();
        }
    }
}
