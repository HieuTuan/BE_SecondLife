package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MarketplaceListingServiceTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final MarketplaceListingService service = new MarketplaceListingService(posts);

    @Test
    void unavailableListingReturnsNotFound() {
        UUID id = UUID.randomUUID();
        when(posts.findByIdAndStatus(id, "ACTIVE")).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.get(id));
        verify(posts).findByIdAndStatus(id, "ACTIVE");
    }

    @Test
    void activeDetailContainsAllImagesAndPurchaseId() {
        Post post = listing();
        post.setImageUrl("https://example.com/product.jpg");
        when(posts.findByIdAndStatus(post.getId(), "ACTIVE")).thenReturn(Optional.of(post));
        var result = service.get(post.getId());
        assertEquals(post.getId(), result.postId());
        assertEquals(post.getUser().getId(), result.sellerId());
        assertEquals(List.of(post.getImageUrl()), result.imageUrls());
        assertEquals("ACTIVE", result.status());
    }

    @Test
    @SuppressWarnings("unchecked")
    void listingQueryAlwaysRestrictsActiveAndAppliesCategoryAndItem() {
        UUID category = UUID.randomUUID(), item = UUID.randomUUID();
        var pageable = PageRequest.of(0, 5);
        when(posts.findAll(any(Specification.class), eq(pageable))).thenAnswer(call -> {
            Specification<Post> spec = call.getArgument(0);
            Root<Post> root = mock(Root.class);
            CriteriaQuery<?> query = mock(CriteriaQuery.class);
            CriteriaBuilder cb = mock(CriteriaBuilder.class);
            Path<Object> statusPath = mock(Path.class), categoryPath = mock(Path.class), itemPath = mock(Path.class);
            when(root.get("status")).thenReturn(statusPath);
            when(root.get("categoryId")).thenReturn(categoryPath);
            when(root.get("itemId")).thenReturn(itemPath);
            when(cb.equal(any(Expression.class), any(Object.class))).thenReturn(mock(Predicate.class));
            when(cb.and(any(Predicate.class), any(Predicate.class))).thenReturn(mock(Predicate.class));
            spec.toPredicate(root, query, cb);
            verify(cb).equal(statusPath, "ACTIVE");
            verify(cb).equal(categoryPath, category);
            verify(cb).equal(itemPath, item);
            return new PageImpl<>(List.of(listing()), pageable, 1);
        });
        var page = service.list(category, item, pageable);
        assertEquals(1, page.getTotalElements());
        assertEquals("ACTIVE", page.getContent().getFirst().status());
    }

    private Post listing() {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        User user = new User();
        user.setId(UUID.randomUUID());
        post.setUser(user);
        post.setStatus("ACTIVE");
        return post;
    }
}
