package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.InspectionAppealRequest;
import com.secondlife.secondlife.dto.request.InspectionBuyerRespondRequest;
import com.secondlife.secondlife.dto.request.InspectionBuyRequest;
import com.secondlife.secondlife.dto.request.InspectionRejectPurchaseRequest;
import com.secondlife.secondlife.dto.request.InspectionResultRequest;
import com.secondlife.secondlife.dto.request.InspectionSellerRespondRequest;
import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Negotiation;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.InspectionAppealStatus;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderStatus;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.enums.InspectionScope;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;
import com.secondlife.secondlife.enums.NegotiationStatus;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.integration.ai.AiChatGateway;
import com.secondlife.secondlife.mapper.InspectionOrderMapper;
import com.secondlife.secondlife.mapper.InspectionSealLogMapper;
import com.secondlife.secondlife.mapper.UserMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.InspectionSealLogRepository;
import com.secondlife.secondlife.repository.NegotiationRepository;
import com.secondlife.secondlife.repository.OrderRepository;
import com.secondlife.secondlife.repository.PostRepository;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.ShipmentRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.CommissionService;
import com.secondlife.secondlife.service.InspectionFeeSettlementService;
import com.secondlife.secondlife.service.InspectionOrderChecklistService;
import com.secondlife.secondlife.service.InspectionPricingService;
import com.secondlife.secondlife.service.ListingPublicationService;
import com.secondlife.secondlife.service.NotificationService;
import com.secondlife.secondlife.service.PaidOrderService;
import com.secondlife.secondlife.service.SellerReputationService;
import com.secondlife.secondlife.service.ShippingQuoteService;
import com.secondlife.secondlife.service.TamperSealService;
import com.secondlife.secondlife.service.WalletService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectionServiceImplWorkflowTest {

    @Mock
    private InspectionOrderRepository inspectionOrderRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private NegotiationRepository negotiationRepository;

    @Mock
    private ShipmentRepository shipments;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ListingPublicationService publication;

    @Mock
    private InspectionPricingService inspectionPricingService;

    @Mock
    private WalletService walletService;

    @Mock
    private CommissionService commissions;

    @Mock
    private ShippingQuoteService shippingQuoteService;

    @Mock
    private PaidOrderService paidOrderService;

    @Mock
    private InspectionFeeSettlementService feeSettlementService;

    @Mock
    private SellerReputationService sellerReputationService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private InspectionSealLogRepository inspectionSealLogRepository;

    @Mock
    private TamperSealService tamperSealService;

    @Mock
    private AiChatGateway googleChatGateway;

    @Mock
    private InspectionOrderMapper inspectionOrderMapper;

    @Mock
    private InspectionSealLogMapper inspectionSealLogMapper;

    private ObjectMapper objectMapper = new ObjectMapper();

    private InspectionServiceImpl service;

    private User buyer;
    private User seller;
    private User inspector;
    private Post post;
    private InspectionOrder order;

    @BeforeEach
    void setUp() {
        service = new InspectionServiceImpl(
                inspectionOrderRepository,
                postRepository,
                orderRepository,
                negotiationRepository,
                shipments,
                userRepository,
                roleRepository,
                passwordEncoder,
                userMapper,
                publication,
                inspectionPricingService,
                walletService,
                commissions,
                shippingQuoteService,
                paidOrderService,
                feeSettlementService,
                sellerReputationService,
                objectMapper,
                notificationService,
                inspectionSealLogRepository,
                tamperSealService,
                googleChatGateway,
                inspectionOrderMapper,
                inspectionSealLogMapper,
                24,
                new BigDecimal("0.05"), // 5% commitment deposit
                new BigDecimal("500000"),
                mock(InspectionOrderChecklistService.class)
        );

        buyer = new User();
        buyer.setId(UUID.randomUUID());
        buyer.setEmail("buyer@test.com");

        seller = new User();
        seller.setId(UUID.randomUUID());
        seller.setEmail("seller@test.com");

        inspector = new User();
        inspector.setId(UUID.randomUUID());
        inspector.setEmail("inspector@test.com");

        post = new Post();
        post.setId(UUID.randomUUID());
        post.setTitle("iPhone 13 Pro Max");
        post.setPrice(new BigDecimal("10000000"));
        post.setStatus("ACTIVE");
        post.setUser(seller);

        order = new InspectionOrder();
        order.setId(UUID.randomUUID());
        order.setPost(post);
        order.setBuyer(buyer);
        order.setSeller(seller);
        order.setInspector(inspector);
        order.setStatus("WAITING_SELLER_APPROVAL");
        order.setOrderType(InspectionOrderType.AT_CENTER);
        order.setFeePayer(InspectionFeePayer.BUYER_PAYS);
        order.setInspectionScope(InspectionScope.BUYER_REQUEST);
        order.setInspectionFee(new BigDecimal("200000"));
        order.setShippingFee(new BigDecimal("30000"));
        order.setReturnShippingReserveFee(new BigDecimal("30000"));
        order.setBuyerFeeShare(new BigDecimal("260000"));
        order.setSellerFeeShare(BigDecimal.ZERO);
        order.setFinalItemPrice(new BigDecimal("10000000"));
    }

    @Test
    @DisplayName("WP-1: Không thể tạo đơn mới nếu bài đăng đang có đơn kiểm định ACTIVE")
    void testRequestInspectionThrowsConflictWhenActiveOrderExists() {
        when(postRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(inspectionOrderRepository.findActiveByPostId(post.getId())).thenReturn(Optional.of(new InspectionOrder()));

        InspectionBuyRequest req = new InspectionBuyRequest(
                post.getId(),
                InspectionOrderType.AT_CENTER,
                InspectionFeePayer.BUYER_PAYS,
                true,
                null,
                null,
                null,
                null,
                null, null, null, null, UUID.randomUUID()
        );

        assertThrows(ConflictException.class, () -> service.requestInspection(buyer.getId(), req));
    }

    @Test
    @DisplayName("WP-2: Pre-listing inspection FAILED -> Bài đăng chuyển REJECTED và thiết lập hạn khiếu nại 24h")
    void testPreListingInspectionFailedRejectsPostAndSetsAppealWindow() {
        order.setInspectionScope(InspectionScope.PRE_LISTING);
        order.setStatus("INSPECTING");

        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(postRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(inspectionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InspectionResultRequest resultReq = new InspectionResultRequest(
                "FAILED",
                "Phát hiện máy đã qua sửa chữa nặng, linh kiện giả",
                null,
                null,
                null,
                null,
                70,
                "POOR",
                null,
                null,
                null
        );

        service.submitResult(order.getId(), inspector.getId(), resultReq);

        assertEquals("FAILED", order.getStatus());
        assertEquals("REJECTED", post.getStatus());
        assertNotNull(order.getAppealDeadline());
        assertEquals(InspectionAppealStatus.NONE, order.getAppealStatus());
        assertFalse(order.isReputationPenaltyApplied());
        verify(postRepository, times(1)).save(post);
    }

    @Test
    @DisplayName("WP-3: Người bán phản hồi COUNTER -> Đổi trạng thái sang WAITING_BUYER_APPROVAL và cập nhật thời hạn")
    void testSellerCounterProposalTransitionsToWaitingBuyerApproval() {
        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InspectionSellerRespondRequest counterReq = new InspectionSellerRespondRequest(
                "COUNTER",
                null,
                InspectionFeePayer.SPLIT_50_50,
                false,
                null
        );

        service.respondInspection(seller.getId(), order.getId(), counterReq);

        assertEquals(InspectionOrderStatus.WAITING_BUYER_APPROVAL.name(), order.getStatus());
        assertEquals(InspectionFeePayer.SPLIT_50_50, order.getFeePayer());
        assertNotNull(order.getApprovalDeadline());
    }

    @Test
    @DisplayName("WP-3: Người mua phản hồi ACCEPT -> Chuyển WAITING_FEE_PAYMENT với hạn thanh toán 6 giờ")
    void testBuyerAcceptCounterTransitionsToWaitingFeePayment() {
        order.setStatus(InspectionOrderStatus.WAITING_BUYER_APPROVAL.name());
        order.setApprovalDeadline(Instant.now().plus(12, ChronoUnit.HOURS));

        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InspectionBuyerRespondRequest acceptReq = new InspectionBuyerRespondRequest("ACCEPT", true, null);

        service.respondInspectionByBuyer(buyer.getId(), order.getId(), acceptReq);

        assertEquals(InspectionOrderStatus.WAITING_FEE_PAYMENT.name(), order.getStatus());
        assertNotNull(order.getPaymentDeadline());
        assertNull(order.getApprovalDeadline());
    }

    @Test
    @DisplayName("WP-3: Scheduler quét các đơn hết hạn duyệt 24h -> Tự động hủy và khôi phục trạng thái post")
    void testProcessExpiredApprovalsCancelsOrderAndRestoresPost() {
        order.setStatus(InspectionOrderStatus.WAITING_SELLER_APPROVAL.name());
        order.setApprovalDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
        post.setStatus("RESERVED");

        when(inspectionOrderRepository.findExpiredApprovalOrders(any())).thenReturn(List.of(order));
        when(inspectionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processExpiredApprovals();

        assertEquals(InspectionOrderStatus.CANCELLED.name(), order.getStatus());
        assertEquals("ACTIVE", post.getStatus());
        verify(postRepository, times(1)).save(post);
    }

    @Test
    @DisplayName("WP-4: Yêu cầu kiểm định có kèm negotiationId -> Khóa giá finalItemPrice theo thương lượng")
    void testRequestInspectionWithNegotiationLocksAgreedPrice() {
        UUID negotiationId = UUID.randomUUID();
        Negotiation negotiation = new Negotiation();
        negotiation.setId(negotiationId);
        negotiation.setBuyer(buyer);
        negotiation.setPost(post);
        negotiation.setStatus(NegotiationStatus.ACCEPTED);
        negotiation.setOfferedPrice(new BigDecimal("9000000")); // Giảm từ 10tr xuống 9tr
        negotiation.setExpiredAt(Instant.now().plus(24, ChronoUnit.HOURS));

        when(postRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(inspectionOrderRepository.findActiveByPostId(post.getId())).thenReturn(Optional.empty());
        when(userRepository.findById(buyer.getId())).thenReturn(Optional.of(buyer));
        when(negotiationRepository.findById(negotiationId)).thenReturn(Optional.of(negotiation));

        InspectionQuoteResponse mockQuote = InspectionQuoteResponse.builder()
                .baseInspectionFee(new BigDecimal("200000"))
                .shippingToCenterFee(new BigDecimal("30000"))
                .returnShippingReserveFee(new BigDecimal("30000"))
                .atCenterTotalFee(new BigDecimal("260000"))
                .build();
        when(inspectionPricingService.calculateQuote(any())).thenReturn(mockQuote);
        when(inspectionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InspectionBuyRequest req = new InspectionBuyRequest(
                post.getId(),
                InspectionOrderType.AT_CENTER,
                InspectionFeePayer.BUYER_PAYS,
                true,
                null,
                null,
                null,
                null,
                negotiationId, null, null, null, UUID.randomUUID()
        );

        service.requestInspection(buyer.getId(), req);

        verify(inspectionOrderRepository).save(argThat(saved ->
                saved.getFinalItemPrice().compareTo(new BigDecimal("9000000")) == 0 &&
                saved.getBuyerCommitmentDeposit().compareTo(new BigDecimal("450000.00")) == 0
        ));
    }

    @Test
    @DisplayName("WP-7: Người mua từ chối mua tiếp -> settle với PASSED_BUYER_DEFAULTED và hủy đơn")
    void testRejectFinalPurchaseSettlesDefaultAndCancels() {
        order.setStatus(InspectionOrderStatus.PASSED.name());

        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InspectionRejectPurchaseRequest rejectReq = new InspectionRejectPurchaseRequest("Tôi đổi ý không mua nữa");

        service.rejectFinalPurchase(buyer.getId(), order.getId(), rejectReq);

        assertEquals(InspectionOrderStatus.CANCELLED.name(), order.getStatus());
        verify(feeSettlementService, times(1)).settle(eq(order), eq(InspectionSettlementOutcome.PASSED_BUYER_DEFAULTED), contains("Tôi đổi ý"));
    }

    @Test
    @DisplayName("Appeal: Người bán gửi khiếu nại trong 24h -> Chuyển PENDING_REVIEW")
    void testSellerSubmitAppealWithin24h() {
        order.setStatus("FAILED");
        order.setAppealDeadline(Instant.now().plus(20, ChronoUnit.HOURS));
        order.setAppealStatus(InspectionAppealStatus.NONE);

        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InspectionAppealRequest appealReq = new InspectionAppealRequest(
                "KTV đánh giá nhầm mã màn hình, máy nguyên zin",
                List.of("https://res.cloudinary.com/proof1.jpg"),
                null,
                "Có video đóng gói đối chứng"
        );

        service.submitAppeal(seller.getId(), order.getId(), appealReq);

        assertEquals(InspectionAppealStatus.PENDING_REVIEW, order.getAppealStatus());
        assertEquals("KTV đánh giá nhầm mã màn hình, máy nguyên zin", order.getAppealReason());
    }

    @Test
    @DisplayName("Appeal: Quá hạn 24h không khiếu nại -> Scheduler trừ điểm uy tín người bán")
    void testProcessExpiredAppealsAppliesReputationPenalty() {
        order.setStatus("FAILED");
        order.setAppealDeadline(Instant.now().minus(1, ChronoUnit.HOURS));
        order.setAppealStatus(InspectionAppealStatus.NONE);
        order.setReputationPenaltyApplied(false);
        order.setConditionScore(30); // score < 40 -> serious mismatch

        when(inspectionOrderRepository.findExpiredAppeals(any())).thenReturn(List.of(order));
        when(inspectionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.processExpiredAppeals();

        assertTrue(order.isReputationPenaltyApplied());
        assertEquals(new BigDecimal("40.00"), order.getReputationPenaltyPoints());
        verify(sellerReputationService, times(1)).recordInspectionFailurePenalty(
                eq(seller.getId()),
                eq(order.getId()),
                eq(new BigDecimal("40.00")),
                any(),
                eq(true)
        );
    }

    @Test
    @DisplayName("Appeal: Người bán tự nguyện bấm chấp nhận kết quả FAILED sớm -> trừ điểm uy tín ngay")
    void testSellerVoluntarilyAcceptsFailedResult() {
        order.setStatus("FAILED");
        order.setAppealDeadline(Instant.now().plus(12, ChronoUnit.HOURS));
        order.setAppealStatus(InspectionAppealStatus.NONE);
        order.setReputationPenaltyApplied(false);

        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.acceptFailedResult(seller.getId(), order.getId());

        assertTrue(order.isReputationPenaltyApplied());
        assertNull(order.getAppealDeadline());
        verify(sellerReputationService, times(1)).recordInspectionFailurePenalty(
                eq(seller.getId()),
                eq(order.getId()),
                any(),
                any(),
                anyBoolean()
        );
    }
}
