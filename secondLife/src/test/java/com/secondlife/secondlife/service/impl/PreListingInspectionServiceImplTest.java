package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.*;
import com.secondlife.secondlife.dto.response.*;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.*;
import com.secondlife.secondlife.mapper.InspectionOrderMapper;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreListingInspectionServiceImplTest {
    @Mock PostRepository posts;
    @Mock InspectionOrderRepository orders;
    @Mock PreListingInspectionQuoteRepository quotes;
    @Mock InspectionPricingService pricing;
    @Mock InspectionLocationService locations;
    @Mock ListingAccessService access;
    @Mock InspectionOrderMapper mapper;
    @InjectMocks PreListingInspectionServiceImpl service;
    Post post;
    User seller;
    InspectionOrder order;
    UUID centerId;
    PreListingInspectionQuote savedQuote;

    @BeforeEach
    void setup() {
        service = new PreListingInspectionServiceImpl(posts, orders, quotes, pricing, locations, access, mapper, new ObjectMapper());
        seller = new User();
        seller.setId(UUID.randomUUID());
        centerId = UUID.randomUUID();
        post = new Post();
        post.setId(UUID.randomUUID());
        post.setUser(seller);
        post.setPrice(new BigDecimal("10000001"));
        post.setStatus("PENDING_INSPECTION");
        order = new InspectionOrder();
        order.setId(UUID.randomUUID());
        order.setPost(post);
        order.setSeller(seller);
        order.setInspectionScope(InspectionScope.PRE_LISTING);
        order.setStatus("PENDING");
        order.setOrderType(null);
        lenient().when(posts.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        lenient().when(orders.findActiveByPostId(post.getId())).thenReturn(Optional.of(order));
        lenient().when(orders.findActivePreListingIdByPostId(post.getId())).thenReturn(Optional.of(order.getId()));
        lenient().when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        lenient().when(orders.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(quotes.saveAndFlush(any())).thenAnswer(invocation -> {
            savedQuote = invocation.getArgument(0);
            savedQuote.setId(UUID.randomUUID());
            when(quotes.findById(savedQuote.getId())).thenReturn(Optional.of(savedQuote));
            return savedQuote;
        });
        lenient().when(locations.sellerPickup(post)).thenReturn(new ShippingAddress("Seller","0901234567","12 Main",
                new BigDecimal("10.75"), new BigDecimal("106.65")));
        lenient().when(pricing.quote(any())).thenAnswer(invocation -> quotation(
                ((InspectionQuoteRequest) invocation.getArgument(0)).orderType()));
        lenient().doAnswer(invocation -> {
            var target = (InspectionOrder) invocation.getArgument(0);
            User center = new User();
            center.setId(centerId);
            target.setInspectionCenter(center);
            assertNull(target.getChecklistSnapshotJson());
            target.setChecklistSnapshotJson("{}");
            return null;
        }).when(pricing).applyConfiguration(any(), any());
    }

    @Test
    void passedInspectionAwaitingReturnCannotStartAnotherInspection() {
        when(orders.existsByPostIdAndInspectionScopeAndStatusIn(post.getId(), InspectionScope.PRE_LISTING,
                List.of("PASSED", "COMPLETED"))).thenReturn(true);

        assertThrows(ConflictException.class, () -> preview(InspectionOrderType.AT_CENTER));
        assertThrows(ConflictException.class, () -> service.confirm(seller.getId(), post.getId(),
                new PreListingInspectionConfirmRequest(UUID.randomUUID(), true)));
        verifyNoInteractions(pricing);
    }

    private InspectionQuoteResponse quotation(InspectionOrderType type) {
        var builder = InspectionQuoteResponse.builder().postId(post.getId()).postPrice(post.getPrice())
                .inspectionCenterId(centerId).baseInspectionFee(new BigDecimal("100000"))
                .travelDistanceKm(BigDecimal.TEN).travelFeeRoundTrip(new BigDecimal("20000"))
                .atHomeTotalFee(new BigDecimal("120000"))
                .inspectionConfiguration(new InspectionConfigurationResponse(centerId, UUID.nameUUIDFromBytes(new byte[]{1}),
                        InspectionComplexityLevel.L1, UUID.nameUUIDFromBytes(new byte[]{2}), new BigDecimal("100000"), null));
        if (type == InspectionOrderType.AT_CENTER) {
            builder.shippingToCenterFee(new BigDecimal("30000")).returnShippingReserveFee(new BigDecimal("30000"))
                    .atCenterTotalFee(new BigDecimal("160000"))
                    .shippingQuote(new InspectionShippingQuoteResponse(UUID.randomUUID(), centerId,
                            Instant.now().plusSeconds(300), null, null));
        }
        return builder.build();
    }

    private PreListingInspectionQuoteResponse preview(InspectionOrderType type) {
        return service.quote(seller.getId(), post.getId(), new PreListingInspectionQuoteRequest(type, null));
    }

    @ParameterizedTest
    @EnumSource(InspectionOrderType.class)
    void sellerChoosesEitherMethodAndFundsTheEntireConfirmedQuote(InspectionOrderType type) {
        var preview = preview(type);
        service.confirm(seller.getId(), post.getId(), new PreListingInspectionConfirmRequest(preview.quoteId(), true));

        BigDecimal expected = new BigDecimal(type == InspectionOrderType.AT_HOME ? "120000" : "160000");
        assertEquals("WAITING_FEE_PAYMENT", order.getStatus());
        assertEquals(type, order.getOrderType());
        assertEquals(InspectionScope.PRE_LISTING, order.getInspectionScope());
        assertEquals(InspectionFeePayer.SELLER_PAYS, order.getFeePayer());
        assertEquals(0, order.getBuyerFeeShare().signum());
        assertEquals(0, order.getBuyerCommitmentDeposit().signum());
        assertNull(order.getBuyer());
        assertEquals(expected, order.getSellerFeeShare());
        assertEquals(preview.quoteId(), order.getPreListingQuoteId());
        assertTrue(order.isSellerConsentZin());
        assertNotNull(order.getPaymentDeadline());

        var wallet = mock(WalletService.class);
        var systemWallet = mock(SystemWalletService.class);
        var escrow = new InspectionEscrowServiceImpl(orders, wallet, systemWallet, posts, mapper,
                mock(NotificationService.class), mock(UserRepository.class), mock(InspectionFeeSettlementService.class));
        assertThrows(ConflictException.class, () -> escrow.payInspectionFee(seller.getId(), order.getId()));
        assertThrows(ConflictException.class, () ->
                escrow.payInspectionFeeResponse(seller.getId(), order.getId(), UUID.randomUUID()));
        verifyNoInteractions(wallet, systemWallet);
        when(orders.save(order)).thenReturn(order);
        escrow.payInspectionFeeResponse(seller.getId(), order.getId(), preview.quoteId());

        assertEquals("CONFIRMED_PENDING_DISPATCH", order.getStatus());
        assertNotNull(order.getSellerPaidAt());
        verify(wallet).processPayment(seller.getId(), expected, order.getId(), WalletTransactionReferenceType.INSPECTION_ORDER);
        verify(systemWallet).holdInspectionFee(order, false, expected, BigDecimal.ZERO);
        assertThrows(ConflictException.class, () -> preview(type));
    }

    @Test
    void preListingQuoteUsesAndConfirmsTheSystemSelectedHub() {
        UUID requestedHub = UUID.randomUUID();
        var response = service.quote(seller.getId(), post.getId(),
                new PreListingInspectionQuoteRequest(InspectionOrderType.AT_CENTER, requestedHub));
        assertEquals(centerId, response.quote().inspectionCenterId());
        verify(pricing).quote(argThat(request -> request.inspectionCenterId() == null));

        service.confirm(seller.getId(), post.getId(), new PreListingInspectionConfirmRequest(response.quoteId(), true));
        verify(pricing).quote(argThat(request -> centerId.equals(request.inspectionCenterId())
                && response.quote().shippingQuote().id().equals(request.inspectionShippingQuoteId())));
        assertEquals(centerId, order.getInspectionCenter().getId());
    }

    @Test
    void expiredQuoteCannotStartPaymentOrChangeThePendingInspection() {
        var preview = preview(InspectionOrderType.AT_HOME);
        savedQuote.setExpiresAt(Instant.now().minusSeconds(1));
        assertThrows(ConflictException.class, () -> service.confirm(seller.getId(), post.getId(),
                new PreListingInspectionConfirmRequest(preview.quoteId(), true)));
        assertEquals("PENDING", order.getStatus());
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void aPassedCenterInspectionWaitingForReturnCannotBeRequoted() {
        post.setListingCreditCharged(true);
        assertThrows(ConflictException.class, () -> preview(InspectionOrderType.AT_CENTER));
        verifyNoInteractions(pricing);
    }

    @Test
    void sellerCannotQuoteAnotherSellersProduct() {
        assertThrows(ForbiddenException.class, () -> service.quote(UUID.randomUUID(), post.getId(),
                new PreListingInspectionQuoteRequest(InspectionOrderType.AT_HOME, centerId)));
        verifyNoInteractions(pricing);
    }

    @Test
    void changedProductPriceOrPickupRequiresANewQuote() {
        var preview = preview(InspectionOrderType.AT_HOME);
        post.setPrice(new BigDecimal("12000000"));
        assertThrows(ConflictException.class, () -> service.confirm(seller.getId(), post.getId(),
                new PreListingInspectionConfirmRequest(preview.quoteId(), true)));
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void changedTravelPriceRequiresSellerToApproveANewQuote() {
        var preview = preview(InspectionOrderType.AT_HOME);
        doReturn(InspectionQuoteResponse.builder().atHomeTotalFee(new BigDecimal("130000")).build()).when(pricing).quote(any());
        assertThrows(ConflictException.class, () -> service.confirm(seller.getId(), post.getId(),
                new PreListingInspectionConfirmRequest(preview.quoteId(), true)));
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void retryOfConfirmedQuotePreservesThePaymentDeadline() {
        var preview = preview(InspectionOrderType.AT_HOME);
        var request = new PreListingInspectionConfirmRequest(preview.quoteId(), true);
        service.confirm(seller.getId(), post.getId(), request);
        Instant deadline = order.getPaymentDeadline();
        when(orders.findByPreListingQuoteId(preview.quoteId())).thenReturn(Optional.of(order));

        service.confirm(seller.getId(), post.getId(), request);
        assertEquals(deadline, order.getPaymentDeadline());
        verify(orders).saveAndFlush(order);
    }

    @Test
    void sellerCanSwitchMethodsBeforePaying() {
        var home = preview(InspectionOrderType.AT_HOME);
        service.confirm(seller.getId(), post.getId(), new PreListingInspectionConfirmRequest(home.quoteId(), true));
        var center = preview(InspectionOrderType.AT_CENTER);
        service.confirm(seller.getId(), post.getId(), new PreListingInspectionConfirmRequest(center.quoteId(), true));
        assertEquals(InspectionOrderType.AT_CENTER, order.getOrderType());
        assertEquals(new BigDecimal("160000"), order.getSellerFeeShare());
        assertEquals(BigDecimal.ZERO, order.getTravelFee());
    }
}
