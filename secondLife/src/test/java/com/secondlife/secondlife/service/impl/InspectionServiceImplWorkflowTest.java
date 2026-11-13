package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.request.InspectionAppealRequest;
import com.secondlife.secondlife.dto.request.InspectionAppointmentRequest;
import com.secondlife.secondlife.enums.InspectionAppointmentShift;
import com.secondlife.secondlife.dto.request.InspectionBuyerRespondRequest;
import com.secondlife.secondlife.dto.request.InspectionBuyRequest;
import com.secondlife.secondlife.dto.request.InspectionRejectPurchaseRequest;
import com.secondlife.secondlife.dto.request.InspectionResultRequest;
import com.secondlife.secondlife.dto.request.InspectionStartRequest;
import com.secondlife.secondlife.dto.request.InspectionSellerRespondRequest;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Negotiation;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.InspectionAppealStatus;
import com.secondlife.secondlife.enums.InspectionAppointmentStatus;
import com.secondlife.secondlife.enums.InspectionFeePayer;
import com.secondlife.secondlife.enums.InspectionOrderStatus;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.enums.InspectionScope;
import com.secondlife.secondlife.enums.InspectionSettlementOutcome;
import com.secondlife.secondlife.enums.NegotiationStatus;
import com.secondlife.secondlife.enums.NotificationType;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.ForbiddenException;
import com.secondlife.secondlife.integration.ai.AiChatGateway;
import com.secondlife.secondlife.mapper.InspectionCenterProfileMapper;
import com.secondlife.secondlife.mapper.InspectionOrderMapper;
import com.secondlife.secondlife.mapper.InspectionSealLogMapper;
import com.secondlife.secondlife.mapper.UserMapper;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.InspectionSealLogRepository;
import com.secondlife.secondlife.repository.InspectionShippingQuoteRepository;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
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
    private InspectionShippingQuoteRepository inspectionShippingQuotes;

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
                inspectionShippingQuotes,
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
                mock(InspectionCenterProfileMapper.class),
                inspectionOrderMapper,
                inspectionSealLogMapper,
                24,
                new BigDecimal("0.05"), // 5% commitment deposit
                new BigDecimal("500000"),
                mock(InspectionOrderChecklistService.class),
                "test-cloud"
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
    void assignmentExposesCustomerStatusAndNotifiesBothParties() {
        order.setInspector(null);
        order.setStatus("CONFIRMED_PENDING_DISPATCH");
        User center = new User();
        center.setId(UUID.randomUUID());
        center.addRole(new Role("INSPECTION_CENTER", "Center", null));
        order.setInspectionCenter(center);
        inspector.setManager(center);
        inspector.addRole(new Role("INSPECTOR", "Inspector", null));
        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(order)).thenReturn(order);
        when(userRepository.findById(inspector.getId())).thenReturn(Optional.of(inspector));
        when(userRepository.findById(center.getId())).thenReturn(Optional.of(center));
        when(inspectionOrderMapper.toResponse(order)).thenAnswer(invocation ->
                InspectionOrderResponse.builder().status(order.getStatus()).inspectorId(order.getInspector().getId()).build());

        InspectionOrderResponse result = service.assignInspector(order.getId(), inspector.getId(), center.getId());

        assertEquals("DISPATCHED", order.getStatus());
        assertEquals("INSPECTING", objectMapper.valueToTree(result).get("customerStatus").asText());
        verify(notificationService).notifyInApp(eq(buyer.getId()), any(), any(), eq(NotificationType.INSPECTION),
                eq(order.getId()), eq("INSPECTION_ORDER"), eq("/buyer/inspections/" + order.getId()));
        verify(notificationService).notifyInApp(eq(seller.getId()), any(), any(), eq(NotificationType.INSPECTION),
                eq(order.getId()), eq("INSPECTION_ORDER"), eq("/seller/inspections/" + order.getId()));

        order.setStatus("INSPECTING");
        service.assignInspector(order.getId(), inspector.getId(), center.getId());

        assertEquals("INSPECTING", order.getStatus());
        verify(notificationService, times(2)).notifyInApp(any(), any(), any(), eq(NotificationType.INSPECTION), any(), any(), any());
    }

    @Test
    void customerStatusKeepsUnassignedAppointmentsAndTerminalResults() {
        assertEquals("SCHEDULED", InspectionOrderResponse.builder().status("SCHEDULED").build().customerStatus());
        assertEquals("SCHEDULED", InspectionOrderResponse.builder().orderType(InspectionOrderType.AT_HOME)
                .status("SCHEDULED").inspectorId(inspector.getId()).build().customerStatus());
        assertEquals("INSPECTING", InspectionOrderResponse.builder().status("SCHEDULED").inspectorId(inspector.getId()).build().customerStatus());
        assertEquals("PASSED", InspectionOrderResponse.builder().status("PASSED").inspectorId(inspector.getId()).build().customerStatus());
    }

    @Test
    void finalizedLegacyInspectionCannotBeRestartedReassignedOrGivenANewSeal() {
        order.setStatus("FAILED");
        order.setChecklistSnapshotJson(null);
        inspector.addRole(new Role("INSPECTOR", "Inspector", null));
        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(userRepository.findById(inspector.getId())).thenReturn(Optional.of(inspector));

        assertThrows(ConflictException.class, () -> service.startInspection(inspector.getId(), order.getId(), null));
        assertThrows(ConflictException.class, () -> service.assignInspector(order.getId(), inspector.getId()));
        order.setOrderType(InspectionOrderType.AT_HOME);
        assertThrows(ConflictException.class, () -> service.generateSealForOrder(inspector.getId(), order.getId()));
        assertThrows(ConflictException.class, () -> service.scheduleAppointment(seller.getId(), order.getId(), null));

        assertEquals("FAILED", order.getStatus());
        verify(inspectionOrderRepository, never()).save(any(InspectionOrder.class));
    }

    @Test
    @DisplayName("WP-Appointment: Người bán có thể hẹn lại lịch mới khi đang có đề xuất COUNTERED_BY_CENTER")
    void testSellerCanRescheduleWhenCounteredByCenter() {
        order.setOrderType(InspectionOrderType.AT_HOME);
        order.setStatus("CONFIRMED_PENDING_DISPATCH");
        order.setAppointmentStatus(InspectionAppointmentStatus.COUNTERED_BY_CENTER);
        order.setBuyerPaidAt(Instant.now());
        User center = new User();
        center.setId(UUID.randomUUID());
        center.addRole(new Role("INSPECTION_CENTER", "Center", null));
        order.setInspectionCenter(center);

        Instant tomorrow9Am = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .plusDays(2)
                .withHour(9)
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
                .toInstant();

        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(inspectionOrderMapper.toResponse(order)).thenAnswer(inv ->
                InspectionOrderResponse.builder()
                        .appointmentStatus(order.getAppointmentStatus())
                        .appointmentScheduledAt(order.getAppointmentScheduledAt())
                        .build());

        InspectionAppointmentRequest req = new InspectionAppointmentRequest(tomorrow9Am, InspectionAppointmentShift.MORNING, "Tôi hẹn lại ca sáng");
        InspectionOrderResponse resp = service.scheduleAppointment(seller.getId(), order.getId(), req);

        assertEquals(InspectionAppointmentStatus.PENDING_CENTER_CONFIRMATION, resp.appointmentStatus());
        assertEquals(tomorrow9Am, order.getAppointmentScheduledAt());
        assertEquals(InspectionAppointmentShift.MORNING, order.getAppointmentShift());
        assertNull(order.getCounterScheduledAt());
        assertNull(order.getCounterShift());
        assertNull(order.getCounterNote());
        verify(notificationService).notifyInApp(eq(center.getId()), any(), any(), eq(NotificationType.INSPECTION), eq(order.getId()), any(), any());
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
                "unused-tem",
                "unused-barcode",
                null,
                List.of(proofPhoto())
        );

        service.submitResult(order.getId(), inspector.getId(), resultReq);

        assertEquals("FAILED", order.getStatus());
        assertEquals("REJECTED", post.getStatus());
        assertEquals(List.of(proofPhoto()), order.getEvidencePhotoUrls());
        assertNull(order.getTamperSealId());
        assertNull(order.getBarcode());
        assertNotNull(order.getAppealDeadline());
        assertEquals(InspectionAppealStatus.NONE, order.getAppealStatus());
        assertFalse(order.isReputationPenaltyApplied());
        verify(postRepository, times(1)).save(post);
    }

    @Test
    void homeCheckInRequiresFundedAppointmentAndAssignedTechnician() {
        order.setOrderType(InspectionOrderType.AT_HOME);
        order.setStatus("SCHEDULED");
        order.setAppointmentScheduledAt(Instant.now().plusSeconds(60));
        order.setAppointmentStatus(InspectionAppointmentStatus.CONFIRMED);
        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThrows(ForbiddenException.class, () -> service.getOrCreateHomeCheckInCode(buyer.getId(), order.getId()));
        assertThrows(ConflictException.class, () -> service.getOrCreateHomeCheckInCode(seller.getId(), order.getId()));

        order.setBuyerPaidAt(Instant.now());
        var code = service.getOrCreateHomeCheckInCode(seller.getId(), order.getId()).code();
        assertEquals(code, service.getOrCreateHomeCheckInCode(seller.getId(), order.getId()).code());
        assertThrows(ForbiddenException.class, () -> service.startInspection(buyer.getId(), order.getId(),
                new InspectionStartRequest(code)));
        assertThrows(BadRequestException.class, () -> service.startInspection(inspector.getId(), order.getId(),
                new InspectionStartRequest(UUID.randomUUID())));
        assertThrows(ConflictException.class, () -> service.submitResult(order.getId(), inspector.getId(),
                resultWithPhotos(null)));

        when(inspectionOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        service.startInspection(inspector.getId(), order.getId(), new InspectionStartRequest(code));
        Instant checkedInAt = order.getHomeCheckedInAt();
        service.startInspection(inspector.getId(), order.getId(), new InspectionStartRequest(code));

        assertNotNull(checkedInAt);
        assertEquals(checkedInAt, order.getHomeCheckedInAt());
        assertEquals("INSPECTING", order.getStatus());
        assertNull(order.getHomeCheckInCode());
        verify(inspectionOrderRepository).save(order);
    }

    @Test
    void centerResultRequiresUploadedPhotosAndDoesNotGenerateSeal() {
        order.setStatus("INSPECTING");
        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThrows(BadRequestException.class, () -> service.submitResult(order.getId(), inspector.getId(),
                resultWithPhotos(null)));
        assertThrows(BadRequestException.class, () -> service.submitResult(order.getId(), inspector.getId(),
                resultWithPhotos(List.of("https://example.com/photo.jpg"))));
        assertThrows(ConflictException.class, () -> service.generateSealForOrder(inspector.getId(), order.getId()));
        verifyNoInteractions(tamperSealService);
    }

    private InspectionResultRequest resultWithPhotos(List<String> photos) {
        return new InspectionResultRequest("FAILED", "Kiểm tra thực tế", null, null, null, null,
                null, null, null, null, null, photos);
    }

    private String proofPhoto() {
        return "https://res.cloudinary.com/test-cloud/image/upload/v123/secondlife/inspections/proofs/"
                + "781527ed-cfd8-4573-bb08-ad981683c50e.jpg";
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
    @DisplayName("Người bán đồng ý kiểm định -> Hạn đóng phí 12 giờ")
    void sellerAcceptSetsTwelveHourFeeDeadline() {
        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var request = new InspectionSellerRespondRequest("ACCEPT", null, null, true, null);

        Instant startedAt = Instant.now();
        service.respondInspection(seller.getId(), order.getId(), request);

        assertEquals(InspectionOrderStatus.WAITING_FEE_PAYMENT.name(), order.getStatus());
        assertFeePaymentDeadline(startedAt);
    }

    @Test
    @DisplayName("WP-3: Người mua phản hồi ACCEPT -> Chuyển WAITING_FEE_PAYMENT với hạn đóng phí 12 giờ")
    void testBuyerAcceptCounterTransitionsToWaitingFeePayment() {
        order.setStatus(InspectionOrderStatus.WAITING_BUYER_APPROVAL.name());
        order.setApprovalDeadline(Instant.now().plus(12, ChronoUnit.HOURS));

        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InspectionBuyerRespondRequest acceptReq = new InspectionBuyerRespondRequest("ACCEPT", null, true, null);

        Instant startedAt = Instant.now();
        service.respondInspectionByBuyer(buyer.getId(), order.getId(), acceptReq);

        assertEquals(InspectionOrderStatus.WAITING_FEE_PAYMENT.name(), order.getStatus());
        assertFeePaymentDeadline(startedAt);
        assertNull(order.getApprovalDeadline());
    }

    @ParameterizedTest
    @CsvSource({"BUYER_PAYS,260000,0", "SELLER_PAYS,0,230000", "SPLIT_50_50,130000,115000"})
    void cashOutboundPrepaymentExcludesOnlyTheSellersOutboundShare(InspectionFeePayer payer,
            BigDecimal buyerDue, BigDecimal sellerDue) {
        order.setCashShippingSimulation(true);
        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.respondInspection(seller.getId(), order.getId(),
                new InspectionSellerRespondRequest("COUNTER", null, payer, false, null));

        assertEquals(0, buyerDue.compareTo(order.getBuyerFeeShare()));
        assertEquals(0, sellerDue.compareTo(order.getSellerFeeShare()));
    }

    private void assertFeePaymentDeadline(Instant startedAt) {
        Instant deadline = order.getPaymentDeadline();
        assertNotNull(deadline);
        assertFalse(deadline.isBefore(startedAt.plus(12, ChronoUnit.HOURS)));
        assertFalse(deadline.isAfter(Instant.now().plus(12, ChronoUnit.HOURS)));
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

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @DisplayName("WP-4: Yêu cầu kiểm định có kèm negotiationId -> Khóa giá finalItemPrice theo thương lượng")
    void testRequestInspectionWithNegotiationLocksAgreedPrice(boolean cashShipping) {
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
                .cashShippingSimulationEnabled(cashShipping)
                .build();
        when(inspectionPricingService.calculateQuote(any())).thenReturn(mockQuote);
        when(inspectionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InspectionBuyRequest req = new InspectionBuyRequest(
                post.getId(),
                InspectionOrderType.AT_CENTER,
                InspectionFeePayer.SPLIT_50_50,
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
                saved.getBuyerCommitmentDeposit().compareTo(new BigDecimal("450000.00")) == 0 &&
                saved.isCashShippingSimulation() == cashShipping &&
                saved.getBuyerFeeShare().compareTo(new BigDecimal("130000")) == 0 &&
                saved.getSellerFeeShare().compareTo(BigDecimal.valueOf(cashShipping ? 115000 : 130000)) == 0
        ));
    }

    @Test
    @DisplayName("WP-7: Người mua từ chối mua tiếp -> settle với PASSED_BUYER_DEFAULTED và hủy đơn")
    void testRejectFinalPurchaseSettlesDefaultAndCancels() {
        order.setStatus(InspectionOrderStatus.PASSED.name());
        when(inspectionOrderRepository.findPostId(order.getId())).thenReturn(Optional.of(post.getId()));
        when(postRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));

        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(inspectionOrderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InspectionRejectPurchaseRequest rejectReq = new InspectionRejectPurchaseRequest("Tôi đổi ý không mua nữa");

        service.rejectFinalPurchase(buyer.getId(), order.getId(), rejectReq);

        assertEquals(InspectionOrderStatus.CANCELLED.name(), order.getStatus());
        verify(feeSettlementService, times(1)).settle(eq(order), eq(InspectionSettlementOutcome.PASSED_BUYER_DEFAULTED), contains("Tôi đổi ý"));
    }

    @Test
    void expiredFinalPaymentSettlesOnlyOnce() {
        order.setStatus(InspectionOrderStatus.PASSED.name());
        order.setFinalPaymentDeadline(Instant.now().minusSeconds(30));
        post.setStatus("RESERVED");
        when(inspectionOrderRepository.findPostId(order.getId())).thenReturn(Optional.of(post.getId()));
        when(postRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.processExpiredFinalPayment(order.getId());
        service.processExpiredFinalPayment(order.getId());

        assertEquals(InspectionOrderStatus.CANCELLED.name(), order.getStatus());
        assertEquals("ACTIVE", post.getStatus());
        verify(feeSettlementService).settle(eq(order), eq(InspectionSettlementOutcome.PASSED_BUYER_DEFAULTED), any());
        verify(inspectionOrderRepository).save(order);
    }

    @Test
    void expiredFinalPaymentSkipsAnOrderAlreadyPaid() {
        order.setStatus(InspectionOrderStatus.PASSED.name());
        order.setFinalPaymentDeadline(Instant.now().minusSeconds(30));
        order.setFinalPaymentPaidAt(Instant.now());
        when(inspectionOrderRepository.findPostId(order.getId())).thenReturn(Optional.of(post.getId()));
        when(postRepository.findByIdForUpdate(post.getId())).thenReturn(Optional.of(post));
        when(inspectionOrderRepository.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.processExpiredFinalPayment(order.getId());

        verifyNoInteractions(feeSettlementService);
        verify(inspectionOrderRepository, never()).save(any());
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
