package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PostRouteMergeTest {
    @Test
    void mergedPostControllersKeepPublicListAndOwnerDraftRoutesDistinct() throws Exception {
        var posts = mock(PostService.class);
        var listings = mock(MarketplaceListingService.class);
        var drafts = mock(ListingDraftService.class);
        var current = mock(CurrentUserProvider.class);
        UUID sellerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(current.resolveUserId(any())).thenReturn(sellerId);
        when(listings.list(isNull(), isNull(), any())).thenAnswer(call ->
                new PageImpl<>(List.of(), call.getArgument(2), 0));

        var mvc = MockMvcBuilders.standaloneSetup(
                new PostController(posts, current, listings),
                new ListingValuationController(mock(AiValuationService.class), drafts, current, 100))
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver()).build();

        mvc.perform(get("/api/v1/posts").param("page", "0").param("size", "10"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/posts/" + postId)).andExpect(status().isOk());
        verify(listings).list(isNull(), isNull(), any());
        verify(drafts).get(sellerId, postId);
        verifyNoInteractions(posts);
    }
}
