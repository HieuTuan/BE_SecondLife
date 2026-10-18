package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.AdminSellerVerificationDetailResponse;
import com.secondlife.secondlife.dto.response.SellerVerificationResponse;
import com.secondlife.secondlife.dto.response.VerificationEventResponse;
import com.secondlife.secondlife.entity.SellerVerification;
import com.secondlife.secondlife.entity.SellerVerificationEvent;
import org.mapstruct.IterableMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValueMappingStrategy;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SellerVerificationMapper {
    private final Delegate delegate;

    public SellerVerificationMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    protected SellerVerificationResponse mapSeller(SellerVerification verification, String shopName, String shopEmail, String phone, String pickupAddress, List<VerificationEventResponse> eventHistory) {
        return delegate.mapSeller(verification, shopName, shopEmail, phone, pickupAddress, eventHistory);
    }

    protected AdminSellerVerificationDetailResponse mapDetail(SellerVerification verification, String shopName, String shopEmail, String phone, String pickupAddress, List<VerificationEventResponse> eventHistory) {
        return delegate.mapDetail(verification, shopName, shopEmail, phone, pickupAddress, eventHistory);
    }

    protected List<VerificationEventResponse> toEvents(List<SellerVerificationEvent> events) {
        return delegate.toEvents(events);
    }

    public SellerVerificationResponse toResponse(SellerVerification verification, String shopName, String shopEmail, String phone, String pickupAddress, List<SellerVerificationEvent> events) {
        return delegate.toResponse(verification, shopName, shopEmail, phone, pickupAddress, events);
    }

    public SellerVerificationResponse toResponse(SellerVerification verification) {
        return delegate.toResponse(verification);
    }

    public SellerVerificationResponse toSellerResponse(SellerVerification verification, String shopName, String shopEmail, String phone, String pickupAddress, List<VerificationEventResponse> eventHistory) {
        return delegate.toSellerResponse(verification, shopName, shopEmail, phone, pickupAddress, eventHistory);
    }

    public SellerVerificationResponse toSellerResponse(SellerVerification verification) {
        return delegate.toSellerResponse(verification);
    }

    public AdminSellerVerificationDetailResponse toDetailResponse(SellerVerification verification, String shopName, String shopEmail, String phone, String pickupAddress, List<SellerVerificationEvent> events) {
        return delegate.toDetailResponse(verification, shopName, shopEmail, phone, pickupAddress, events);
    }

    public AdminSellerVerificationDetailResponse toDetailResponse(SellerVerification verification, List<SellerVerificationEvent> events) {
        return delegate.toDetailResponse(verification, events);
    }

    public AdminSellerVerificationDetailResponse toAdminDetailResponse(SellerVerification verification, String shopName, String shopEmail, String phone, String pickupAddress, List<VerificationEventResponse> eventHistory) {
        return delegate.toAdminDetailResponse(verification, shopName, shopEmail, phone, pickupAddress, eventHistory);
    }

    public AdminSellerVerificationDetailResponse toAdminDetailResponse(SellerVerification verification, List<VerificationEventResponse> eventHistory) {
        return delegate.toAdminDetailResponse(verification, eventHistory);
    }

    protected String documentNumber(SellerVerification verification) {
        return delegate.documentNumber(verification);
    }

    protected String shopEmail(SellerVerification verification, String email) {
        return delegate.shopEmail(verification, email);
    }

    protected String phone(SellerVerification verification, String phone) {
        return delegate.phone(verification, phone);
    }

    protected List<VerificationEventResponse> eventsOrEmpty(List<VerificationEventResponse> events) {
        return delegate.eventsOrEmpty(events);
    }

    @Mapper(config = MapStructConfiguration.class, uses = VerificationEventMapper.class)
    public interface Delegate {
        @Mapping(target = "userId", source = "verification.user.id")
        @Mapping(target = "userEmail", source = "verification.user.email")
        @Mapping(target = "userFullName", source = "verification.user.profile.fullName")
        @Mapping(target = "shopEmail", expression = "java(shopEmail(verification, shopEmail))")
        @Mapping(target = "phone", expression = "java(phone(verification, phone))")
        @Mapping(target = "documentNumber", expression = "java(documentNumber(verification))")
        @Mapping(target = "reasonCodeDescription", source = "verification.reasonCode.description")
        @Mapping(target = "reviewedBy", source = "verification.reviewedBy.id")
        @Mapping(target = "eventHistory", expression = "java(eventsOrEmpty(eventHistory))")
        SellerVerificationResponse mapSeller(SellerVerification verification, String shopName, String shopEmail, String phone, String pickupAddress, List<VerificationEventResponse> eventHistory);

        @Mapping(target = "userId", source = "verification.user.id")
        @Mapping(target = "userEmail", source = "verification.user.email")
        @Mapping(target = "userFullName", source = "verification.user.profile.fullName")
        @Mapping(target = "shopEmail", expression = "java(shopEmail(verification, shopEmail))")
        @Mapping(target = "phone", expression = "java(phone(verification, phone))")
        @Mapping(target = "documentNumber", expression = "java(documentNumber(verification))")
        @Mapping(target = "reasonCodeDescription", source = "verification.reasonCode.description")
        @Mapping(target = "reviewedBy", source = "verification.reviewedBy.id")
        @Mapping(target = "eventHistory", source = "eventHistory")
        AdminSellerVerificationDetailResponse mapDetail(SellerVerification verification, String shopName, String shopEmail, String phone, String pickupAddress, List<VerificationEventResponse> eventHistory);

        @IterableMapping(nullValueMappingStrategy = NullValueMappingStrategy.RETURN_DEFAULT)
        List<VerificationEventResponse> toEvents(List<SellerVerificationEvent> events);

        default SellerVerificationResponse toResponse(SellerVerification verification, String shopName, String shopEmail,
                String phone, String pickupAddress, List<SellerVerificationEvent> events) {
            return toSellerResponse(verification, shopName, shopEmail, phone, pickupAddress, toEvents(events));
        }

        default SellerVerificationResponse toResponse(SellerVerification verification) {
            return toSellerResponse(verification);
        }

        default SellerVerificationResponse toSellerResponse(SellerVerification verification, String shopName, String shopEmail,
                String phone, String pickupAddress, List<VerificationEventResponse> eventHistory) {
            return verification == null ? null : mapSeller(verification, shopName, shopEmail, phone, pickupAddress, eventHistory);
        }

        default SellerVerificationResponse toSellerResponse(SellerVerification verification) {
            return toSellerResponse(verification, null, null, null, null, List.of());
        }

        default AdminSellerVerificationDetailResponse toDetailResponse(SellerVerification verification, String shopName, String shopEmail,
                String phone, String pickupAddress, List<SellerVerificationEvent> events) {
            return toAdminDetailResponse(verification, shopName, shopEmail, phone, pickupAddress, toEvents(events));
        }

        default AdminSellerVerificationDetailResponse toDetailResponse(SellerVerification verification, List<SellerVerificationEvent> events) {
            return toDetailResponse(verification, null, null, null, null, events);
        }

        default AdminSellerVerificationDetailResponse toAdminDetailResponse(SellerVerification verification, String shopName, String shopEmail,
                String phone, String pickupAddress, List<VerificationEventResponse> eventHistory) {
            return verification == null ? null : mapDetail(verification, shopName, shopEmail, phone, pickupAddress, eventHistory);
        }

        default AdminSellerVerificationDetailResponse toAdminDetailResponse(SellerVerification verification, List<VerificationEventResponse> eventHistory) {
            return toAdminDetailResponse(verification, null, null, null, null, eventHistory);
        }

        default String documentNumber(SellerVerification verification) {
            return verification.getDocumentNumber() != null ? verification.getDocumentNumber() : verification.getDocumentNumberMasked();
        }

        default String shopEmail(SellerVerification verification, String email) {
            return email != null && !email.isBlank() ? email : verification.getUser() == null ? null : verification.getUser().getEmail();
        }

        default String phone(SellerVerification verification, String phone) {
            if (phone != null && !phone.isBlank()) return phone;
            return verification.getUser() == null || verification.getUser().getProfile() == null
                    ? null : verification.getUser().getProfile().getPhone();
        }

        @Named("eventsOrEmpty")
        default List<VerificationEventResponse> eventsOrEmpty(List<VerificationEventResponse> events) {
            return events == null ? List.of() : events;
        }
    }
}
