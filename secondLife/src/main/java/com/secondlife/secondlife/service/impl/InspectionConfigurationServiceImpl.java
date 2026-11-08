package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.dto.response.InspectionConfigurationResponse;
import com.secondlife.secondlife.entity.InspectionChecklistTemplate;
import com.secondlife.secondlife.entity.InspectionFeeProposal;
import com.secondlife.secondlife.entity.InspectionOrder;
import com.secondlife.secondlife.entity.Item;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.InspectionChecklistStatus;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.exception.ConflictException;
import com.secondlife.secondlife.exception.NotFoundException;
import com.secondlife.secondlife.mapper.InspectionChecklistMapper;
import com.secondlife.secondlife.mapper.InspectionPricingMapper;
import com.secondlife.secondlife.repository.InspectionChecklistTemplateRepository;
import com.secondlife.secondlife.repository.InspectionFeeProposalRepository;
import com.secondlife.secondlife.repository.ItemRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionConfigurationService;
import com.secondlife.secondlife.service.InspectionGovernanceAccessService;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InspectionConfigurationServiceImpl implements InspectionConfigurationService {
    private final ItemRepository items;
    private final UserRepository users;
    private final InspectionChecklistTemplateRepository templates;
    private final InspectionFeeProposalRepository fees;
    private final InspectionGovernanceAccessService access;
    private final InspectionChecklistMapper checklistMapper;
    private final InspectionPricingMapper pricingMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.inspection.platform-fee-share-rate:0.00}")
    private BigDecimal platformFeeRate = BigDecimal.ZERO;

    @Override
    @Transactional(readOnly = true)
    public InspectionConfigurationResponse resolve(Post post, UUID inspectionCenterId) {
        if (post == null || post.getItemId() == null) {
            throw new ConflictException("Cần xác định loại sản phẩm cụ thể trước khi báo phí kiểm định");
        }
        Item item = items.findById(post.getItemId()).orElseThrow(() -> new NotFoundException("Item not found"));
        if (!item.isSelectable()) {
            throw new ConflictException("Loại sản phẩm đã được tách; người bán cần chọn loại cụ thể trước khi kiểm định");
        }
        if (post.getCategoryId() == null || !post.getCategoryId().equals(item.getCategory().getId())) {
            throw new BadRequestException("Loại sản phẩm không thuộc danh mục của bài đăng");
        }
        InspectionChecklistTemplate template = templates
                .findByCatalogItem_IdAndApprovalStatusAndActiveTrue(
                        item.getId(), InspectionChecklistStatus.APPROVED)
                .orElseThrow(() -> new ConflictException("Loại sản phẩm chưa có checklist và cấp độ phức tạp được Admin duyệt"));
        UUID centerId = inspectionCenterId;
        if (centerId == null) {
            List<UUID> centers = users.findActiveInspectionCenterUserIds();
            UUID proposerId = template.getCreatedBy() == null ? null : template.getCreatedBy().getId();
            if (proposerId != null && centers.contains(proposerId)) {
                centerId = proposerId;
            } else if (centers.size() == 1) {
                centerId = centers.getFirst();
            } else {
                throw new ConflictException("Cần chọn trung tâm kiểm định cụ thể trước khi báo giá");
            }
        }
        User center = access.requireCenter(centerId);
        InspectionFeeProposal fee = fees
                .findFirstByProposedBy_IdAndComplexityLevelAndStatusOrderByReviewedAtDescIdDesc(
                        centerId, template.getComplexityLevel(), InspectionFeeProposalStatus.APPROVED)
                .orElseThrow(() -> new ConflictException("Trung tâm chưa có giá được duyệt cho cấp " + template.getComplexityLevel()));
        InspectionChecklistResponse checklist = checklistMapper.toResponse(template);
        if (platformFeeRate.signum() < 0 || platformFeeRate.compareTo(BigDecimal.ONE) > 0 || platformFeeRate.scale() > 6) {
            throw new ConflictException("Tỷ lệ phí nền tảng kiểm định phải từ 0 đến 1 và tối đa 6 chữ số thập phân");
        }
        return pricingMapper.toConfigurationResponse(center, template, fee, checklist, platformFeeRate);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void snapshot(InspectionOrder order, InspectionConfigurationResponse configuration) {
        if (configuration == null) throw new IllegalStateException("Inspection quote must include its approved configuration");
        if (order.getChecklistSnapshotJson() != null) {
            throw new ConflictException("Không được thay checklist hoặc cấp độ của đơn đã chốt");
        }
        order.setInspectionCenter(users.getReferenceById(configuration.inspectionCenterId()));
        order.setChecklistTemplate(templates.getReferenceById(configuration.checklistTemplateId()));
        order.setFeeProposal(fees.getReferenceById(configuration.feeProposalId()));
        order.setComplexityLevel(configuration.complexityLevel());
        order.setPlatformFeeRateSnapshot(configuration.platformFeeRate());
        order.setChecklistSnapshotJson(objectMapper.writeValueAsString(configuration.checklist()));
    }
}
