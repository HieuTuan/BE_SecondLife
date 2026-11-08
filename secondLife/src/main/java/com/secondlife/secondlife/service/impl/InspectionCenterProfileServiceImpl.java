package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.common.VietnameseAdministrativeAreas;
import com.secondlife.secondlife.dto.request.InspectionCenterProfileRequest;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.InspectionCenterOptionResponse;
import com.secondlife.secondlife.dto.response.InspectionCenterProfileResponse;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.entity.UserProfile;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.mapper.InspectionCenterProfileMapper;
import com.secondlife.secondlife.repository.UserProfileRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionCenterProfileService;
import com.secondlife.secondlife.service.InspectionGovernanceAccessService;

import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionCenterProfileServiceImpl implements InspectionCenterProfileService {
    private final InspectionGovernanceAccessService access;
    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final InspectionCenterProfileMapper mapper;
    private final ShippingCatalogPolicy shippingPolicy;
    private final Validator validator;

    @Override
    public InspectionCenterProfileResponse getMyProfile(UUID actorId) {
        User center = access.requireCenter(actorId);
        return mapper.toResponse(center, configured(center.getProfile()));
    }

    @Override
    @Transactional
    public InspectionCenterProfileResponse updateMyProfile(UUID actorId, InspectionCenterProfileRequest request) {
        User center = access.requireCenter(actorId);
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new BadRequestException("Vui lòng nhập đủ tên, hotline, địa chỉ và GPS hợp lệ của trung tâm");
        }
        UserProfile profile = center.getProfile();
        if (profile == null) {
            profile = new UserProfile();
            center.setProfile(profile);
        }
        mapper.update(request, profile);
        if (!configured(profile)) {
            throw new BadRequestException("Thông tin trung tâm không hợp lệ: kiểm tra tên, hotline, địa chỉ tối đa 500 ký tự và GPS cùng tỉnh/thành trong khu vực phục vụ đã cấu hình");
        }
        profiles.saveAndFlush(profile);
        return mapper.toResponse(center, true);
    }

    @Override
    public List<InspectionCenterOptionResponse> getAvailableCenters() {
        return users.findActiveInspectionCentersWithProfile().stream()
                .map(center -> mapper.toOption(center, configured(center.getProfile())))
                .toList();
    }

    @Override
    public ShippingAddress requireShippingAddress(UUID centerId) {
        if (centerId == null) throw new ConflictException("Đơn kiểm định chưa được gắn trung tâm tiếp nhận");
        User center = access.requireCenter(centerId);
        if (!configured(center.getProfile())) {
            throw new ConflictException("Trung tâm cần cập nhật tên, hotline, địa chỉ và GPS hợp lệ trước khi tạo chuyến vận chuyển");
        }
        return mapper.toShippingAddress(center.getProfile());
    }

    private boolean configured(UserProfile profile) {
        if (profile == null || !validator.validate(mapper.toRequest(profile)).isEmpty()) return false;
        ShippingAddress address = mapper.toShippingAddress(profile);
        if (!validator.validate(address).isEmpty()) return false;
        var division = VietnameseAdministrativeAreas.locate(address.latitude().doubleValue(), address.longitude().doubleValue());
        return division != null && shippingPolicy.allowedCity(division.city().name()) != null
                && shippingPolicy.allowedAdministrativeCity(profile.getProvince()) == division.city();
    }
}
