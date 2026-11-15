package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.InspectionQuoteRequest;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.InspectionConfigurationResponse;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.InspectionComplexityLevel;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.mapper.InspectionPricingMapper;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionFeeProposalRepository;
import com.secondlife.secondlife.repository.InspectionTravelTierRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.service.InspectionConfigurationService;
import com.secondlife.secondlife.service.InspectionGovernanceAccessService;
import com.secondlife.secondlife.service.InspectionLocationService;
import com.secondlife.secondlife.service.InspectionLocationService.HubSelection;
import com.secondlife.secondlife.service.InspectionShippingQuoteService;

import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionPricingServiceImplTest {
    @Mock PostRepository postRepository;
    @Mock InspectionTravelFeeCalculator travelFeeCalculator;
    @Mock InspectionFeeProposalRepository proposalRepository;
    @Mock InspectionTravelTierRepository travelTierRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock InspectionLocationService locations;
    @Mock InspectionShippingQuoteService inspectionShipping;
    @Mock ObjectMapper objectMapper;
    @Mock InspectionPricingMapper inspectionPricingMapper;
    @Mock InspectionConfigurationService configurations;
    @Mock InspectionGovernanceAccessService access;
    @InjectMocks InspectionPricingServiceImpl service;

    @Test
    void approvedItemLevelFeeReplacesLegacyCategoryPricing() {
        Post post = new Post();
        var configuration = new InspectionConfigurationResponse(UUID.randomUUID(), UUID.randomUUID(),
                InspectionComplexityLevel.L2, UUID.randomUUID(), new BigDecimal("50000"), null);
        when(configurations.resolve(post, null)).thenReturn(configuration);
        assertEquals(new BigDecimal("50000"), service.resolveBaseInspectionFee(post));
        verifyNoInteractions(proposalRepository);
    }

    @Test
    void unclassifiedItemsFailInsteadOfReceivingAConfiguredDefaultFee() {
        Post post = new Post();
        when(configurations.resolve(post, null)).thenThrow(new ConflictException("Missing approved checklist"));
        assertThrows(ConflictException.class, () -> service.resolveBaseInspectionFee(post));
        verifyNoInteractions(proposalRepository);
    }

    @Test
    void staleQuoteVersionIsRejectedBeforeAnyShippingCalculation() {
        Post post = new Post();
        post.setId(UUID.randomUUID());
        when(postRepository.findById(post.getId())).thenReturn(Optional.of(post));
        UUID centerId = UUID.randomUUID();
        ShippingAddress pickup = new ShippingAddress("Seller", "0912345678", "Kho người bán",
                new BigDecimal("21.02"), new BigDecimal("105.53"));
        when(locations.sellerPickup(post)).thenReturn(pickup);
        when(locations.selectHub(pickup, null)).thenReturn(new HubSelection(centerId, "Hub", BigDecimal.ONE));
        when(configurations.resolve(post, centerId)).thenReturn(new InspectionConfigurationResponse(
                centerId, UUID.randomUUID(), InspectionComplexityLevel.L2,
                UUID.randomUUID(), new BigDecimal("50000"), null));
        var request = InspectionQuoteRequest.builder().postId(post.getId())
                .expectedFeeProposalId(UUID.randomUUID()).build();
        assertThrows(ConflictException.class, () -> service.quote(request));
        verifyNoInteractions(inspectionShipping, travelFeeCalculator);
    }
}
