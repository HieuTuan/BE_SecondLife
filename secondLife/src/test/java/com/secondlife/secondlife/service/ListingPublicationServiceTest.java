package com.secondlife.secondlife.service;

import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
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
    void highValueListingUsesConfiguredInspectionFees() {
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
        assertEquals(new BigDecimal("20"), order.getValue().getInspectionFee());
        assertEquals(new BigDecimal("5"), order.getValue().getShippingFee());
    }

    private static ListingPublicationService service(PostRepository posts, InspectionOrderRepository inspections,
                                                     long window, int limit) {
        return new ListingPublicationService(mock(ListingCreditService.class), mock(ListingAccessService.class),
                posts, inspections, new BigDecimal("100"), new BigDecimal("20"), new BigDecimal("5"), window, limit);
    }
}
