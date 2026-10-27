package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.UpdateProfileRequest;
import com.secondlife.secondlife.entity.*;
import com.secondlife.secondlife.enums.*;
import com.secondlife.secondlife.mapper.UserMapper;
import com.secondlife.secondlife.repository.*;
import com.secondlife.secondlife.service.impl.UserServiceImpl;
import com.secondlife.secondlife.security.userdetails.UserPrincipalCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.secondlife.secondlife.mapper.MapperTestSupport;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfileGenderServiceTest {
    private final UserRepository users=mock(UserRepository.class);
    private final UserMapper mapper=MapperTestSupport.mapper(UserMapper.class);
    private final User user=new User("gender@example.test","hash",AccountStatus.ACTIVE);
    private UserServiceImpl service;
    @BeforeEach void setup() {
        user.setId(UUID.randomUUID()); user.setProfile(new UserProfile(user,"Nguyễn Test",null,null)); user.getProfile().setGender(Gender.MALE);
        when(users.findByIdWithProfileDetails(user.getId())).thenReturn(Optional.of(user)); when(users.save(user)).thenReturn(user);
        service=new UserServiceImpl(users,mock(RoleRepository.class),mock(PasswordEncoder.class),mock(TokenService.class),mapper,mock(NotificationService.class),mock(SellerReputationService.class),mock(UserPrincipalCache.class));
    }
    @Test void chosenGenderCanBeUpdatedAndReadFromProfileAndAuthSummary() {
        var result=service.updateProfile(user.getId(),new UpdateProfileRequest(null,null,null,null,null,null,null,null,null,Gender.FEMALE));
        assertEquals(Gender.FEMALE,result.gender()); assertEquals(Gender.FEMALE,user.getProfile().getGender());
        assertEquals(Gender.FEMALE,service.getProfile(user.getId()).gender()); assertEquals(Gender.FEMALE,mapper.toSummaryResponse(user).gender());
    }
    @Test void updatingOtherFieldsPreservesExistingGender() {
        var result=service.updateProfile(user.getId(),new UpdateProfileRequest("Tên mới",null,null,null,null,null,null,null,null));
        assertEquals(Gender.MALE,result.gender()); assertEquals("Tên mới",result.fullName());
    }
    @Test void existingAccountWithoutGenderRemainsUnspecified() {
        user.getProfile().setGender(null); assertNull(service.getProfile(user.getId()).gender());
        user.setProfile(null); assertNull(mapper.toSummaryResponse(user).gender());
    }
}
