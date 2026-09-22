package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.dto.response.AdminSellerVerificationDetailResponse;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.dto.response.VerificationEventResponse;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.SellerVerificationEvent;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SellerVerificationMapper {

    public SellerVerificationResponse toResponse(SellerVerification sv) {
        if (sv == null) return null;
        return SellerVerificationResponse.from(sv);
    }

    public AdminSellerVerificationDetailResponse toDetailResponse(SellerVerification sv, List<SellerVerificationEvent> events) {
        if (sv == null) return null;
        List<VerificationEventResponse> eventResponses = (events != null)
                ? events.stream().map(VerificationEventResponse::from).toList()
                : List.of();
        return AdminSellerVerificationDetailResponse.from(sv, eventResponses);
    }
}
