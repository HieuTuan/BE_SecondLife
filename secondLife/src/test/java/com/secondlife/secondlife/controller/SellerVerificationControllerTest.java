package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.request.SellerVerificationRequest;
import com.secondlife.secondlife.dto.request.SellerVerificationResubmitRequest;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.SellerVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SellerVerificationControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SellerVerificationService sellerVerificationService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private SellerVerificationController controller;

    private UUID userId;
    private UUID verificationId;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        verificationId = UUID.randomUUID();

        User user = new User("buyer@example.com", "pass", AccountStatus.ACTIVE);
        java.lang.reflect.Field idField = User.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(user, userId);
        CustomUserDetails userDetails = new CustomUserDetails(user);

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
                .setCustomArgumentResolvers(authPrincipalResolver)
                .build();

        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);
    }

    @Test
    void submitVerification_WhenValid_ShouldReturn201WithMaskedDocumentNumber() throws Exception {
        SellerVerificationResponse response = new SellerVerificationResponse(
                verificationId, userId, "buyer@example.com", "John Doe",
                VerificationType.CITIZEN_ID, "********1234",
                "http://front.url", "http://back.url", null,
                SellerVerificationStatus.APPROVED, EkycStatus.PASSED, RiskStatus.CLEAR,
                ReviewSource.SYSTEM, ReasonCode.NONE, 0,
                Instant.now(), Instant.now(), null, null
        );

        when(sellerVerificationService.submitVerification(eq(userId), any(SellerVerificationRequest.class)))
                .thenReturn(response);

        String jsonBody = """
                {
                    "verificationType": "CITIZEN_ID",
                    "documentNumber": "012345671234",
                    "documentFrontUrl": "http://front.url",
                    "documentBackUrl": "http://back.url"
                }
                """;

        mockMvc.perform(post("/api/v1/seller-verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.documentNumber").value("********1234"))
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.ekycStatus").value("PASSED"))
                .andExpect(jsonPath("$.data.riskStatus").value("CLEAR"));
    }

    @Test
    void getMyVerification_WhenExists_ShouldReturn200() throws Exception {
        SellerVerificationResponse response = new SellerVerificationResponse(
                verificationId, userId, "buyer@example.com", "John Doe",
                VerificationType.CITIZEN_ID, "********5678",
                "http://front.url", "http://back.url", null,
                SellerVerificationStatus.NEEDS_REVIEW, EkycStatus.PASSED, RiskStatus.REVIEW,
                null, ReasonCode.FACE_MATCH_BORDERLINE, 0,
                Instant.now(), null, null, null
        );

        when(sellerVerificationService.getCurrentVerification(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/seller-verifications/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.documentNumber").value("********5678"))
                .andExpect(jsonPath("$.data.status").value("NEEDS_REVIEW"));
    }

    @Test
    void resubmitVerification_WhenValid_ShouldReturn200() throws Exception {
        SellerVerificationResponse response = new SellerVerificationResponse(
                verificationId, userId, "buyer@example.com", "John Doe",
                VerificationType.CITIZEN_ID, "********5678",
                "http://new-front.url", "http://new-back.url", null,
                SellerVerificationStatus.APPROVED, EkycStatus.PASSED, RiskStatus.CLEAR,
                ReviewSource.SYSTEM, ReasonCode.NONE, 1,
                Instant.now(), Instant.now(), null, null
        );

        when(sellerVerificationService.resubmitVerification(eq(userId), eq(verificationId), any(SellerVerificationResubmitRequest.class)))
                .thenReturn(response);

        String jsonBody = """
                {
                    "documentFrontUrl": "http://new-front.url",
                    "documentBackUrl": "http://new-back.url"
                }
                """;

        mockMvc.perform(post("/api/v1/seller-verifications/" + verificationId + "/resubmit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.resubmissionCount").value(1));
    }

    @Test
    void resubmitVerification_WhenNotAllowed_ShouldReturn409Conflict() throws Exception {
        when(sellerVerificationService.resubmitVerification(eq(userId), eq(verificationId), any(SellerVerificationResubmitRequest.class)))
                .thenThrow(new ConflictException("Chỉ hồ sơ ở trạng thái RESUBMIT_REQUIRED mới được phép gửi lại"));

        String jsonBody = """
                {
                    "documentFrontUrl": "http://new-front.url",
                    "documentBackUrl": "http://new-back.url"
                }
                """;

        mockMvc.perform(post("/api/v1/seller-verifications/" + verificationId + "/resubmit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }
}
