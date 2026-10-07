package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.ekyc.EkycResult;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.EkycStatus;
import com.secondlife.secondlife.enums.ReasonCode;
import com.secondlife.secondlife.enums.RiskStatus;
import com.secondlife.secondlife.enums.SellerVerificationStatus;
import com.secondlife.secondlife.repository.IdentityRestrictionRepository;
import com.secondlife.secondlife.repository.SellerVerificationRepository;
import com.secondlife.secondlife.service.impl.SellerRiskServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SellerRiskConfigurationTest {
    private final SellerVerificationRepository verifications = mock(SellerVerificationRepository.class);
    private final IdentityRestrictionRepository restrictions = mock(IdentityRestrictionRepository.class);
    private final User user = activeUser();
    private final SellerVerification verification = verification(user);

    @Test
    void duplicateIdentityUsesConfiguredRiskScore() {
        SellerVerification duplicate = verification(activeUser());
        duplicate.setStatus(SellerVerificationStatus.APPROVED);
        when(verifications.findByDocumentNumberHash("identity-hash")).thenReturn(List.of(duplicate));

        var result = configuredService().evaluateRisk(user, verification,
                EkycResult.pass("MOCK", "reference", 0.95, 0.99, 0.98));

        assertEquals(RiskStatus.REVIEW, result.status());
        assertEquals(91.0, result.riskScore());
        assertTrue(result.reasonCodes().contains(ReasonCode.DUPLICATE_IDENTITY));
    }

    @Test
    void repeatedFailuresUseConfiguredCountAndRiskScore() {
        when(verifications.countByUserIdAndEkycStatus(user.getId(), EkycStatus.FAILED)).thenReturn(2L);

        var result = configuredService().evaluateRisk(user, verification,
                EkycResult.pass("MOCK", "reference", 0.95, 0.99, 0.98));

        assertEquals(RiskStatus.REVIEW, result.status());
        assertEquals(73.0, result.riskScore());
        assertTrue(result.reasonCodes().contains(ReasonCode.REPEATED_EKYC_FAILURES));
    }

    @Test
    void borderlineFaceMatchUsesConfiguredThresholdAndRiskScore() {
        var result = configuredService().evaluateRisk(user, verification,
                EkycResult.pass("MOCK", "reference", 0.55, 0.99, 0.98));

        assertEquals(RiskStatus.REVIEW, result.status());
        assertEquals(42.0, result.riskScore());
        assertTrue(result.reasonCodes().contains(ReasonCode.FACE_MATCH_BORDERLINE));
    }

    @Test
    void faceMatchAboveConfiguredThresholdRemainsClear() {
        var result = configuredService().evaluateRisk(user, verification,
                EkycResult.pass("MOCK", "reference", 0.70, 0.99, 0.98));

        assertEquals(RiskStatus.CLEAR, result.status());
    }

    @ParameterizedTest
    @MethodSource("invalidRiskSettings")
    void invalidRiskSettingFailsStartup(String property, Object value) {
        var failure = assertThrows(BeanCreationException.class,
                () -> configuredService(Map.of(property, value)));

        assertInstanceOf(IllegalArgumentException.class, failure.getMostSpecificCause());
        assertTrue(failure.getMostSpecificCause().getMessage().contains(property));
    }

    @Test
    void validRiskRangesIncludeLowerAndUpperBounds() {
        assertDoesNotThrow(() -> configuredService(Map.of(
                "app.risk.seller-review-threshold", 0.0,
                "app.risk.duplicate-identity-score", 0.0,
                "app.risk.repeated-failure-score", 0.0,
                "app.risk.borderline-face-match-score", 0.0,
                "app.risk.borderline-face-match-threshold", 0.0)));
        assertDoesNotThrow(() -> configuredService(Map.of(
                "app.risk.seller-review-threshold", 100.0,
                "app.risk.duplicate-identity-score", 100.0,
                "app.risk.repeated-failure-score", 100.0,
                "app.risk.borderline-face-match-score", 100.0,
                "app.risk.borderline-face-match-threshold", 1.0)));
    }

    private static Stream<Arguments> invalidRiskSettings() {
        Stream<Arguments> scores = Stream.of("app.risk.seller-review-threshold", "app.risk.duplicate-identity-score",
                        "app.risk.repeated-failure-score", "app.risk.borderline-face-match-score")
                .flatMap(property -> Stream.of(-1.0, 101.0, Double.NaN, Double.POSITIVE_INFINITY)
                        .map(value -> Arguments.of(property, value)));
        Stream<Arguments> faceThreshold = Stream.of(-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY)
                .map(value -> Arguments.of("app.risk.borderline-face-match-threshold", value));
        Stream<Arguments> failures = Stream.of(0, -1)
                .map(value -> Arguments.of("app.risk.repeated-failure-threshold", value));
        return Stream.concat(Stream.concat(scores, faceThreshold), failures);
    }

    private SellerRiskServiceImpl configuredService() {
        return configuredService(Map.of());
    }

    private SellerRiskServiceImpl configuredService(Map<String, Object> overrides) {
        try (var context = new AnnotationConfigApplicationContext()) {
            Map<String, Object> settings = new LinkedHashMap<>(Map.of(
                    "app.risk.duplicate-check-enabled", true,
                    "app.risk.seller-review-threshold", 70.0,
                    "app.risk.duplicate-identity-score", 91.0,
                    "app.risk.repeated-failure-score", 73.0,
                    "app.risk.borderline-face-match-score", 42.0,
                    "app.risk.repeated-failure-threshold", 2,
                    "app.risk.borderline-face-match-threshold", 0.60));
            settings.putAll(overrides);
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("risk-test", settings));
            context.registerBean(SellerVerificationRepository.class, () -> verifications);
            context.registerBean(IdentityRestrictionRepository.class, () -> restrictions);
            context.registerBean(SellerRiskServiceImpl.class);
            context.refresh();
            return context.getBean(SellerRiskServiceImpl.class);
        }
    }

    private static User activeUser() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setAccountStatus(AccountStatus.ACTIVE);
        return user;
    }

    private static SellerVerification verification(User user) {
        SellerVerification verification = new SellerVerification();
        verification.setUser(user);
        verification.setDocumentNumberHash("identity-hash");
        return verification;
    }
}
