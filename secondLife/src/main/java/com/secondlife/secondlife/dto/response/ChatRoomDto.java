package com.secondlife.secondlife.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class ChatRoomDto {
    private UUID id;
    private UUID postId;
    private String postTitle;
    private String postImageUrl;
    private UUID buyerId;
    private String buyerName;
    private String buyerAvatar;
    private UUID sellerId;
    private String sellerName;
    private String sellerAvatar;
    private String lastMessage;
    private Instant updatedAt;
}
