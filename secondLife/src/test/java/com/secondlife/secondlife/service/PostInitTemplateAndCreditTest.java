package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.AiChatRequest;
import com.secondlife.secondlife.dto.request.PostInitRequest;
import com.secondlife.secondlife.dto.response.AiChatResponse;
import com.secondlife.secondlife.dto.response.PostInitResponse;
import com.secondlife.secondlife.dto.response.ResolvedQuestionTemplateResponse;
import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.Item;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.CreditType;
import com.secondlife.secondlife.exception.AiProviderException;
import com.secondlife.secondlife.integration.ai.AiChatGateway;
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
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PostInitTemplateAndCreditTest {

    private PostRepository postRepository;
    private UserRepository userRepository;
    private ListingCreditService listingCredits;
    private AiChatService aiChatService;
    private CategoryTemplateService categoryTemplateService;
    private CategoryQuestionTemplateRepository templateRepository;
    private AiChatSessionRepository sessionRepository;
    private CategoryRepository categoryRepository;
    private ItemRepository itemRepository;
    private CloudinaryService cloudinaryService;
    private ListingImageSimilarity imageSimilarity;
    private ListingFingerprint listingFingerprint;
    private PostServiceImpl postService;

    private UUID userId;
    private UUID categoryId;
    private UUID itemId;
    private UUID draftPostId;

    @BeforeEach
    void setUp() throws Exception {
        postRepository = mock(PostRepository.class);
        userRepository = mock(UserRepository.class);
        listingCredits = mock(ListingCreditService.class);
        aiChatService = mock(AiChatService.class);
        categoryTemplateService = mock(CategoryTemplateService.class);
        templateRepository = mock(CategoryQuestionTemplateRepository.class);
        sessionRepository = mock(AiChatSessionRepository.class);
        categoryRepository = mock(CategoryRepository.class);
        itemRepository = mock(ItemRepository.class);
        cloudinaryService = mock(CloudinaryService.class);
        imageSimilarity = mock(ListingImageSimilarity.class);
        listingFingerprint = mock(ListingFingerprint.class);

        postService = new PostServiceImpl(
                postRepository,
                userRepository,
                listingCredits,
                mock(ListingAccessService.class),
                aiChatService,
                categoryTemplateService,
                templateRepository,
                sessionRepository,
                mock(AiChatMessageRepository.class),
                categoryRepository,
                itemRepository,
                mock(AiChatGateway.class),
                cloudinaryService,
                mock(InspectionOrderRepository.class),
                mock(AiChatGateway.class),
                mock(ListingPublicationService.class),
                mock(ListingReviewService.class),
                imageSimilarity,
                listingFingerprint,
                BigDecimal.valueOf(50000),
                BigDecimal.valueOf(30000),
                5,
                10 * 1024 * 1024L,
                1,
                6,
                mock(PostMapper.class),
                null
        );

        userId = UUID.randomUUID();
        categoryId = UUID.randomUUID();
        itemId = UUID.randomUUID();
        draftPostId = UUID.randomUUID();

        User user = new User();
        user.setId(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.getReferenceById(userId)).thenReturn(user);

        Category category = new Category();
        category.setId(categoryId);
        category.setName("Điện tử");
        when(categoryRepository.existsById(categoryId)).thenReturn(true);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));

        Item item = new Item();
        item.setId(itemId);
        item.setCategory(category);
        item.setName("Tivi");
        when(itemRepository.findById(itemId)).thenReturn(Optional.of(item));

        when(postRepository.countByUser_IdAndStatusIn(eq(userId), anyList())).thenReturn(0L);
        when(imageSimilarity.fingerprint(any(byte[].class))).thenReturn("010101");
        when(listingFingerprint.sha256(any(byte[].class))).thenReturn("abc123sha");
        when(cloudinaryService.uploadImage(any())).thenReturn("https://cdn.example.com/tv.jpg");
        when(postRepository.save(any(Post.class))).thenAnswer(inv -> {
            Post p = inv.getArgument(0);
            if (p.getId() == null) {
                p.setId(draftPostId);
            }
            return p;
        });
    }

    @Test
    void initPostReturnsTemporaryAiQuestionsWhileKeepingOfficialPostTemplateNullUntilAdminApproval() {
        UUID sessionId = UUID.randomUUID();
        when(aiChatService.processChat(any(AiChatRequest.class), eq(userId)))
                .thenReturn(AiChatResponse.builder()
                        .sessionId(sessionId)
                        .reply("[HỢP_LỆ] Tivi màn hình phẳng viền mỏng màu đen còn đẹp.")
                        .build());
        when(sessionRepository.findById(sessionId)).thenReturn(Optional.empty());

        String tempQuestions = "**Kích thước màn hình Tivi là bao nhiêu inch?** (Ví dụ: 43 inch, 55 inch, 65 inch)";
        when(categoryTemplateService.resolveOrGenerateForListing(categoryId, itemId, userId))
                .thenReturn(new ResolvedQuestionTemplateResponse(null, tempQuestions, true));

        MockMultipartFile img = new MockMultipartFile(
                "images", "tv.jpg", "image/jpeg", new byte[]{1, 2, 3, 4}
        );
        PostInitRequest req = new PostInitRequest(categoryId, itemId, "Samsung", "Q60B", List.of(img), "ai");

        PostInitResponse resp = postService.initPost(userId, req);

        assertEquals(draftPostId, resp.postId());
        assertEquals(tempQuestions, resp.aiInitialMessage());
        assertEquals("Tivi màn hình phẳng viền mỏng màu đen còn đẹp.", resp.aiDescription());

        ArgumentCaptor<Post> savedPosts = ArgumentCaptor.forClass(Post.class);
        verify(postRepository, org.mockito.Mockito.times(2)).save(savedPosts.capture());
        Post updatedPost = savedPosts.getAllValues().getLast();
        assertNull(updatedPost.getTemplate(), "Temporary AI proposal must not bind unapproved template_id on posts");
    }

    @Test
    void initPostRefundsCreditAndDeletesOrphanDraftWhenAiVisionScanFails() {
        when(aiChatService.processChat(any(AiChatRequest.class), eq(userId)))
                .thenThrow(new AiProviderException("Gemini Vision unavailable"));

        MockMultipartFile img = new MockMultipartFile(
                "images", "tv.jpg", "image/jpeg", new byte[]{1, 2, 3, 4}
        );
        PostInitRequest req = new PostInitRequest(categoryId, itemId, "Samsung", null, List.of(img), "ai");

        assertThrows(AiProviderException.class, () -> postService.initPost(userId, req));

        ArgumentCaptor<String> consumeKey = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> refundKey = ArgumentCaptor.forClass(String.class);
        verify(listingCredits).consumeImmediately(eq(userId), eq(CreditType.AI_CHAT), consumeKey.capture());
        verify(listingCredits).refund(eq(userId), eq(CreditType.AI_CHAT), refundKey.capture());
        assertTrue(refundKey.getValue().startsWith(consumeKey.getValue()));
        assertTrue(refundKey.getValue().endsWith(":REFUND"));
        verify(postRepository).deleteById(draftPostId);
    }
}
