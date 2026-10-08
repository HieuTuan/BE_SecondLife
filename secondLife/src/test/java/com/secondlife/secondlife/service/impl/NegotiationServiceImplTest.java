package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.NegotiationRequestDTO;
import com.secondlife.secondlife.entity.Negotiation;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.NegotiationStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.repository.NegotiationRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NegotiationServiceImplTest {
    @Test
    void appliesConfiguredRejectionLimit() {
        var negotiations = mock(NegotiationRepository.class);
        var posts = mock(PostRepository.class);
        var users = mock(UserRepository.class);
        User buyer = user();
        Post post = post(user());
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        when(users.findById(buyer.getId())).thenReturn(Optional.of(buyer));
        when(negotiations.countRejectedNegotiations(post.getId(), buyer.getId())).thenReturn(1L);
        when(negotiations.findMaxRejectedPrice(post.getId(), buyer.getId())).thenReturn(Optional.empty());
        when(negotiations.save(any(Negotiation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new NegotiationRequestDTO();
        request.setPostId(post.getId());
        request.setOfferedPrice(new BigDecimal("40"));

        var limitOne = new NegotiationServiceImpl(negotiations, posts, users, mock(com.secondlife.secondlife.service.ChatService.class), 1, 2L);
        assertThrows(BadRequestException.class, () -> limitOne.createNegotiation(buyer.getId(), request));
        var limitTwo = new NegotiationServiceImpl(negotiations, posts, users, mock(com.secondlife.secondlife.service.ChatService.class), 2, 2L);
        assertEquals(NegotiationStatus.PENDING, limitTwo.createNegotiation(buyer.getId(), request).getStatus());
    }

    @Test
    void acceptedNegotiationUsesConfiguredExpiry() {
        var negotiations = mock(NegotiationRepository.class);
        User seller = user();
        Negotiation negotiation = new Negotiation();
        negotiation.setId(UUID.randomUUID());
        negotiation.setPost(post(seller));
        negotiation.setBuyer(user());
        negotiation.setStatus(NegotiationStatus.PENDING);
        when(negotiations.findById(negotiation.getId())).thenReturn(Optional.of(negotiation));
        when(negotiations.save(any(Negotiation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var service = new NegotiationServiceImpl(negotiations, mock(PostRepository.class), mock(UserRepository.class), mock(com.secondlife.secondlife.service.ChatService.class), 3, 2L);

        Instant before = Instant.now().plus(2, ChronoUnit.HOURS);
        var accepted = service.acceptNegotiation(seller.getId(), negotiation.getId());
        Instant after = Instant.now().plus(2, ChronoUnit.HOURS);

        assertEquals(NegotiationStatus.ACCEPTED, accepted.getStatus());
        assertFalse(accepted.getExpiredAt().isBefore(before));
        assertFalse(accepted.getExpiredAt().isAfter(after));
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static Post post(User seller) {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setUser(seller);
        post.setStatus("ACTIVE");
        post.setPrice(new BigDecimal("100"));
        return post;
    }
}
