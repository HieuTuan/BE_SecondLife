package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.entity.InspectionReturnShippingPolicy;
import com.secondlife.secondlife.entity.InspectionShippingQuote;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.integration.shipping.ShippingProvider;
import com.secondlife.secondlife.mapper.InspectionShippingQuoteMapper;
import com.secondlife.secondlife.mapper.ShippingVehicleSelectionMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.InspectionReturnShippingPolicyRepository;
import com.secondlife.secondlife.repository.InspectionShippingQuoteRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.InspectionCenterProfileService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import com.secondlife.secondlife.mapper.MapperTestSupport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InspectionShippingQuoteServiceImplTest {
    @Test
    void estimatesReturnReserveFromOneQuoteAndReusesTheSavedAmount() {
        var quotes = mock(InspectionShippingQuoteRepository.class);
        var policies = mock(InspectionReturnShippingPolicyRepository.class);
        var posts = mock(PostRepository.class);
        var centers = mock(InspectionCenterProfileService.class);
        var shipping = mock(ShippingQuoteService.class);
        var provider = mock(ShippingProvider.class);
        var json = new ObjectMapper();
        var service = new InspectionShippingQuoteServiceImpl(quotes, policies, mock(InspectionOrderRepository.class),
                posts, centers, shipping, provider, MapperTestSupport.mapper(InspectionShippingQuoteMapper.class),
                mock(ShippingVehicleSelectionMapper.class), json);

        UUID centerId = UUID.randomUUID();
        var seller = new User();
        seller.setId(UUID.randomUUID());
        var post = new Post();
        post.setId(UUID.randomUUID());
        post.setUser(seller);
        post.setTitle("Khung giường");
        post.setPrice(new BigDecimal("500000"));
        when(posts.findById(post.getId())).thenReturn(Optional.of(post));
        when(shipping.pickup(seller.getId())).thenReturn(new ShippingAddress("Seller", "0912345678",
                "Địa chỉ người bán", new BigDecimal("10.77"), new BigDecimal("106.69")));
        when(centers.requireShippingAddress(centerId)).thenReturn(new ShippingAddress("Center", "0912345679",
                "Địa chỉ trung tâm", new BigDecimal("10.78"), new BigDecimal("106.70")));
        var policy = new InspectionReturnShippingPolicy();
        policy.setMarkupPercentage(new BigDecimal("20"));
        when(policies.findById((short) 1)).thenReturn(Optional.of(policy));

        var expiry = Instant.now().plusSeconds(300);
        when(provider.code()).thenReturn("LALAMOVE");
        when(provider.preview(anyMap())).thenReturn(json.valueToTree(Map.of(
                "total_fee", 30000, "expires_at", expiry.toString(),
                "provider_quote", Map.of("quotationId", "Q1", "serviceType", "MOTORCYCLE"))));
        when(quotes.saveAndFlush(any(InspectionShippingQuote.class))).thenAnswer(invocation -> {
            InspectionShippingQuote row = invocation.getArgument(0);
            row.setId(UUID.randomUUID());
            when(quotes.findById(row.getId())).thenReturn(Optional.of(row));
            return row;
        });

        var quote = service.quote(post.getId(), centerId, null);
        policy.setMarkupPercentage(new BigDecimal("30"));
        var reused = service.quote(post.getId(), centerId, quote.id());

        assertEquals(new BigDecimal("30000"), quote.outbound().shippingFee());
        assertEquals(new BigDecimal("36000"), quote.returnReserve().shippingFee());
        assertNull(quote.returnReserve().providerQuotationId());
        assertEquals(expiry, quote.expiresAt());
        assertEquals(quote, reused);
        verify(provider, times(1)).preview(anyMap());
    }
}
