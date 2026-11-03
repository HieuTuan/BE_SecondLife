package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.request.ShippingAddressRequest;
import com.secondlife.secondlife.dto.request.ShippingQuoteRequest;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.ShippingPickupAddress;
import com.secondlife.secondlife.entity.ShippingQuote;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.NegotiationRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.ShippingPickupAddressRepository;
import com.secondlife.secondlife.repository.ShippingQuoteRepository;
import com.secondlife.secondlife.service.impl.ShippingQuoteServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShippingQuoteParcelTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final ShippingPickupAddressRepository addresses = mock(ShippingPickupAddressRepository.class);
    private final ShippingQuoteRepository quotes = mock(ShippingQuoteRepository.class);
    private final ShippingProvider provider = mock(ShippingProvider.class);
    private final ReverseGeocodingService geocoding = mock(ReverseGeocodingService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final ShippingQuoteService service = new ShippingQuoteServiceImpl(posts,
            mock(NegotiationRepository.class), addresses, quotes, mock(InspectionOrderRepository.class),
            mock(ShipmentRepository.class), provider, mapper, geocoding);
    private final Post post = new Post();
    private final UUID buyerId = UUID.randomUUID();
    private final ShippingAddressRequest delivery = new ShippingAddressRequest("Buyer", "0912345678",
            new BigDecimal("10.870"), new BigDecimal("106.684"));

    @BeforeEach
    void setUp() {
        var seller = new User();
        seller.setId(UUID.randomUUID());
        post.setId(UUID.randomUUID());
        post.setUser(seller);
        post.setTitle("Packed item");
        post.setStatus("ACTIVE");
        post.setPrice(new BigDecimal("150000"));
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        var pickup = new ShippingAddress("Seller", "0987654321", "Pickup address",
                new BigDecimal("10.865"), new BigDecimal("106.745"));
        var row = new ShippingPickupAddress();
        row.setAddressJson(mapper.writeValueAsString(pickup));
        when(addresses.findById(seller.getId())).thenReturn(Optional.of(row));
        when(geocoding.shipping(delivery)).thenReturn(new ShippingAddress("Buyer", "0912345678", "Delivery address",
                delivery.latitude(), delivery.longitude()));
        when(provider.code()).thenReturn("LALAMOVE");
        when(quotes.save(any(ShippingQuote.class))).thenAnswer(invocation -> {
            ShippingQuote quote = invocation.getArgument(0);
            quote.setId(UUID.randomUUID());
            return quote;
        });
    }

    @ParameterizedTest
    @CsvSource({"2000,160,43,18,LD_TRUCK_500,340000", "2500,25,20,15,MOTORCYCLE,30000"})
    void responseUsesTheQuotedParcelSnapshotAndPreservesProviderFee(
            int weight, int length, int width, int height, String vehicle, String fee) {
        post.setShippingWeight(weight);
        post.setShippingLength(length);
        post.setShippingWidth(width);
        post.setShippingHeight(height);
        when(provider.preview(anyMap())).thenAnswer(invocation -> {
            // A later entity change must not replace the measurements sent for this quotation.
            post.setShippingLength(999);
            return mapper.createObjectNode()
                    .put("total_fee", new BigDecimal(fee))
                    .put("expires_at", Instant.now().plusSeconds(600).toString())
                    .set("provider_quote", mapper.createObjectNode()
                            .put("serviceType", vehicle).put("quotationId", "quotation-reference"));
        });

        var response = service.quote(buyerId, new ShippingQuoteRequest(post.getId(), null, delivery));
        var saved = ArgumentCaptor.forClass(ShippingQuote.class);
        verify(quotes).save(saved.capture());
        var payload = mapper.readTree(saved.getValue().getPayload());
        var json = mapper.valueToTree(response);

        assertEquals(weight, json.path("shippingWeight").asInt());
        assertEquals(length, json.path("shippingLength").asInt());
        assertEquals(width, json.path("shippingWidth").asInt());
        assertEquals(height, json.path("shippingHeight").asInt());
        assertEquals(weight, payload.path("weight").asInt());
        assertEquals(length, payload.path("length").asInt());
        assertEquals(width, payload.path("width").asInt());
        assertEquals(height, payload.path("height").asInt());
        assertEquals(vehicle, response.serviceType());
        assertEquals(new BigDecimal(fee), response.shippingFee());
        assertEquals(new BigDecimal("150000").add(new BigDecimal(fee)), response.totalPayable());
    }
}
