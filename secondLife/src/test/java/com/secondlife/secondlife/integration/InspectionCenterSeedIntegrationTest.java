package com.secondlife.secondlife.integration;

import com.secondlife.secondlife.dto.request.InspectionCenterProfileRequest;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.seeder.InspectionCenterAccountSeeder;
import com.secondlife.secondlife.service.InspectionCenterProfileService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIf("databaseAvailable")
@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.properties", properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=none", "app.seeder.enabled=false",
        "app.seeder.inspection-center-2.enabled=true",
        "app.seeder.inspection-center-2.email=seed-hanoi@example.test",
        "app.seeder.inspection-center-2.password=IntegrationTestOnly!123",
        "app.seeder.inspection-center-2.full-name=SecondLife Hanoi Test Hub",
        "app.seeder.inspection-center-2.phone=02400000000",
        "app.seeder.inspection-center-2.province=Hà Nội",
        "app.seeder.inspection-center-2.ward=Hòa Lạc",
        "app.seeder.inspection-center-2.street-address=Km29 Đại lộ Thăng Long",
        "app.seeder.inspection-center-2.latitude=21.0135277",
        "app.seeder.inspection-center-2.longitude=105.5252615"
})
class InspectionCenterSeedIntegrationTest {
    private static final String DATABASE_ENV = "GHN_FLOW_TEST_JDBC_URL";
    private static final String EMAIL = "seed-hanoi@example.test";
    private static final GhnTestDatabase database = new GhnTestDatabase(DATABASE_ENV);

    static boolean databaseAvailable() {
        return GhnTestDatabase.available(DATABASE_ENV);
    }

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", database::getJdbcUrl);
        registry.add("spring.datasource.username", database::getUsername);
        registry.add("spring.datasource.password", database::getPassword);
    }

    @Autowired private UserRepository users;
    @Autowired private InspectionCenterProfileService profiles;
    @Autowired private PasswordEncoder passwords;
    @Autowired private InspectionCenterAccountSeeder seeder;

    @Test
    void seedsConfiguredCenterIndependentlyAndPreservesExistingAccountOnRetry() {
        var original = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertEquals(AccountStatus.ACTIVE, original.getAccountStatus());
        assertTrue(original.isEmailVerified());
        assertTrue(passwords.matches("IntegrationTestOnly!123", original.getPasswordHash()));

        var profile = profiles.getMyProfile(original.getId());
        assertTrue(profile.shippingAddressConfigured());
        assertEquals("SecondLife Hanoi Test Hub", profile.name());
        assertEquals("02400000000", profile.phone());
        profiles.updateMyProfile(original.getId(), new InspectionCenterProfileRequest(
                "Updated Hanoi Hub", profile.phone(), profile.province(), null, profile.ward(),
                profile.streetAddress(), profile.latitude(), profile.longitude()));

        seeder.run(new DefaultApplicationArguments());
        var afterRetry = users.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertEquals(original.getId(), afterRetry.getId());
        assertEquals(original.getPasswordHash(), afterRetry.getPasswordHash());
        assertEquals("Updated Hanoi Hub", profiles.getMyProfile(afterRetry.getId()).name());
    }
}
