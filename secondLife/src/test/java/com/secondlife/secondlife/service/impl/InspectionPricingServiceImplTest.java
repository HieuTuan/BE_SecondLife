package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.InspectionFeeProposal;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import com.secondlife.secondlife.mapper.InspectionPricingMapper;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionFeeProposalRepository;
import com.secondlife.secondlife.repository.InspectionTravelTierRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;

import com.secondlife.secondlife.service.ShippingQuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionPricingServiceImplTest {

    @Mock
    private PostRepository postRepository;
    @Mock
    private InspectionTravelFeeCalculator travelFeeCalculator;
    @Mock
    private InspectionFeeProposalRepository proposalRepository;
    @Mock
    private InspectionTravelTierRepository travelTierRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ShippingQuoteService shippingQuoteService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private InspectionPricingMapper inspectionPricingMapper;

    @InjectMocks
    private InspectionPricingServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "defaultBaseInspectionFee", BigDecimal.valueOf(100000));
        ReflectionTestUtils.setField(service, "defaultShippingFee", BigDecimal.valueOf(50000));
    }

    @Test
    void resolveBaseInspectionFee_WhenApprovedProposalExists_ReturnsProposalBaseFee() {
        UUID categoryId = UUID.randomUUID();
        Post post = new Post();
        post.setCategoryId(categoryId);

        InspectionFeeProposal proposal = new InspectionFeeProposal();
        proposal.setBaseInspectionFee(BigDecimal.valueOf(150000));

        when(proposalRepository.findFirstByCategory_IdAndStatusOrderByReviewedAtDesc(categoryId, InspectionFeeProposalStatus.APPROVED))
                .thenReturn(Optional.of(proposal));

        BigDecimal fee = service.resolveBaseInspectionFee(post);

        assertEquals(BigDecimal.valueOf(150000), fee);
        verify(proposalRepository).findFirstByCategory_IdAndStatusOrderByReviewedAtDesc(categoryId, InspectionFeeProposalStatus.APPROVED);
    }

    @Test
    void resolveBaseInspectionFee_WhenNoProposalExists_ReturnsDefaultFee() {
        UUID categoryId = UUID.randomUUID();
        Post post = new Post();
        post.setCategoryId(categoryId);

        when(proposalRepository.findFirstByCategory_IdAndStatusOrderByReviewedAtDesc(categoryId, InspectionFeeProposalStatus.APPROVED))
                .thenReturn(Optional.empty());

        BigDecimal fee = service.resolveBaseInspectionFee(post);

        assertEquals(BigDecimal.valueOf(100000), fee);
    }

    @Test
    void resolveBaseInspectionFee_WhenPostOrCategoryNull_ReturnsDefaultFee() {
        BigDecimal feeNullPost = service.resolveBaseInspectionFee(null);
        assertEquals(BigDecimal.valueOf(100000), feeNullPost);

        Post postNoCategory = new Post();
        BigDecimal feeNoCategory = service.resolveBaseInspectionFee(postNoCategory);
        assertEquals(BigDecimal.valueOf(100000), feeNoCategory);

        verifyNoInteractions(proposalRepository);
    }
}
