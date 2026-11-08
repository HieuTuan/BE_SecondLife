package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.dto.response.InspectionConfigurationResponse;
import com.secondlife.secondlife.dto.response.InspectionFeeProposalResponse;
import com.secondlife.secondlife.dto.response.InspectionTravelTierDto;
import com.secondlife.secondlife.entity.InspectionChecklistTemplate;
import com.secondlife.secondlife.entity.InspectionFeeProposal;
import com.secondlife.secondlife.entity.InspectionTravelTier;
import com.secondlife.secondlife.entity.User;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class InspectionPricingMapper {
    private final Delegate delegate;

    public InspectionPricingMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public InspectionFeeProposalResponse toProposalResponse(InspectionFeeProposal proposal, List<InspectionTravelTierDto> tiers) {
        return delegate.toProposalResponse(proposal, tiers);
    }

    public InspectionTravelTierDto toTierResponse(InspectionTravelTier tier) {
        return delegate.toTierResponse(tier);
    }

    public InspectionConfigurationResponse toConfigurationResponse(User center, InspectionChecklistTemplate template,
            InspectionFeeProposal fee, InspectionChecklistResponse checklist, BigDecimal platformFeeRate) {
        return delegate.toConfigurationResponse(center, template, fee, checklist, platformFeeRate);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "inspectionCenterId", source = "center.id")
        @Mapping(target = "checklistTemplateId", source = "template.id")
        @Mapping(target = "complexityLevel", source = "template.complexityLevel")
        @Mapping(target = "feeProposalId", source = "fee.id")
        @Mapping(target = "inspectionFee", source = "fee.baseInspectionFee")
        @Mapping(target = "checklist", source = "checklist")
        @Mapping(target = "platformFeeRate", source = "platformFeeRate")
        InspectionConfigurationResponse toConfigurationResponse(User center, InspectionChecklistTemplate template,
                InspectionFeeProposal fee, InspectionChecklistResponse checklist, BigDecimal platformFeeRate);

        @Mapping(target = "proposedById", source = "proposal.proposedBy.id")
        @Mapping(target = "proposedByName", source = "proposal.proposedBy.email")
        @Mapping(target = "reviewedById", source = "proposal.reviewedBy.id")
        @Mapping(target = "reviewedByName", source = "proposal.reviewedBy.email")
        @Mapping(target = "tiers", source = "tiers")
        @Mapping(target = "categoryName", source = "proposal.category.name")
        @Mapping(target = "tiersJson", source = "proposal.tiersJson")
        @Mapping(target = "proposedByEmail", source = "proposal.proposedBy.email")
        InspectionFeeProposalResponse toProposalResponse(InspectionFeeProposal proposal, List<InspectionTravelTierDto> tiers);

        @Mapping(target = "distanceChargedInTier", ignore = true)
        @Mapping(target = "feeInTier", ignore = true)
        InspectionTravelTierDto toTierResponse(InspectionTravelTier tier);
    }
}
