package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.dto.response.ReputationResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.Gender;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

class UserMapperMergeCompatibilityTest {
    private final UserMapper mapper = MapperTestSupport.mapper(UserMapper.class);
    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void inspectorProfileIncludesDevFieldsAndRetainsRefactoredProfileMapping() {
        User inspector = user("INSPECTOR");
        User manager = user("INSPECTION_CENTER");
        manager.getProfile().setFullName("Inspection Center 01");
        inspector.setManager(manager);

        var response = mapper.toProfileResponse(inspector, null);
        var body = json.valueToTree(response);

        assertEquals("SL-TECH-A1B2", body.path("badgeCode").asText());
        assertEquals(manager.getId().toString(), body.path("stationHubId").asText());
        assertEquals("Inspection Center 01", body.path("stationHubName").asText());
        assertEquals("Kỹ thuật viên Thẩm định Thiết bị Bậc 3", body.path("certificationTitle").asText());
        assertEquals("Test User", response.fullName());
        assertEquals(Gender.MALE, response.gender());
        assertEquals("Ho Chi Minh", response.province());
    }

    @Test
    void inspectorWithoutManagerUsesExistingDevFallback() {
        var body = json.valueToTree(mapper.toProfileResponse(user("INSPECTOR"), null));
        assertEquals("SL-TECH-A1B2", body.path("badgeCode").asText());
        assertEquals("Trung Tâm Kiểm Định SecondLife", body.path("stationHubName").asText());
        assertFalse(body.hasNonNull("stationHubId"));
    }

    @Test
    void inspectorAndSellerRolesRetainBothTechnicianFieldsAndSellerReputation() {
        User inspector = user("INSPECTOR");
        inspector.addRole(new Role("SELLER", "Seller", null));
        ReputationResponse reputation = mock(ReputationResponse.class);
        var response = mapper.toProfileResponse(inspector, reputation);
        assertSame(reputation, response.sellerReputation());
        assertEquals("SL-TECH-A1B2", json.valueToTree(response).path("badgeCode").asText());
    }

    @Test
    void buyerDoesNotReceiveTechnicianFieldsEvenWhenAManagerIsPresent() {
        User buyer = user("BUYER");
        buyer.setManager(user("INSPECTION_CENTER"));
        var body = json.valueToTree(mapper.toProfileResponse(buyer, null));
        for (String field : new String[]{"badgeCode", "stationHubId", "stationHubName", "certificationTitle"}) {
            assertFalse(body.hasNonNull(field), field);
        }
    }

    private User user(String role) {
        User user = new User("mapper@example.test", "hash", AccountStatus.ACTIVE);
        user.setId(UUID.fromString("11111111-1111-4111-8111-11111111a1b2"));
        user.addRole(new Role(role, role, null));
        UserProfile profile = new UserProfile(user, "Test User", "0901234567", "avatar.jpg");
        profile.setGender(Gender.MALE);
        profile.setProvince("Ho Chi Minh");
        user.setProfile(profile);
        return user;
    }
}
