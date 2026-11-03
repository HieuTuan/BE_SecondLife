package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.validation.ImageUploadValidator;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.PostImage;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.integration.ai.AiChatGateway;
import com.secondlife.secondlife.mapper.MapperTestSupport;
import com.secondlife.secondlife.mapper.PostMapper;
import com.secondlife.secondlife.repository.AiChatMessageRepository;
import com.secondlife.secondlife.repository.AiChatSessionRepository;
import com.secondlife.secondlife.repository.CategoryQuestionTemplateRepository;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.impl.PostServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SellerListingDetailServiceTest {
    private final UUID ownerId = UUID.randomUUID();
    private final PostRepository posts = mock(PostRepository.class);
    private final ListingCreditService credits = mock(ListingCreditService.class);
    private final CloudinaryService cloud = mock(CloudinaryService.class);
    private final ListingPublicationService publication = mock(ListingPublicationService.class);
    private final ListingReviewService reviews = mock(ListingReviewService.class);
    private PostService service;

    @BeforeEach
    void setup() {
        service = new PostServiceImpl(posts, mock(UserRepository.class), credits, mock(ListingAccessService.class),
                mock(AiChatService.class), mock(CategoryTemplateService.class),
                mock(CategoryQuestionTemplateRepository.class), mock(AiChatSessionRepository.class),
                mock(AiChatMessageRepository.class), mock(CategoryRepository.class), mock(ItemRepository.class),
                mock(AiChatGateway.class), cloud, mock(InspectionOrderRepository.class), mock(AiChatGateway.class),
                publication, reviews, mock(ListingImageSimilarity.class), mock(ListingFingerprint.class),
                BigDecimal.ZERO, BigDecimal.ZERO, 30, 10485760, 3, 6,
                MapperTestSupport.mapper(PostMapper.class), null, mock(ImageUploadValidator.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ACTIVE", "RESERVED", "PAYMENT_PENDING", "SOLD", "DRAFT", "PENDING", "REJECTED", "PENDING_INSPECTION"})
    void ownerCanReadCompleteListingInAnyStatusWithoutChangingIt(String status) {
        var post = listing(ownerId);
        post.setStatus(status);
        post.setShippingWeight(2000);
        post.setShippingLength(160);
        post.setShippingWidth(43);
        post.setShippingHeight(18);
        post.setRejectionReason("Saved review reason");
        post.setImages(List.of(new PostImage("https://example.test/front.jpg", null),
                new PostImage("https://example.test/back.jpg", null)));
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));

        var result = service.getMyPostDetail(ownerId, post.getId());

        assertEquals(post.getId(), result.id());
        assertEquals(ownerId, result.user().id());
        assertEquals(status, result.status());
        assertEquals(post.getTitle(), result.title());
        assertEquals(post.getDescription(), result.description());
        assertEquals(new BigDecimal("150000"), result.price());
        assertEquals(List.of("https://example.test/front.jpg", "https://example.test/back.jpg"), result.imageUrls());
        assertEquals(2000, result.shippingWeight());
        assertEquals(160, result.shippingLength());
        assertEquals(43, result.shippingWidth());
        assertEquals(18, result.shippingHeight());
        assertEquals("Saved review reason", result.rejectionReason());
        verify(posts).findById(post.getId());
        verify(posts, never()).save(any());
        verifyNoInteractions(credits, cloud, publication, reviews);
    }

    @Test
    void anotherSellerCannotReadTheListingEvenWhenItIsPublic() {
        var post = listing(UUID.randomUUID());
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));

        assertThrows(ForbiddenException.class, () -> service.getMyPostDetail(ownerId, post.getId()));
        verifyNoInteractions(credits, cloud, publication, reviews);
    }

    @Test
    void missingListingReturnsNotFound() {
        UUID postId = UUID.randomUUID();
        when(posts.findById(postId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getMyPostDetail(ownerId, postId));
        verifyNoInteractions(credits, cloud, publication, reviews);
    }

    @Test
    void listingWithoutAnOwnerCannotBeReadAsAnOwnListing() {
        var post = listing(ownerId);
        post.setUser(null);
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));

        assertThrows(ForbiddenException.class, () -> service.getMyPostDetail(ownerId, post.getId()));
    }

    private Post listing(UUID sellerId) {
        var seller = new User();
        seller.setId(sellerId);
        seller.setEmail("seller@example.test");
        var post = new Post();
        post.setId(UUID.randomUUID());
        post.setUser(seller);
        post.setStatus("ACTIVE");
        post.setTitle("Clothes drying rack");
        post.setDescription("Packed product description");
        post.setPrice(new BigDecimal("150000"));
        return post;
    }
}
