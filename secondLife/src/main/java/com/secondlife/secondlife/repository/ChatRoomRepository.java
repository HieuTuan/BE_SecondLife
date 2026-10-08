package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    Optional<ChatRoom> findByPostIdAndBuyerIdAndSellerId(UUID postId, UUID buyerId, UUID sellerId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"post", "buyer", "seller", "buyer.profile", "seller.profile"})
    @Query("SELECT cr FROM ChatRoom cr WHERE cr.buyer.id = :userId OR cr.seller.id = :userId ORDER BY cr.updatedAt DESC")
    List<ChatRoom> findUserChatRooms(@Param("userId") UUID userId);

}
