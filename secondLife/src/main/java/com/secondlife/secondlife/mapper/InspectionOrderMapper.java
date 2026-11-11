package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.request.ShippingAddress;
import com.secondlife.secondlife.dto.response.InspectionOrderResponse;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.User;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class InspectionOrderMapper {
    private final Delegate delegate;

    public InspectionOrderMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public InspectionOrderResponse toResponse(InspectionOrder order) {
        return toResponse(order, null);
    }

    public InspectionOrderResponse toResponse(InspectionOrder order, BigDecimal centerPayoutAmount) {
        return toResponse(order, null, null, null, centerPayoutAmount);
    }

    public InspectionOrderResponse toResponse(InspectionOrder order, ShippingAddress sellerContact,
                                              ShippingAddress buyerContact, ShippingAddress centerContact,
                                              BigDecimal centerPayoutAmount) {
        return delegate.toResponse(order, sellerContact, buyerContact, centerContact, centerPayoutAmount);
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
        @Mapping(target = "buyerId", source = "order.buyer.id")
        @Mapping(target = "buyerName", expression = "java(contactName(buyerContact, order.getBuyer()))")
        @Mapping(target = "buyerEmail", source = "order.buyer.email")
        @Mapping(target = "buyerPhone", source = "buyerContact.phone")
        @Mapping(target = "buyerAddress", source = "buyerContact.address")
        @Mapping(target = "sellerId", source = "order.seller.id")
        @Mapping(target = "sellerName", expression = "java(contactName(sellerContact, order.getSeller()))")
        @Mapping(target = "sellerEmail", source = "order.seller.email")
        @Mapping(target = "sellerPhone", source = "sellerContact.phone")
        @Mapping(target = "sellerAddress", source = "sellerContact.address")
        @Mapping(target = "inspectorId", source = "order.inspector.id")
        @Mapping(target = "inspectionCenterId", source = "order.inspectionCenter.id")
        @Mapping(target = "inspectionCenterName", source = "centerContact.name")
        @Mapping(target = "inspectionCenterAddress", source = "centerContact.address")
        @Mapping(target = "inspectionCenterPhone", source = "centerContact.phone")
        @Mapping(target = "checklistTemplateId", source = "order.checklistTemplate.id")
        @Mapping(target = "feeProposalId", source = "order.feeProposal.id")
        @Mapping(target = "inspectorName", expression = "java(inspectorName(order.getInspector()))")
        @Mapping(target = "postId", source = "order.post.id")
        @Mapping(target = "postTitle", source = "order.post.title")
        @Mapping(target = "postImageUrls", source = "order.post.imageUrls")
        @Mapping(target = "postPrice", source = "order.post.price")
        @Mapping(target = "linkedOrderId", source = "order.order.id")
        @Mapping(target = "negotiationId", source = "order.negotiation.id")
        @Mapping(target = "inspectionScope", expression = "java(order.getInspectionScope() != null ? order.getInspectionScope().name() : null)")
        @Mapping(target = "appealStatus", expression = "java(order.getAppealStatus() != null ? order.getAppealStatus().name() : null)")
        @Mapping(target = "initialDepositPaid", expression = "java(resolveInitialDepositPaid(order))")
        @Mapping(target = "finalPaymentRequired", expression = "java(resolveFinalPaymentRequired(order))")
        @Mapping(target = "videoCleanedMessage", expression = "java(cleanedMessage(order.getVideoCleanupStatus()))")
        @Mapping(target = "centerPayoutAmount", source = "centerPayoutAmount")
        InspectionOrderResponse toResponse(InspectionOrder order, ShippingAddress sellerContact,
                                           ShippingAddress buyerContact, ShippingAddress centerContact,
                                           BigDecimal centerPayoutAmount);

        default String contactName(ShippingAddress contact, User user) {
            return contact != null && contact.name() != null && !contact.name().isBlank()
                    ? contact.name() : partyName(user);
        }

        default BigDecimal resolveInitialDepositPaid(InspectionOrder order) {
            if (order == null) return BigDecimal.ZERO;
            BigDecimal feeShare = order.getBuyerFeeShare() != null ? order.getBuyerFeeShare() : BigDecimal.ZERO;
            BigDecimal commitment = order.getBuyerCommitmentDeposit() != null ? order.getBuyerCommitmentDeposit() : BigDecimal.ZERO;
            return feeShare.add(commitment);
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
