package com.secondlife.secondlife.service;

import tools.jackson.databind.ObjectMapper;
import com.secondlife.secondlife.service.shipping.ReverseGeocodingService;
import com.secondlife.secondlife.dto.shipping.ShippingAddress;
import com.secondlife.secondlife.dto.shipping.ShippingAddressRequest;
import com.secondlife.secondlife.dto.shipping.ShippingQuoteRequest;
import com.secondlife.secondlife.entity.Negotiation;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.NegotiationStatus;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.shipping.ShippingProvider;
import com.secondlife.secondlife.service.shipping.ShippingQuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ShippingQuoteReservedPostTest {

    private PostRepository posts;
    private NegotiationRepository negotiations;
    private ShippingPickupAddressRepository addresses;
    private ShippingQuoteRepository quotes;
    private InspectionOrderRepository inspections;
    private ShipmentRepository shipments;
    private ShippingProvider provider;
    private ReverseGeocodingService geocoding;
    private ObjectMapper mapper;
    private ShippingQuoteService service;

    @BeforeEach
    void setUp() {
        posts = mock(PostRepository.class);
        negotiations = mock(NegotiationRepository.class);
        addresses = mock(ShippingPickupAddressRepository.class);
        quotes = mock(ShippingQuoteRepository.class);
        inspections = mock(InspectionOrderRepository.class);
        shipments = mock(ShipmentRepository.class);
        provider = mock(ShippingProvider.class);
        geocoding = mock(ReverseGeocodingService.class);
        mapper = mock(ObjectMapper.class);
        service = new ShippingQuoteService(posts, negotiations, addresses, quotes,
                inspections, shipments, provider, mapper, geocoding);
    }

    @Test
    void reservedPostWithoutNegotiationThrowsConflictException() {
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setStatus("RESERVED");
        User seller = new User();
        seller.setId(sellerId);
        post.setUser(seller);

        when(posts.findById(post.getId())).thenReturn(Optional.of(post));

        ShippingAddressRequest deliveryAddress = new ShippingAddressRequest("Buyer", "0912345678", BigDecimal.valueOf(21.0333), BigDecimal.valueOf(105.7937));
        ShippingQuoteRequest request = new ShippingQuoteRequest(post.getId(), null, deliveryAddress);

        var ex = assertThrows(ConflictException.class, () -> service.quote(buyerId, request));
        assertTrue(ex.getMessage().contains("Post is reserved"));
    }

    @Test
    void nonActiveOrReservedPostThrowsConflictException() {
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setStatus("SOLD");
        User seller = new User();
        seller.setId(sellerId);
        post.setUser(seller);

        when(posts.findById(post.getId())).thenReturn(Optional.of(post));

        ShippingAddressRequest deliveryAddress = new ShippingAddressRequest("Buyer", "0912345678", BigDecimal.valueOf(21.0333), BigDecimal.valueOf(105.7937));
        ShippingQuoteRequest request = new ShippingQuoteRequest(post.getId(), null, deliveryAddress);

        var ex = assertThrows(ConflictException.class, () -> service.quote(buyerId, request));
        assertTrue(ex.getMessage().contains("Post is not available for shipping quotation"));
    }
}
