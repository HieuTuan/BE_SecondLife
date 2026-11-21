package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.impl.ListingPublicationServiceImpl;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ListingPublicationServiceTest {
    @Test
    void appliesConfiguredPublishLimitAndWindow() {
        var posts = mock(PostRepository.class);
        var service = service(posts, mock(InspectionOrderRepository.class), 90, 2);
        UUID userId = UUID.randomUUID();
        when(posts.countByUser_IdAndPublishedAtAfter(eq(userId), any(Instant.class))).thenReturn(2L);
        Instant before = Instant.now().minusSeconds(90);

        assertThrows(ConflictException.class, () -> service.requirePublishAvailable(userId));

        Instant after = Instant.now().minusSeconds(90);
        var cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(posts).countByUser_IdAndPublishedAtAfter(eq(userId), cutoff.capture());
        assertFalse(cutoff.getValue().isBefore(before));
        assertFalse(cutoff.getValue().isAfter(after));
    }

    @Test
    void highValueListingWaitsForSellerMethodSelectionWithoutChargingListingCredit() {
        var inspections = mock(InspectionOrderRepository.class);
        var service = service(mock(PostRepository.class), inspections, 90, 2);
        User seller = new User();
        seller.setId(UUID.randomUUID());
        Post post = new Post();
        post.setUser(seller);
        post.setPrice(new BigDecimal("101"));

        service.accept(post);

        var order = ArgumentCaptor.forClass(InspectionOrder.class);
        verify(inspections).save(order.capture());
        assertEquals("PENDING_INSPECTION", post.getStatus());
        assertEquals("PENDING", order.getValue().getStatus());
        assertNull(order.getValue().getOrderType());
        assertEquals(BigDecimal.ZERO, order.getValue().getSellerFeeShare());
        assertEquals(new BigDecimal("100"), post.getInspectionThresholdSnapshot());
        assertTrue(post.getMandatoryInspection());
    }

    @Test
    void priceExactlyAtThresholdCanPublishWhileHigherPriceCannotBypassInspection() {
        var posts = mock(PostRepository.class);
        var inspections = mock(InspectionOrderRepository.class);
        var service = service(posts, inspections, 90, 2);
        User seller = new User();
        seller.setId(UUID.randomUUID());
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setUser(seller);
        post.setPrice(new BigDecimal("100"));

        service.accept(post);
        assertEquals("ACTIVE", post.getStatus());
        assertFalse(post.getMandatoryInspection());
        verify(inspections, never()).save(any());

        Post expensive = new Post();
        expensive.setId(UUID.randomUUID());
        expensive.setUser(seller);
        expensive.setPrice(new BigDecimal("101"));
        expensive.setMandatoryInspection(true);
        expensive.setInspectionPolicyVersion(1L);
        assertThrows(ConflictException.class, () -> service.activate(expensive));
    }

    @Test
    void changingThePolicyDoesNotReclassifyAnAlreadySubmittedListing() {
        var posts = mock(PostRepository.class);
        var inspections = mock(InspectionOrderRepository.class);
        var service = service(posts, inspections, 90, 2);
        User seller = new User();
        seller.setId(UUID.randomUUID());
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setUser(seller);
        post.setPrice(new BigDecimal("90"));
        post.setInspectionThresholdSnapshot(new BigDecimal("80"));
        post.setInspectionPolicyVersion(0L);
        post.setMandatoryInspection(true);

        service.accept(post);
        assertEquals("PENDING_INSPECTION", post.getStatus());
        assertEquals(new BigDecimal("80"), post.getInspectionThresholdSnapshot());
    }

    @Test
    void hubReturnWaitsForSellerPublicationAndChargesListingCreditOnlyOnce() {
        var posts = mock(PostRepository.class);
        var inspections = mock(InspectionOrderRepository.class);
        var access = mock(ListingAccessService.class);
        var credits = mock(ListingCreditService.class);
        var service = new ListingPublicationServiceImpl(credits, access, posts, inspections,
                mock(HighValueInspectionPolicyService.class), 90, 2);
        User seller = new User();
        seller.setId(UUID.randomUUID());
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setUser(seller);
        post.setMandatoryInspection(true);
        post.setStatus("PENDING_INSPECTION");
        when(posts.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(access.owned(seller.getId(), post.getId(), true)).thenReturn(post);
        when(inspections.existsByPostIdAndInspectionScopeAndStatusIn(post.getId(),
                com.secondlife.secondlife.enums.InspectionScope.PRE_LISTING, List.of("PASSED", "COMPLETED"))).thenReturn(true);

        assertThrows(ConflictException.class, () -> service.publishInspectedPost(seller.getId(), post.getId()));
        service.markReadyAfterInspectionReturn(post.getId());
        assertEquals("READY_TO_PUBLISH", post.getStatus());
        assertNull(post.getPublishedAt());
        verifyNoInteractions(credits);

        service.publishInspectedPost(seller.getId(), post.getId());
        service.publishInspectedPost(seller.getId(), post.getId());
        service.markReadyAfterInspectionReturn(post.getId());
        assertEquals("ACTIVE", post.getStatus());
        assertNotNull(post.getPublishedAt());
        verify(credits).consume(seller.getId(), com.secondlife.secondlife.enums.CreditType.LISTING,
                "LISTING_PUBLISH:" + post.getId());
    }

    private static ListingPublicationService service(PostRepository posts, InspectionOrderRepository inspections,
                                                     long window, int limit) {
        HighValueInspectionPolicyService policies = mock(HighValueInspectionPolicyService.class);
        when(policies.get()).thenReturn(new com.secondlife.secondlife.dto.response.HighValueInspectionPolicyResponse(
                new BigDecimal("100"), "VND", 1, Instant.now(), null));
        return new ListingPublicationServiceImpl(mock(ListingCreditService.class), mock(ListingAccessService.class),
                posts, inspections, policies, window, limit);
    }
}
