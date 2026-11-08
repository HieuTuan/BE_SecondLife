package com.secondlife.secondlife.service.impl;

import com.secondlife.secondlife.dto.response.InspectionChecklistPricingResponse;
import com.secondlife.secondlife.entity.InspectionChecklistTemplate;
import com.secondlife.secondlife.entity.InspectionFeeProposal;
import com.secondlife.secondlife.entity.User;
import com.secondlife.secondlife.enums.AccountStatus;
import com.secondlife.secondlife.enums.InspectionChecklistPriceStatus;
import com.secondlife.secondlife.enums.InspectionChecklistStatus;
import com.secondlife.secondlife.enums.InspectionComplexityLevel;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import com.secondlife.secondlife.repository.InspectionFeeProposalRepository;
import com.secondlife.secondlife.repository.UserRepository;
import com.secondlife.secondlife.service.InspectionChecklistPricingService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionChecklistPricingServiceImpl implements InspectionChecklistPricingService {
    private final InspectionFeeProposalRepository fees;
    private final UserRepository users;

    @Override
    public Map<UUID, InspectionChecklistPricingResponse> getPrices(List<InspectionChecklistTemplate> templates) {
        if (templates.isEmpty()) return Map.of();
        Set<UUID> centerIds = templates.stream().map(InspectionChecklistTemplate::getCreatedBy)
                .filter(Objects::nonNull).map(User::getId).collect(Collectors.toSet());
        Set<InspectionComplexityLevel> levels = templates.stream().map(InspectionChecklistTemplate::getComplexityLevel)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, User> centers = centerIds.isEmpty() ? Map.of() : users.findAllWithRolesByIdIn(centerIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<FeeKey, InspectionFeeProposal> approved = new HashMap<>();
        Map<FeeKey, InspectionFeeProposal> pending = new HashMap<>();
        if (!centerIds.isEmpty() && !levels.isEmpty()) {
            for (InspectionFeeProposal proposal : fees.findLatestLevelFeesForCenters(centerIds, levels)) {
                FeeKey key = new FeeKey(proposal.getProposedBy().getId(), proposal.getComplexityLevel());
                (proposal.getStatus() == InspectionFeeProposalStatus.APPROVED ? approved : pending).put(key, proposal);
            }
        }
        Map<UUID, InspectionChecklistPricingResponse> result = new HashMap<>();
        for (InspectionChecklistTemplate template : templates) {
            UUID centerId = template.getCreatedBy() == null ? null : template.getCreatedBy().getId();
            User center = centerId == null ? null : centers.get(centerId);
            FeeKey key = new FeeKey(centerId, template.getComplexityLevel());
            result.put(template.getId(), price(template, centerId, center, approved.get(key), pending.get(key)));
        }
        return result;
    }

    private InspectionChecklistPricingResponse price(InspectionChecklistTemplate template, UUID centerId, User center,
            InspectionFeeProposal approved, InspectionFeeProposal pending) {
        InspectionChecklistPriceStatus status;
        String reason;
        if (template.getCatalogItem() == null || template.getComplexityLevel() == null
                || template.getApprovalStatus() == InspectionChecklistStatus.LEGACY) {
            status = InspectionChecklistPriceStatus.LEGACY;
            reason = "ITEM_NOT_CLASSIFIED";
            approved = null;
            pending = null;
        } else if (center == null || center.getAccountStatus() != AccountStatus.ACTIVE || !center.hasRole("INSPECTION_CENTER")) {
            status = InspectionChecklistPriceStatus.CENTER_UNAVAILABLE;
            reason = "CENTER_UNAVAILABLE";
        } else if (approved == null) {
            status = pending == null ? InspectionChecklistPriceStatus.NOT_CONFIGURED : InspectionChecklistPriceStatus.PENDING_APPROVAL;
            reason = "NO_APPROVED_LEVEL_FEE";
        } else {
            status = InspectionChecklistPriceStatus.APPROVED;
            if (!template.getCatalogItem().isSelectable()) {
                reason = "ITEM_NOT_SELECTABLE";
            } else if (template.getApprovalStatus() != InspectionChecklistStatus.APPROVED || !template.isActive()) {
                reason = "CHECKLIST_NOT_EFFECTIVE";
            } else {
                reason = null;
            }
        }
        return new InspectionChecklistPricingResponse(centerId, centerName(center), template.getComplexityLevel(),
                approved == null ? null : approved.getBaseInspectionFee(), approved == null ? null : approved.getId(),
                approved == null ? null : approved.getReviewedAt(), pending == null ? null : pending.getBaseInspectionFee(),
                pending == null ? null : pending.getId(), status, reason == null, reason);
    }

    private String centerName(User center) {
        if (center == null) return null;
        if (center.getProfile() != null && center.getProfile().getFullName() != null
                && !center.getProfile().getFullName().isBlank()) {
            return center.getProfile().getFullName();
        }
        return center.getEmail();
    }

    private record FeeKey(UUID centerId, InspectionComplexityLevel level) {
    }
}
