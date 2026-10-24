package com.secondlife.secondlife.service;

import tools.jackson.databind.ObjectMapper;
import com.secondlife.secondlife.dto.request.ShippingAddressRequest;
import com.secondlife.secondlife.dto.request.ShippingQuoteRequest;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
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
        service = new com.secondlife.secondlife.service.impl.ShippingQuoteServiceImpl(posts, negotiations, addresses, quotes,
                inspections, shipments, provider, mapper, geocoding);
    }

    @Test
    void checkoutQuoteStillRequiresTheSellerPickupAddress() {
        var post=new Post();post.setId(UUID.randomUUID());post.setStatus("ACTIVE");
        post.setPrice(BigDecimal.valueOf(100000));post.setTitle("Appliance");
        post.setShippingWeight(10000);post.setShippingLength(40);post.setShippingWidth(40);post.setShippingHeight(40);
        var seller=new User();seller.setId(UUID.randomUUID());post.setUser(seller);
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        when(addresses.findById(seller.getId())).thenReturn(Optional.empty());
        var delivery=new ShippingAddressRequest("Buyer","0912345678",BigDecimal.valueOf(21.0333),BigDecimal.valueOf(105.7937));
        var request=new ShippingQuoteRequest(post.getId(),null,delivery);
        var error=assertThrows(com.secondlife.secondlife.exception.BadRequestException.class,()->service.quote(UUID.randomUUID(),request));
        assertEquals("Seller pickup address is not configured",error.getMessage());
        verifyNoInteractions(provider,quotes,geocoding);
    }
    @Test
    void missingPickupAddressReturnsEmptyForOnboarding() {
        UUID userId = UUID.randomUUID();
        when(addresses.findById(userId)).thenReturn(Optional.empty());
        assertNull(service.pickup(userId));
        verifyNoInteractions(mapper);
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
