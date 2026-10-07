package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.AiPriceEstimate;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.PostImage;
import com.secondlife.secondlife.exception.AiProviderException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiValuationVisionTest {
    private final ListingAccessService access = mock(ListingAccessService.class);
    private final ListingCreditService credits = mock(ListingCreditService.class);
    private final AiPriceEstimateRepository estimates = mock(AiPriceEstimateRepository.class);
    private final PostRepository posts = mock(PostRepository.class);
    private final AiPriceProvider provider = mock(AiPriceProvider.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final ItemRepository items = mock(ItemRepository.class);
    private final AiValuationService service = new AiValuationService(access, credits, estimates, posts,
            provider, JsonMapper.builder().build(), categories, items);
    private final UUID userId = UUID.randomUUID(), postId = UUID.randomUUID(), requestId = UUID.randomUUID();

    private Post savedProduct() {
        Post post = new Post();
        post.setId(postId);
        post.setTitle("Ghế gỗ");
        post.setDescription("Ghế có vết xước");
        post.setDescriptionAccepted(true);
        post.setImages(List.of(new PostImage("https://res.cloudinary.com/demo/image/upload/front.jpg", "front"),
                new PostImage("https://res.cloudinary.com/demo/image/upload/back.png", "back"),
                new PostImage("https://res.cloudinary.com/demo/image/upload/side.webp", "side")));
        when(access.owned(userId, postId, true)).thenReturn(post);
        when(estimates.findByListingIdAndRequestId(postId, requestId)).thenReturn(Optional.empty());
        return post;
    }

    @Test void valuesPersistedImagesAndRecordsTheExactSameSnapshot() {
        Post post = savedProduct();
        when(provider.estimate(anyString(), anyList())).thenReturn(new AiPriceProvider.PriceSuggestion(
                new BigDecimal("1000000"), new BigDecimal("1500000"), new BigDecimal("1200000"), "1 tuần", "gemini-test"));
        when(estimates.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(userId, postId, requestId);

        var snapshot = ArgumentCaptor.forClass(String.class);
        verify(provider).estimate(snapshot.capture(), eq(post.getImageUrls()));
        var saved = ArgumentCaptor.forClass(AiPriceEstimate.class);
        verify(estimates).save(saved.capture());
        assertEquals(snapshot.getValue(), saved.getValue().getInputSnapshot());
        var input = JsonMapper.builder().build().readTree(snapshot.getValue());
        assertEquals("Ghế có vết xước", input.get("description").asText());
        assertEquals(3, input.get("imageUrls").size());
        for (int index = 0; index < 3; index++)
            assertEquals(post.getImageUrls().get(index), input.get("imageUrls").get(index).asText());
        assertEquals(ListingFingerprint.sha256(snapshot.getValue()), saved.getValue().getInputFingerprint());
        assertEquals("gemini-test", saved.getValue().getModelVersion());
    }

    @Test void unacceptedDescriptionDoesNotConsumeCreditOrCallAi() {
        Post post = savedProduct();
        post.setDescriptionAccepted(false);
        assertThrows(ConflictException.class, () -> service.create(userId, postId, requestId));
        verifyNoInteractions(credits, provider);
        verify(estimates, never()).save(any());
    }

    @Test void failedVisionDoesNotPersistEstimateOrSuggestedPrice() {
        Post post = savedProduct();
        when(provider.estimate(anyString(), anyList())).thenThrow(new AiProviderException("Invalid image result"));
        assertThrows(AiProviderException.class, () -> service.create(userId, postId, requestId));
        assertNull(post.getAiSuggestedPrice());
        verify(estimates, never()).save(any());
        verify(posts, never()).save(any());
    }
}
