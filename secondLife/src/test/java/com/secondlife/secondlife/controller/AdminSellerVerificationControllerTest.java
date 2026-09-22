package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.controller.admin.AdminSellerVerificationController;
import com.secondlife.secondlife.dto.request.SellerVerificationReviewRequest;
import com.secondlife.secondlife.dto.response.AdminSellerVerificationDetailResponse;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.dto.response.VerificationEventResponse;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.SellerVerificationService;
import com.secondlife.secondlife.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminSellerVerificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SellerVerificationService sellerVerificationService;

    @Mock
    private UserService userService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private AdminSellerVerificationController controller;

    private UUID adminId;
    private UUID verificationId;

    @BeforeEach
    void setUp() throws Exception {
        adminId = UUID.randomUUID();
        verificationId = UUID.randomUUID();

        User adminUser = new User("admin@example.com", "pass", AccountStatus.ACTIVE);
        java.lang.reflect.Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(adminUser, adminId);
        CustomUserDetails userDetails = new CustomUserDetails(adminUser);

        org.springframework.web.method.support.HandlerMethodArgumentResolver authPrincipalResolver =
                new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(org.springframework.core.MethodParameter parameter) {
                        return parameter.getParameterType().isAssignableFrom(CustomUserDetails.class);
                    }

                    @Override
                    public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                                                  org.springframework.web.method.support.ModelAndViewContainer mavContainer,
                                                  org.springframework.web.context.request.NativeWebRequest webRequest,
                                                  org.springframework.web.bind.support.WebDataBinderFactory binderFactory) {
                        return userDetails;
                    }
                };

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(authPrincipalResolver, new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    void getSellerVerifications_ShouldReturnPageResponse() throws Exception {
        SellerVerificationResponse item = new SellerVerificationResponse(
                verificationId, UUID.randomUUID(), "seller@example.com", "Jane Doe",
                VerificationType.CITIZEN_ID, "********9988",
                "http://f", "http://b", null,
                SellerVerificationStatus.NEEDS_REVIEW, EkycStatus.PASSED, RiskStatus.REVIEW,
                null, ReasonCode.FACE_MATCH_BORDERLINE, 0,
                Instant.now(), null, null, null
        );

        PageResponse<SellerVerificationResponse> pageResponse = PageResponse.from(
                new PageImpl<>(List.of(item), PageRequest.of(0, 10), 1)
        );

        when(sellerVerificationService.getAdminVerifications(any(), any(), any(), any(), any()))
                .thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/admin/seller-verifications?status=NEEDS_REVIEW"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].documentNumber").value("********9988"))
                .andExpect(jsonPath("$.data.items[0].status").value("NEEDS_REVIEW"));
    }

    @Test
    void getSellerVerificationById_ShouldReturnAdminDetailWithHistory() throws Exception {
        VerificationEventResponse event = new VerificationEventResponse(
                UUID.randomUUID(), VerificationEventType.EKYC_STARTED,
                SellerVerificationStatus.SUBMITTED, SellerVerificationStatus.EKYC_PENDING,
                null, "SYSTEM", null, "eKYC started", Instant.now()
        );

        AdminSellerVerificationDetailResponse detailResponse = new AdminSellerVerificationDetailResponse(
                verificationId, UUID.randomUUID(), "user@example.com", "Alice",
                VerificationType.CITIZEN_ID, "********1122", "http://f", "http://b", null,
                SellerVerificationStatus.NEEDS_REVIEW, EkycStatus.PASSED, RiskStatus.REVIEW,
                null, ReasonCode.FACE_MATCH_BORDERLINE, "SECONDLIFE_MOCK_EKYC", "REF-1234",
                0.78, 0.99, 0.95, 60.0, 0,
                Instant.now(), Instant.now(), Instant.now(), null, null, null,
                List.of(event)
        );

        when(sellerVerificationService.getAdminVerificationById(verificationId))
                .thenReturn(detailResponse);

        mockMvc.perform(get("/api/v1/admin/seller-verifications/" + verificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.documentNumberMasked").value("********1122"))
                .andExpect(jsonPath("$.data.faceMatchScore").value(0.78))
                .andExpect(jsonPath("$.data.eventHistory[0].eventType").value("EKYC_STARTED"));
    }

    @Test
    void approveSellerVerification_WhenNeedsReview_ShouldReturn200() throws Exception {
        when(currentUserProvider.resolveAdminId(any())).thenReturn(adminId);

        SellerVerificationResponse response = new SellerVerificationResponse(
                verificationId, UUID.randomUUID(), "seller@example.com", "Jane Doe",
                VerificationType.CITIZEN_ID, "********9988",
                "http://f", "http://b", null,
                SellerVerificationStatus.APPROVED, EkycStatus.PASSED, RiskStatus.REVIEW,
                ReviewSource.ADMIN, ReasonCode.FACE_MATCH_BORDERLINE, 0,
                Instant.now(), Instant.now(), adminId, null
        );

        when(sellerVerificationService.approveVerification(adminId, verificationId))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/admin/seller-verifications/" + verificationId + "/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewSource").value("ADMIN"));
    }

    @Test
    void approveSellerVerification_WhenNotNeedsReview_ShouldReturn409Conflict() throws Exception {
        when(currentUserProvider.resolveAdminId(any())).thenReturn(adminId);

        when(sellerVerificationService.approveVerification(adminId, verificationId))
                .thenThrow(new ConflictException("Chỉ hồ sơ ở trạng thái NEEDS_REVIEW mới có thể phê duyệt"));

        mockMvc.perform(post("/api/v1/admin/seller-verifications/" + verificationId + "/approve"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void rejectSellerVerification_WhenNeedsReview_ShouldReturn200() throws Exception {
        when(currentUserProvider.resolveAdminId(any())).thenReturn(adminId);

        SellerVerificationResponse response = new SellerVerificationResponse(
                verificationId, UUID.randomUUID(), "seller@example.com", "Jane Doe",
                VerificationType.CITIZEN_ID, "********9988",
                "http://f", "http://b", null,
                SellerVerificationStatus.REJECTED, EkycStatus.PASSED, RiskStatus.REVIEW,
                ReviewSource.ADMIN, ReasonCode.FACE_MATCH_BORDERLINE, 0,
                Instant.now(), Instant.now(), adminId, "ID card photo is expired"
        );

        when(sellerVerificationService.rejectVerification(eq(adminId), eq(verificationId), any(SellerVerificationReviewRequest.class)))
                .thenReturn(response);

        String jsonBody = """
                {
                    "rejectionReason": "ID card photo is expired"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/seller-verifications/" + verificationId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("ID card photo is expired"));
    }

    @Test
    void rejectSellerVerification_WhenNotNeedsReview_ShouldReturn409Conflict() throws Exception {
        when(currentUserProvider.resolveAdminId(any())).thenReturn(adminId);

        when(sellerVerificationService.rejectVerification(eq(adminId), eq(verificationId), any(SellerVerificationReviewRequest.class)))
                .thenThrow(new ConflictException("Chỉ hồ sơ ở trạng thái NEEDS_REVIEW mới có thể từ chối"));

        String jsonBody = """
                {
                    "rejectionReason": "Some reason"
                }
                """;

        mockMvc.perform(post("/api/v1/admin/seller-verifications/" + verificationId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }
}
