package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

@Component
public class InspectionOrderMapper {
    private final Delegate delegate;

    public InspectionOrderMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public InspectionOrderResponse toResponse(InspectionOrder order) {
        return delegate.toResponse(order);
    }

    protected String partyName(User user) {
        return delegate.partyName(user);
    }

    protected static String inspectorName(User user) {
        if (user == null) return null;
        return user.getProfile() != null && user.getProfile().getFullName() != null
                ? user.getProfile().getFullName() : user.getEmail();
    }

    protected String cleanedMessage(String cleanupStatus) {
        return delegate.cleanedMessage(cleanupStatus);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "buyerId", source = "buyer.id")
        @Mapping(target = "buyerName", expression = "java(partyName(order.getBuyer()))")
        @Mapping(target = "buyerEmail", source = "buyer.email")
        @Mapping(target = "sellerId", source = "seller.id")
        @Mapping(target = "sellerName", expression = "java(partyName(order.getSeller()))")
        @Mapping(target = "sellerEmail", source = "seller.email")
        @Mapping(target = "inspectorId", source = "inspector.id")
        @Mapping(target = "inspectorName", expression = "java(inspectorName(order.getInspector()))")
        @Mapping(target = "postId", source = "post.id")
        @Mapping(target = "postTitle", source = "post.title")
        @Mapping(target = "postImageUrls", source = "post.imageUrls")
        @Mapping(target = "postPrice", source = "post.price")
        @Mapping(target = "initialDepositPaid", expression = "java(resolveInitialDepositPaid(order))")
        @Mapping(target = "finalPaymentRequired", expression = "java(resolveFinalPaymentRequired(order))")
        @Mapping(target = "videoCleanedMessage", expression = "java(cleanedMessage(order.getVideoCleanupStatus()))")
        InspectionOrderResponse toResponse(InspectionOrder order);

        default java.math.BigDecimal resolveInitialDepositPaid(InspectionOrder order) {
            if (order == null) return java.math.BigDecimal.ZERO;
            return order.getBuyerFeeShare() != null ? order.getBuyerFeeShare() : java.math.BigDecimal.ZERO;
        }

        default boolean resolveFinalPaymentRequired(InspectionOrder order) {
            return true;
        }

        default String partyName(User user) {
            if (user == null) return null;
            return user.getProfile() != null ? user.getProfile().getFullName() : user.getEmail();
        }

        default String inspectorName(User user) {
            return InspectionOrderMapper.inspectorName(user);
        }

        @Named("cleanedMessage")
        default String cleanedMessage(String cleanupStatus) {
            return "CLEANED".equals(cleanupStatus)
                    ? "Video bằng chứng đã được hệ thống tự động dọn dẹp an toàn sau 48h hoàn tất giao dịch theo Chính sách Bảo mật Dữ liệu."
                    : null;
        }
    }
}
