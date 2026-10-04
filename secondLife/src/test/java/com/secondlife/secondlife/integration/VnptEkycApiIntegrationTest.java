package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.ekyc.vnpt.VnptResults;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.security.jwt.JwtTokenProvider;
import com.secondlife.secondlife.service.ekyc.vnpt.VnptEkycOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none",
        "app.seeder.enabled=false", "app.ekyc.provider=VNPT",
        "app.vnpt.base-url=https://api.idg.vnpt.vn",
        "app.vnpt.client-id=dummy-client-id", "app.vnpt.client-secret=dummy-client-secret",
        "app.vnpt.token-id=dummy-token-id", "app.vnpt.token-key=dummy-token-key",
        "app.vnpt.mac-address=TEST1", "app.vnpt.timeout-ms=1000"
})
class VnptEkycApiIntegrationTest {
    @Container
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private VnptEkycOrchestrator orchestrator;
    @MockitoBean private com.secondlife.secondlife.service.SellerOnboardingService onboarding;

    @Test
    void verifyRequiresBuyerPermissionAndAcceptsThreeImages() throws Exception {
        User buyer = userForRole("BUYER");
        User staff = userForRole("STAFF");
        when(orchestrator.verify(any(byte[].class), any(byte[].class), any(byte[].class),
                anyString(), anyString(), anyInt()))
                .thenReturn(new VnptResults.Verification(UUID.randomUUID(), true,
                        "PASSED", null, null, null, null, null));

        mockMvc.perform(request()).andExpect(status().isUnauthorized());
        mockMvc.perform(request().header("Authorization", bearer(staff)))
                .andExpect(status().isForbidden());
        mockMvc.perform(request().header("Authorization", bearer(buyer)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(true))
                .andExpect(jsonPath("$.data.verificationId").exists())
                .andExpect(jsonPath("$.data.reasonCode").value("PASSED"));
        verify(orchestrator).verify(any(byte[].class), any(byte[].class), any(byte[].class),
                org.mockito.ArgumentMatchers.eq("IOS_model_os_device_sdk_id_time"),
                org.mockito.ArgumentMatchers.eq("request-identifier"), org.mockito.ArgumentMatchers.eq(-1));
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder request() {
        byte[] jpeg = {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0};
        return multipart("/api/v1/ekyc/verify")
                .file(new MockMultipartFile("frontImage", "front.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg))
                .file(new MockMultipartFile("backImage", "back.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg))
                .file(new MockMultipartFile("selfieImage", "selfie.jpg", MediaType.IMAGE_JPEG_VALUE, jpeg))
                .param("clientSession", "IOS_model_os_device_sdk_id_time")
                .param("token", "request-identifier");
    }

    private User userForRole(String code) {
        Role role = roleRepository.findByCode(code).orElseThrow();
        User user = new User("vnpt-" + UUID.randomUUID() + "@example.test",
                passwordEncoder.encode("Password@123"), AccountStatus.ACTIVE);
        user.addRole(role);
        return userRepository.saveAndFlush(user);
    }

    private String bearer(User user) {
        return "Bearer " + jwtTokenProvider.generateAccessToken(user);
    }
}
