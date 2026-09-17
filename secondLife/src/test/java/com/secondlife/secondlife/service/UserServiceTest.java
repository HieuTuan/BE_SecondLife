package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.AdminStatusUpdateRequest;
import com.secondlife.secondlife.dto.request.ChangePasswordRequest;
import com.secondlife.secondlife.dto.request.CreateInspectionCenterRequest;
import com.secondlife.secondlife.dto.request.UpdateProfileRequest;
import com.secondlife.secondlife.dto.response.UserProfileResponse;
import com.secondlife.secondlife.dto.response.UserSummaryResponse;
import com.secondlife.secondlife.entity.Role;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.RoleCode;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.mapper.UserMapper;
import com.secondlife.secondlife.repository.RoleRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.impl.UserServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private User user;
    private User admin;
    private UUID userId;
    private UUID adminId;

    @BeforeEach
    void setUp() throws Exception {
        userId = UUID.randomUUID();
        user = new User("user@example.com", "encoded-old-pass", AccountStatus.ACTIVE);
        UserProfile profile = new UserProfile(user, "Old Name", "1234567890", null);
        user.setProfile(profile);
        Field idField1 = User.class.getDeclaredField("id");
        idField1.setAccessible(true);
        idField1.set(user, userId);

        adminId = UUID.randomUUID();
        admin = new User("admin@example.com", "encoded-admin-pass", AccountStatus.ACTIVE);
        Role adminRole = new Role(RoleCode.ADMIN.name(), "Admin", "Admin role");
        admin.addRole(adminRole);
        Field idField2 = User.class.getDeclaredField("id");
        idField2.setAccessible(true);
        idField2.set(admin, adminId);
    }

    @Test
    void updateProfile_WhenValid_ShouldUpdateAndReturn() {
        UpdateProfileRequest request = new UpdateProfileRequest("New Name", "0987654321", "http://avatar.jpg");

        when(userRepository.findByIdWithAuthorities(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenReturn(user);

        UserProfileResponse expected = new UserProfileResponse(
                userId, user.getEmail(), "New Name", "0987654321", "http://avatar.jpg",
                AccountStatus.ACTIVE, false, null, null, null, null, null
        );
        when(userMapper.toProfileResponse(user)).thenReturn(expected);

        UserProfileResponse result = userService.updateProfile(userId, request);

        assertNotNull(result);
        assertEquals("New Name", result.fullName());
        assertEquals("0987654321", result.phone());
        verify(userRepository).save(user);
    }

    @Test
    void changePassword_WhenCorrectCurrentPassword_ShouldUpdateAndRevokeSessions() {
        ChangePasswordRequest request = new ChangePasswordRequest("OldPass@123", "NewPass@123", "NewPass@123");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("OldPass@123", "encoded-old-pass")).thenReturn(true);
        when(passwordEncoder.encode("NewPass@123")).thenReturn("encoded-new-pass");

        userService.changePassword(userId, request);

        assertEquals("encoded-new-pass", user.getPasswordHash());
        verify(userRepository).save(user);
        verify(tokenService).revokeAllUserRefreshTokens(userId);
    }

    @Test
    void changePassword_WhenWrongCurrentPassword_ShouldThrowBadRequest() {
        ChangePasswordRequest request = new ChangePasswordRequest("WrongPass@123", "NewPass@123", "NewPass@123");

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass@123", "encoded-old-pass")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> userService.changePassword(userId, request));
        verify(tokenService, never()).revokeAllUserRefreshTokens(any());
    }

    @Test
    void updateAdminUserStatus_WhenSelfLocking_ShouldThrowBadRequest() {
        AdminStatusUpdateRequest request = new AdminStatusUpdateRequest(AccountStatus.LOCKED);
        when(userRepository.findByIdWithAuthorities(adminId)).thenReturn(Optional.of(admin));

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                userService.updateAdminUserStatus(adminId, adminId, request)
        );

        assertTrue(ex.getMessage().contains("own account"));
    }

    @Test
    void updateAdminUserStatus_WhenLockingLastActiveAdmin_ShouldThrowBadRequest() {
        UUID otherAdminId = UUID.randomUUID();
        User otherAdmin = new User("otheradmin@example.com", "pass", AccountStatus.ACTIVE);
        Role adminRole = new Role(RoleCode.ADMIN.name(), "Admin", "Admin");
        otherAdmin.addRole(adminRole);

        AdminStatusUpdateRequest request = new AdminStatusUpdateRequest(AccountStatus.LOCKED);

        when(userRepository.findByIdWithAuthorities(otherAdminId)).thenReturn(Optional.of(otherAdmin));
        when(userRepository.countActiveAdmins()).thenReturn(1L);

        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                userService.updateAdminUserStatus(adminId, otherAdminId, request)
        );

        assertTrue(ex.getMessage().contains("last remaining active Administrator"));
    }

    @Test
    void createInspectionCenterAccount_WhenValid_ShouldProvisionAccount() {
        CreateInspectionCenterRequest request = new CreateInspectionCenterRequest(
                "center@secondlife.com", "CenterPass@123", "Inspection Center 01", "123456789"
        );

        Role inspectionRole = new Role(RoleCode.INSPECTION_CENTER.name(), "Center", "Center");

        when(userRepository.existsByEmailIgnoreCase("center@secondlife.com")).thenReturn(false);
        when(roleRepository.findByCodeWithPermissions(RoleCode.INSPECTION_CENTER.name())).thenReturn(Optional.of(inspectionRole));
        when(passwordEncoder.encode(request.password())).thenReturn("encoded-center-pass");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserSummaryResponse expected = new UserSummaryResponse(
                UUID.randomUUID(), "center@secondlife.com", "Inspection Center 01", "123456789", null, AccountStatus.ACTIVE, true
        );
        when(userMapper.toSummaryResponse(any(User.class))).thenReturn(expected);

        UserSummaryResponse result = userService.createInspectionCenterAccount(request);

        assertNotNull(result);
        assertEquals("center@secondlife.com", result.email());
        verify(userRepository).save(any(User.class));
    }
}
