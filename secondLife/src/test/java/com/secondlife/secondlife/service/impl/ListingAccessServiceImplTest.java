package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.SellerReputationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListingAccessServiceImplTest {

    @Mock
    private UserRepository users;

    @Mock
    private SellerReputationService reputation;

    @Mock
    private PostRepository posts;

    @Mock
    private SellerVerificationRepository verifications;

    @InjectMocks
    private ListingAccessServiceImpl listingAccessService;

    private UUID userId;
    private UUID postId;
    private User owner;
    private Post post;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        postId = UUID.randomUUID();
        owner = new User();
        owner.setId(userId);
        post = new Post();
        post.setId(postId);
        post.setUser(owner);
        post.setStatus("DRAFT");
    }

    @Test
    void ownedWithLockTrue_acquiresPostLockAndDoesNotLockUsers() {
        when(posts.findByIdForUpdate(postId)).thenReturn(Optional.of(post));

        Post result = listingAccessService.owned(userId, postId, true);

        assertNotNull(result);
        assertEquals(postId, result.getId());
        verify(posts).findByIdForUpdate(postId);
        verify(posts, never()).findById(postId);
        verify(users, never()).findByIdForRoleUpdate(userId);
    }

    @Test
    void ownedWithLockFalse_usesFindByIdAndDoesNotLockUsers() {
        when(posts.findById(postId)).thenReturn(Optional.of(post));

        Post result = listingAccessService.owned(userId, postId, false);

        assertNotNull(result);
        assertEquals(postId, result.getId());
        verify(posts).findById(postId);
        verify(posts, never()).findByIdForUpdate(postId);
        verify(users, never()).findByIdForRoleUpdate(userId);
    }

    @Test
    void owned_throwsNotFoundWhenPostDoesNotExist() {
        when(posts.findById(postId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> listingAccessService.owned(userId, postId, false));
    }

    @Test
    void owned_throwsForbiddenWhenPostBelongsToAnotherUser() {
        User otherUser = new User();
        otherUser.setId(UUID.randomUUID());
        post.setUser(otherUser);

        when(posts.findById(postId)).thenReturn(Optional.of(post));

        assertThrows(ForbiddenException.class, () -> listingAccessService.owned(userId, postId, false));
    }

    @Test
    void requireDraft_allowsDraftOrRejected() {
        post.setStatus("DRAFT");
        listingAccessService.requireDraft(post);

        post.setStatus("REJECTED");
        listingAccessService.requireDraft(post);
    }

    @Test
    void requireDraft_rejectsActiveOrSubmitted() {
        post.setStatus("ACTIVE");
        assertThrows(ConflictException.class, () -> listingAccessService.requireDraft(post));

        post.setStatus("DRAFT");
        post.setListingCreditCharged(true);
        assertThrows(ConflictException.class, () -> listingAccessService.requireDraft(post));
    }
}
