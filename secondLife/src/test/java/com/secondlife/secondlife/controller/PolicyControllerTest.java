package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.AcceptPolicyRequest;
import com.secondlife.secondlife.dto.response.PolicyAcceptanceStatusResponse;
import com.secondlife.secondlife.dto.response.PolicyVersionResponse;
import com.secondlife.secondlife.enums.PolicyType;
import com.secondlife.secondlife.enums.PolicyVersionStatus;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.GlobalExceptionHandler;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.service.PolicyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PolicyControllerTest {

    private MockMvc mockMvc;

    @Mock private PolicyService policyService;
    @Mock private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private PolicyController policyController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(policyController)
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getCurrentPolicy_ShouldReturn200Publicly() throws Exception {
        UUID policyId = UUID.randomUUID();
        when(policyService.getCurrentPolicy(PolicyType.TERMS_OF_SERVICE)).thenReturn(new PolicyVersionResponse(
                policyId,
                PolicyType.TERMS_OF_SERVICE,
                "Điều khoản sử dụng và Quy chế hoạt động SecondLife",
                "1.0.0",
                "# Quy chế hoạt động",
                "abc123",
                PolicyVersionStatus.ACTIVE,
                true,
                Instant.now(),
                null,
                Instant.now()
        ));

        mockMvc.perform(get("/api/v1/policies/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(policyId.toString()))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.contentMarkdown").value("# Quy chế hoạt động"));
    }

    @Test
    void acceptPolicy_WhenAuthenticated_ShouldUseResolvedUserId() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID policyId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);
        when(policyService.acceptCurrentPolicy(eq(userId), any(AcceptPolicyRequest.class))).thenReturn(
                new PolicyAcceptanceStatusResponse(
                        PolicyType.TERMS_OF_SERVICE,
                        policyId,
                        "1.0.0",
                        "Điều khoản sử dụng",
                        Instant.now(),
                        null,
                        false,
                        true,
                        Instant.now(),
                        Instant.now(),
                        "1.0.0",
                        Instant.now()
                )
        );

        String body = """
                {
                  "policyVersionId": "%s",
                  "accepted": true,
                  "source": "WEB",
                  "context": "POLICY_UPDATE"
                }
                """.formatted(policyId);

        mockMvc.perform(post("/api/v1/policies/acceptance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.acceptedCurrentVersion").value(true))
                .andExpect(jsonPath("$.data.requiresAcceptance").value(false));
    }

    @Test
    void acceptPolicy_WhenPolicyVersionOutdated_ShouldReturn409Conflict() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID oldPolicyId = UUID.randomUUID();
        when(currentUserProvider.resolveUserId(any())).thenReturn(userId);
        when(policyService.acceptCurrentPolicy(eq(userId), any(AcceptPolicyRequest.class)))
                .thenThrow(new ConflictException("Chính sách đã được cập nhật lên phiên bản mới (2.0.0)"));

        String body = """
                {
                  "policyVersionId": "%s",
                  "accepted": true,
                  "source": "WEB"
                }
                """.formatted(oldPolicyId);

        mockMvc.perform(post("/api/v1/policies/acceptance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false));
    }
}
