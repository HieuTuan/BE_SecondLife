package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.OrderReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderReviewRepository extends JpaRepository<OrderReview, UUID> {

    Optional<OrderReview> findByOrderId(UUID orderId);

    boolean existsByOrderId(UUID orderId);

    Page<OrderReview> findBySellerIdOrderByCreatedAtDesc(UUID sellerId, Pageable pageable);

    Page<OrderReview> findByPostIdOrderByCreatedAtDesc(UUID postId, Pageable pageable);

    @Query("SELECT AVG(r.rating) FROM OrderReview r WHERE r.seller.id = :sellerId")
    Double getAverageRatingBySellerId(@Param("sellerId") UUID sellerId);

    @Query("SELECT COUNT(r) FROM OrderReview r WHERE r.seller.id = :sellerId")
    long countBySellerId(@Param("sellerId") UUID sellerId);

    @Query("SELECT r.rating, COUNT(r) FROM OrderReview r WHERE r.seller.id = :sellerId GROUP BY r.rating")
    List<Object[]> countRatingsGroupedBySellerId(@Param("sellerId") UUID sellerId);

    @Query("SELECT AVG(r.rating) FROM OrderReview r WHERE r.post.id = :postId")
    Double getAverageRatingByPostId(@Param("postId") UUID postId);

    @Query("SELECT COUNT(r) FROM OrderReview r WHERE r.post.id = :postId")
    long countByPostId(@Param("postId") UUID postId);
}
