package com.secondlife.secondlife.service;

import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.AdminStatusUpdateRequest;
import com.secondlife.secondlife.dto.request.ChangePasswordRequest;
import com.secondlife.secondlife.dto.request.CreateInspectionCenterRequest;
import com.secondlife.secondlife.dto.request.UpdateProfileRequest;
import com.secondlife.secondlife.dto.response.UserAdminResponse;
import com.secondlife.secondlife.dto.response.UserProfileResponse;
import com.secondlife.secondlife.dto.response.UserSummaryResponse;
import com.secondlife.secondlife.enums.AccountStatus;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface UserService {

    UserProfileResponse getProfile(UUID userId);

    UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);

    void changePassword(UUID userId, ChangePasswordRequest request);

    PageResponse<UserAdminResponse> getAdminUsers(String email, AccountStatus status, String role, Pageable pageable);

    UserAdminResponse getAdminUserById(UUID userId);

    UserAdminResponse updateAdminUserStatus(UUID currentAdminId, UUID targetUserId, AdminStatusUpdateRequest request);

    UserSummaryResponse createInspectionCenterAccount(CreateInspectionCenterRequest request);
}
