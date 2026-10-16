package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.response.InspectorPayoutResponse;
import com.secondlife.secondlife.dto.response.InspectorStatsResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.InspectionOrderStatus;
import com.secondlife.secondlife.enums.InspectionOrderType;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.repository.InspectionOrderRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.impl.InspectionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InspectorStatsTest {

    @Mock
    private InspectionOrderRepository inspectionOrderRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private InspectionServiceImpl inspectionService;

    private UUID inspectorId;
    private User inspectorUser;

    @BeforeEach
    void setUp() {
        inspectorId = UUID.randomUUID();
        inspectorUser = new User();
        inspectorUser.setId(inspectorId);
        inspectorUser.setEmail("inspector.tech@secondlife.com");
        inspectorUser.setAccountStatus(AccountStatus.ACTIVE);

        UserProfile profile = new UserProfile(inspectorUser, "Nguyễn Văn Kỹ Thuật", "0901234567", null);
        inspectorUser.setProfile(profile);
    }

    @Test
    @DisplayName("getInspectorStats should calculate KPI, jobs, and earnings correctly for the current month")
    void testGetInspectorStats_Success() {
        when(userRepository.findById(inspectorId)).thenReturn(Optional.of(inspectorUser));

        LocalDate now = LocalDate.now();
        int month = now.getMonthValue();
        int year = now.getYear();

        YearMonth ym = YearMonth.of(year, month);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
        Instant withinMonth = ym.atDay(15).atStartOfDay(zone).toInstant();
        Instant outsideMonth = ym.minusMonths(2).atDay(15).atStartOfDay(zone).toInstant();

        // Order 1: PASSED in month
        InspectionOrder o1 = new InspectionOrder();
        o1.setId(UUID.randomUUID());
        o1.setStatus(InspectionOrderStatus.PASSED.name());
        o1.setInspectionFee(new BigDecimal("150000"));
        o1.setTravelFee(new BigDecimal("30000"));
        o1.setUpdatedAt(withinMonth);

        // Order 2: COMPLETED in month
        InspectionOrder o2 = new InspectionOrder();
        o2.setId(UUID.randomUUID());
        o2.setStatus(InspectionOrderStatus.COMPLETED.name());
        o2.setInspectionFee(new BigDecimal("200000"));
        o2.setTravelFee(new BigDecimal("50000"));
        o2.setUpdatedAt(withinMonth);

        // Order 3: FAILED in month
        InspectionOrder o3 = new InspectionOrder();
        o3.setId(UUID.randomUUID());
        o3.setStatus(InspectionOrderStatus.FAILED.name());
        o3.setInspectionFee(new BigDecimal("100000"));
        o3.setTravelFee(new BigDecimal("20000"));
        o3.setUpdatedAt(withinMonth);

        // Order 4: PASSED but outside month (should be ignored for monthly earnings & counts)
        InspectionOrder o4 = new InspectionOrder();
        o4.setId(UUID.randomUUID());
        o4.setStatus(InspectionOrderStatus.PASSED.name());
        o4.setInspectionFee(new BigDecimal("500000"));
        o4.setTravelFee(new BigDecimal("50000"));
        o4.setUpdatedAt(outsideMonth);

        // Order 5: INSPECTING (pending settlement)
        InspectionOrder o5 = new InspectionOrder();
        o5.setId(UUID.randomUUID());
        o5.setStatus(InspectionOrderStatus.INSPECTING.name());
        o5.setInspectionFee(new BigDecimal("180000"));
        o5.setTravelFee(new BigDecimal("40000"));

        // Order 6: SCHEDULED (pending settlement)
        InspectionOrder o6 = new InspectionOrder();
        o6.setId(UUID.randomUUID());
        o6.setStatus(InspectionOrderStatus.SCHEDULED.name());
        o6.setInspectionFee(new BigDecimal("120000"));
        o6.setTravelFee(new BigDecimal("25000"));

        when(inspectionOrderRepository.findByInspectorId(inspectorId))
                .thenReturn(List.of(o1, o2, o3, o4, o5, o6));

        InspectorStatsResponse response = inspectionService.getInspectorStats(inspectorId, month, year);

        assertNotNull(response);
        assertEquals(inspectorId, response.getInspectorId());
        assertEquals("Nguyễn Văn Kỹ Thuật", response.getInspectorName());
        assertTrue(response.getBadgeCode().startsWith("SL-TECH-"));
        assertEquals("Kỹ thuật viên Thẩm định Bậc 3", response.getSkillLevel());

        // Jobs counts (o1, o2, o3)
        assertEquals(3, response.getCompletedJobs());
        assertEquals(2, response.getPassedJobs()); // o1 (PASSED), o2 (COMPLETED)
        assertEquals(1, response.getFailedJobs()); // o3 (FAILED)

        // Fees: labor = 150k + 200k + 100k = 450k
        assertEquals(new BigDecimal("450000"), response.getLaborFeeEarned());
        // Travel: 30k + 50k + 20k = 100k
        assertEquals(new BigDecimal("100000"), response.getTravelAllowanceEarned());
        // Monthly: 450k + 100k = 550k
        assertEquals(new BigDecimal("550000"), response.getMonthlyEarnings());

        // Pending: o5 (180k+40k=220k) + o6 (120k+25k=145k) = 365k
        assertEquals(new BigDecimal("365000"), response.getPendingSettlementAmount());

        // Default KPI ratings
        assertEquals(4.9, response.getAverageRating());
        assertEquals(98.5, response.getOnTimeRate());
    }

    @Test
    @DisplayName("getInspectorStats should throw NotFoundException when inspector not found")
    void testGetInspectorStats_NotFound() {
        when(userRepository.findById(inspectorId)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () ->
                inspectionService.getInspectorStats(inspectorId, 10, 2026));
    }

    @Test
    @DisplayName("getPayoutHistory should return paged settlements correctly")
    void testGetPayoutHistory_Success() {
        Pageable pageable = PageRequest.of(0, 10);

        Post post = new Post();
        post.setTitle("iPhone 14 Pro Max 256GB Vàng Gold");

        InspectionOrder order = new InspectionOrder();
        UUID orderId = UUID.randomUUID();
        order.setId(orderId);
        order.setPost(post);
        order.setOrderType(InspectionOrderType.AT_HOME);
        order.setInspectionFee(new BigDecimal("150000"));
        order.setTravelFee(new BigDecimal("35000"));
        order.setStatus(InspectionOrderStatus.PASSED.name());
        order.setUpdatedAt(Instant.now());

        Page<InspectionOrder> page = new PageImpl<>(List.of(order), pageable, 1);
        when(inspectionOrderRepository.findPayoutOrdersByInspectorId(inspectorId, pageable)).thenReturn(page);

        Page<InspectorPayoutResponse> result = inspectionService.getPayoutHistory(inspectorId, pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());

        InspectorPayoutResponse item = result.getContent().get(0);
        assertEquals(orderId, item.getOrderId());
        assertTrue(item.getPayoutId().startsWith("PO-"));
        assertEquals("iPhone 14 Pro Max 256GB Vàng Gold", item.getPostTitle());
        assertEquals(InspectionOrderType.AT_HOME, item.getOrderType());
        assertEquals(new BigDecimal("150000"), item.getLaborFee());
        assertEquals(new BigDecimal("35000"), item.getTravelFee());
        assertEquals(new BigDecimal("185000"), item.getTotalPayout());
        assertEquals("SETTLED", item.getStatus());
    }

    @Test
    @DisplayName("UserMapper should map badgeCode, stationHub, and certificationTitle for INSPECTOR role")
    void testUserMapper_InspectorProfile() {
        com.secondlife.secondlife.mapper.UserMapper mapper = new com.secondlife.secondlife.mapper.UserMapper();

        User manager = new User();
        UUID managerId = UUID.randomUUID();
        manager.setId(managerId);
        UserProfile managerProfile = new UserProfile(manager, "Trung Tâm SecondLife - Trạm Thủ Đức", "0988888888", null);
        manager.setProfile(managerProfile);

        User inspector = new User();
        UUID inspId = UUID.randomUUID();
        inspector.setId(inspId);
        inspector.setEmail("tech@secondlife.com");
        UserProfile inspProfile = new UserProfile(inspector, "Lê Kỹ Thuật", "0912345678", null);
        inspector.setProfile(inspProfile);
        inspector.setManager(manager);

        com.secondlife.secondlife.entity.Role inspectorRole = new com.secondlife.secondlife.entity.Role("INSPECTOR", "KTV", "Kỹ thuật viên");
        inspector.addRole(inspectorRole);

        com.secondlife.secondlife.dto.response.UserProfileResponse response = mapper.toProfileResponse(inspector);

        assertNotNull(response);
        assertNotNull(response.badgeCode());
        assertTrue(response.badgeCode().startsWith("SL-TECH-"));
        assertEquals("Kỹ thuật viên Thẩm định Thiết bị Bậc 3", response.certificationTitle());
        assertEquals(managerId, response.stationHubId());
        assertEquals("Trung Tâm SecondLife - Trạm Thủ Đức", response.stationHubName());
    }

    @Test
    @DisplayName("UserMapper should leave inspector fields as null for regular BUYER/SELLER users")
    void testUserMapper_NonInspectorProfile() {
        com.secondlife.secondlife.mapper.UserMapper mapper = new com.secondlife.secondlife.mapper.UserMapper();

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("buyer@secondlife.com");
        UserProfile profile = new UserProfile(user, "Trần Người Mua", "0900000000", null);
        user.setProfile(profile);

        com.secondlife.secondlife.entity.Role userRole = new com.secondlife.secondlife.entity.Role("BUYER", "Người mua", "Khách hàng mua");
        user.addRole(userRole);

        com.secondlife.secondlife.dto.response.UserProfileResponse response = mapper.toProfileResponse(user);

        assertNotNull(response);
        assertNull(response.badgeCode());
        assertNull(response.certificationTitle());
        assertNull(response.stationHubId());
        assertNull(response.stationHubName());
    }
}
