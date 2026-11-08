package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.InspectionQuoteRequest;
import com.secondlife.secondlife.dto.response.InspectionConfigurationResponse;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.InspectionComplexityLevel;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.mapper.InspectionPricingMapper;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionFeeProposalRepository;
import com.secondlife.secondlife.repository.InspectionTravelTierRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionConfigurationService;
import com.secondlife.secondlife.service.InspectionGovernanceAccessService;
import com.secondlife.secondlife.service.ShippingQuoteService;

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionPricingServiceImplTest {
    @Mock PostRepository postRepository;
    @Mock InspectionTravelFeeCalculator travelFeeCalculator;
    @Mock InspectionFeeProposalRepository proposalRepository;
    @Mock InspectionTravelTierRepository travelTierRepository;
    @Mock UserRepository userRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock ShippingQuoteService shippingQuoteService;
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
        when(configurations.resolve(any(), isNull())).thenReturn(new InspectionConfigurationResponse(
                UUID.randomUUID(), UUID.randomUUID(), InspectionComplexityLevel.L2,
                UUID.randomUUID(), new BigDecimal("50000"), null));
        var request = InspectionQuoteRequest.builder().postId(post.getId())
                .expectedFeeProposalId(UUID.randomUUID()).build();
        assertThrows(ConflictException.class, () -> service.quote(request));
        verifyNoInteractions(shippingQuoteService, travelFeeCalculator);
    }
}
