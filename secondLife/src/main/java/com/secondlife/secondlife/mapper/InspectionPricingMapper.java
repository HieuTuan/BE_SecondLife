package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.InspectionFeeProposalResponse;
import com.secondlife.secondlife.dto.response.InspectionTravelTierDto;
import com.secondlife.secondlife.entity.InspectionFeeProposal;
import com.secondlife.secondlife.entity.InspectionTravelTier;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.stereotype.Component;

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

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        @Mapping(target = "proposedById", source = "proposal.proposedBy.id")
        @Mapping(target = "proposedByName", source = "proposal.proposedBy.email")
        @Mapping(target = "reviewedById", source = "proposal.reviewedBy.id")
        @Mapping(target = "reviewedByName", source = "proposal.reviewedBy.email")
        @Mapping(target = "tiers", source = "tiers")
        @Mapping(target = "categoryName", ignore = true)
        @Mapping(target = "tiersJson", ignore = true)
        @Mapping(target = "proposedByEmail", ignore = true)
        InspectionFeeProposalResponse toProposalResponse(InspectionFeeProposal proposal, List<InspectionTravelTierDto> tiers);

        @Mapping(target = "distanceChargedInTier", ignore = true)
        @Mapping(target = "feeInTier", ignore = true)
        InspectionTravelTierDto toTierResponse(InspectionTravelTier tier);
    }
}
