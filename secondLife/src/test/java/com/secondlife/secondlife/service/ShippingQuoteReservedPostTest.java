package com.secondlife.secondlife.service;

import tools.jackson.databind.ObjectMapper;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingAddressRequest;
import com.secondlife.secondlife.dto.request.ShippingQuoteRequest;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.Shipment;
import com.secondlife.secondlife.entity.ShippingPickupAddress;
import com.secondlife.secondlife.entity.ShippingQuote;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
import com.secondlife.secondlife.mapper.ShippingVehicleSelectionMapper;
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
                inspections, shipments, provider, mapper, geocoding, mock(ShippingVehicleSelectionMapper.class));
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

    @Test
    void activePostWithCancelledInspectionAllowsNewBuyerDirectShippingQuote() {
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setStatus("ACTIVE");
        post.setTitle("Philips vacuum");
        post.setPrice(new BigDecimal("500000"));
        post.setShippingWeight(4000);
        post.setShippingLength(30);
        post.setShippingWidth(10);
        post.setShippingHeight(80);
        User seller = new User();
        seller.setId(sellerId);
        post.setUser(seller);

        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        when(inspections.findFirstByPostIdAndStatusOrderByCreatedAtDesc(post.getId(), "PASSED")).thenReturn(Optional.empty());
        when(inspections.findActiveByPostId(post.getId())).thenReturn(Optional.empty());

        var pickupAddress = new ShippingPickupAddress();
        pickupAddress.setUserId(sellerId);
        pickupAddress.setAddressJson("{\"name\":\"Seller\",\"phone\":\"0988888888\",\"address\":\"123 Seller St\",\"latitude\":10.87,\"longitude\":106.68}");
        when(addresses.findById(sellerId)).thenReturn(Optional.of(pickupAddress));

        ObjectMapper realMapper = new ObjectMapper();
        var realService = new com.secondlife.secondlife.service.impl.ShippingQuoteServiceImpl(posts, negotiations, addresses, quotes,
                inspections, shipments, provider, realMapper, geocoding, mock(ShippingVehicleSelectionMapper.class));

        ShippingAddress delivery = new ShippingAddress("Buyer", "0912345678", "456 Buyer St", new BigDecimal("10.85"), new BigDecimal("106.65"));
        when(geocoding.shipping(any())).thenReturn(delivery);

        when(provider.code()).thenReturn("LALAMOVE");
        var previewResult = realMapper.createObjectNode();
        previewResult.put("total_fee", new BigDecimal("45000"));
        var providerQuote = previewResult.putObject("provider_quote");
        providerQuote.put("serviceType", "MOTORCYCLE");
        previewResult.put("expires_at", Instant.now().plusSeconds(300).toString());
        when(provider.preview(any())).thenReturn(previewResult);

        when(quotes.save(any(ShippingQuote.class))).thenAnswer(invocation -> {
            ShippingQuote sq = invocation.getArgument(0);
            sq.setId(UUID.randomUUID());
            return sq;
        });

        ShippingAddressRequest deliveryAddress = new ShippingAddressRequest("Buyer", "0912345678", new BigDecimal("10.85"), new BigDecimal("106.65"));
        ShippingQuoteRequest request = new ShippingQuoteRequest(post.getId(), null, deliveryAddress);

        var response = realService.quote(buyerId, request);
        assertNotNull(response);
        assertEquals("SELLER_TO_BUYER", response.leg());
        assertEquals(new BigDecimal("45000"), response.shippingFee());
    }

    @Test
    void passedInspectionReturnedToSellerAllowsDirectPurchaseAsSellerToBuyer() {
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setStatus("ACTIVE");
        post.setTitle("Philips vacuum");
        post.setPrice(new BigDecimal("500000"));
        post.setShippingWeight(4000);
        post.setShippingLength(30);
        post.setShippingWidth(10);
        post.setShippingHeight(80);
        User seller = new User();
        seller.setId(sellerId);
        post.setUser(seller);

        when(posts.findById(post.getId())).thenReturn(Optional.of(post));

        var inspection = new InspectionOrder();
        inspection.setId(UUID.randomUUID());
        inspection.setStatus("PASSED");
        inspection.setOrderType(InspectionOrderType.AT_CENTER);
        when(inspections.findFirstByPostIdAndStatusOrderByCreatedAtDesc(post.getId(), "PASSED")).thenReturn(Optional.of(inspection));

        var returnShipment = new Shipment();
        returnShipment.setLeg("CENTER_TO_SELLER");
        returnShipment.setStatus("DELIVERED");
        when(shipments.findByInspectionOrderIdOrderByCreatedAtAsc(inspection.getId())).thenReturn(java.util.List.of(returnShipment));

        var pickupAddress = new ShippingPickupAddress();
        pickupAddress.setUserId(sellerId);
        pickupAddress.setAddressJson("{\"name\":\"Seller\",\"phone\":\"0988888888\",\"address\":\"123 Seller St\",\"latitude\":10.87,\"longitude\":106.68}");
        when(addresses.findById(sellerId)).thenReturn(Optional.of(pickupAddress));

        ObjectMapper realMapper = new ObjectMapper();
        var realService = new com.secondlife.secondlife.service.impl.ShippingQuoteServiceImpl(posts, negotiations, addresses, quotes,
                inspections, shipments, provider, realMapper, geocoding, mock(ShippingVehicleSelectionMapper.class));

        ShippingAddress delivery = new ShippingAddress("Buyer", "0912345678", "456 Buyer St", new BigDecimal("10.85"), new BigDecimal("106.65"));
        when(geocoding.shipping(any())).thenReturn(delivery);

        when(provider.code()).thenReturn("LALAMOVE");
        var previewResult = realMapper.createObjectNode();
        previewResult.put("total_fee", new BigDecimal("45000"));
        var providerQuote = previewResult.putObject("provider_quote");
        providerQuote.put("serviceType", "MOTORCYCLE");
        previewResult.put("expires_at", Instant.now().plusSeconds(300).toString());
        when(provider.preview(any())).thenReturn(previewResult);

        when(quotes.save(any(ShippingQuote.class))).thenAnswer(invocation -> {
            ShippingQuote sq = invocation.getArgument(0);
            sq.setId(UUID.randomUUID());
            return sq;
        });

        ShippingAddressRequest deliveryAddress = new ShippingAddressRequest("Buyer", "0912345678", new BigDecimal("10.85"), new BigDecimal("106.65"));
        ShippingQuoteRequest request = new ShippingQuoteRequest(post.getId(), null, deliveryAddress);

        var response = realService.quote(buyerId, request);
        assertNotNull(response);
        assertEquals("SELLER_TO_BUYER", response.leg());
    }

    @Test
    void passedAtHomeInspectionAllowsDirectPurchaseAsSellerToBuyer() {
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setStatus("ACTIVE");
        post.setTitle("Philips vacuum");
        post.setPrice(new BigDecimal("500000"));
        post.setShippingWeight(4000);
        post.setShippingLength(30);
        post.setShippingWidth(10);
        post.setShippingHeight(80);
        User seller = new User();
        seller.setId(sellerId);
        post.setUser(seller);

        when(posts.findById(post.getId())).thenReturn(Optional.of(post));

        var inspection = new InspectionOrder();
        inspection.setId(UUID.randomUUID());
        inspection.setStatus("PASSED");
        inspection.setOrderType(InspectionOrderType.AT_HOME);
        when(inspections.findFirstByPostIdAndStatusOrderByCreatedAtDesc(post.getId(), "PASSED")).thenReturn(Optional.of(inspection));
        when(shipments.findByInspectionOrderIdOrderByCreatedAtAsc(inspection.getId())).thenReturn(java.util.List.of());

        var pickupAddress = new ShippingPickupAddress();
        pickupAddress.setUserId(sellerId);
        pickupAddress.setAddressJson("{\"name\":\"Seller\",\"phone\":\"0988888888\",\"address\":\"123 Seller St\",\"latitude\":10.87,\"longitude\":106.68}");
        when(addresses.findById(sellerId)).thenReturn(Optional.of(pickupAddress));

        ObjectMapper realMapper = new ObjectMapper();
        var realService = new com.secondlife.secondlife.service.impl.ShippingQuoteServiceImpl(posts, negotiations, addresses, quotes,
                inspections, shipments, provider, realMapper, geocoding, mock(ShippingVehicleSelectionMapper.class));

        ShippingAddress delivery = new ShippingAddress("Buyer", "0912345678", "456 Buyer St", new BigDecimal("10.85"), new BigDecimal("106.65"));
        when(geocoding.shipping(any())).thenReturn(delivery);

        when(provider.code()).thenReturn("LALAMOVE");
        var previewResult = realMapper.createObjectNode();
        previewResult.put("total_fee", new BigDecimal("45000"));
        var providerQuote = previewResult.putObject("provider_quote");
        providerQuote.put("serviceType", "MOTORCYCLE");
        previewResult.put("expires_at", Instant.now().plusSeconds(300).toString());
        when(provider.preview(any())).thenReturn(previewResult);

        when(quotes.save(any(ShippingQuote.class))).thenAnswer(invocation -> {
            ShippingQuote sq = invocation.getArgument(0);
            sq.setId(UUID.randomUUID());
            return sq;
        });

        ShippingAddressRequest deliveryAddress = new ShippingAddressRequest("Buyer", "0912345678", new BigDecimal("10.85"), new BigDecimal("106.65"));
        ShippingQuoteRequest request = new ShippingQuoteRequest(post.getId(), null, deliveryAddress);

        var response = realService.quote(buyerId, request);
        assertNotNull(response);
        assertEquals("SELLER_TO_BUYER", response.leg());
    }
}
