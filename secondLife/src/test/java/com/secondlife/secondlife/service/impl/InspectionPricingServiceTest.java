package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.entity.Category;
import com.secondlife.secondlife.entity.InspectionFeeProposal;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import com.secondlife.secondlife.repository.CategoryRepository;
import com.secondlife.secondlife.repository.InspectionFeeProposalRepository;
import com.secondlife.secondlife.repository.InspectionTravelTierRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionTravelFeeCalculator;
import com.secondlife.secondlife.service.shipping.ShippingQuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionPricingServiceTest {

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

    @InjectMocks
    private InspectionPricingServiceImpl pricingService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(pricingService, "defaultBaseInspectionFee", new BigDecimal("100000"));
        ReflectionTestUtils.setField(pricingService, "defaultShippingFee", new BigDecimal("50000"));
    }

    @Test
    @DisplayName("resolveBaseInspectionFee should return proposal base fee when approved proposal exists for category")
    void testResolveBaseInspectionFee_WithApprovedProposal() {
        UUID categoryId = UUID.randomUUID();
        Category category = new Category();
        category.setId(categoryId);

        Post post = new Post();
        post.setCategoryId(categoryId);

        InspectionFeeProposal proposal = new InspectionFeeProposal();
        proposal.setCategory(category);
        proposal.setBaseInspectionFee(new BigDecimal("180000"));
        proposal.setStatus(InspectionFeeProposalStatus.APPROVED);

        when(proposalRepository.findFirstByCategory_IdAndStatusOrderByReviewedAtDesc(categoryId, InspectionFeeProposalStatus.APPROVED))
                .thenReturn(Optional.of(proposal));

        BigDecimal resolvedFee = pricingService.resolveBaseInspectionFee(post);

        assertEquals(new BigDecimal("180000"), resolvedFee);
    }

    @Test
    @DisplayName("resolveBaseInspectionFee should fallback to defaultBaseInspectionFee when no proposal exists")
    void testResolveBaseInspectionFee_FallbackToDefault() {
        UUID categoryId = UUID.randomUUID();
        Category category = new Category();
        category.setId(categoryId);

        Post post = new Post();
        post.setCategoryId(categoryId);

        when(proposalRepository.findFirstByCategory_IdAndStatusOrderByReviewedAtDesc(categoryId, InspectionFeeProposalStatus.APPROVED))
                .thenReturn(Optional.empty());

        BigDecimal resolvedFee = pricingService.resolveBaseInspectionFee(post);

        assertEquals(new BigDecimal("100000"), resolvedFee);
    }

    @Test
    @DisplayName("resolveBaseInspectionFee should return default when post or category is null")
    void testResolveBaseInspectionFee_NullPostOrCategory() {
        assertEquals(new BigDecimal("100000"), pricingService.resolveBaseInspectionFee(null));

        Post postWithoutCategory = new Post();
        assertEquals(new BigDecimal("100000"), pricingService.resolveBaseInspectionFee(postWithoutCategory));
    }
}
