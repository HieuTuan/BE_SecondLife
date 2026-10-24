package com.secondlife.secondlife.service;

import com.secondlife.secondlife.dto.request.InspectionFeeProposalRequest;
import com.secondlife.secondlife.dto.request.InspectionQuoteRequest;
import com.secondlife.secondlife.dto.response.InspectionFeeProposalResponse;
import com.secondlife.secondlife.dto.response.InspectionQuoteResponse;
import com.secondlife.secondlife.dto.response.InspectionTravelTierDto;
import com.secondlife.secondlife.entity.Post;
import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface InspectionPricingService {
    InspectionQuoteResponse calculateQuote(InspectionQuoteRequest request);
    InspectionQuoteResponse quote(InspectionQuoteRequest request);
    BigDecimal resolveBaseInspectionFee(Post post);
    List<InspectionTravelTierDto> getActiveTiers();
    List<InspectionTravelTierDto> updateTiers(List<InspectionTravelTierDto> dtoList);
    InspectionFeeProposalResponse proposeFee(UUID proposedById, InspectionFeeProposalRequest request);
    Page<InspectionFeeProposalResponse> getProposals(InspectionFeeProposalStatus status, Pageable pageable);
    InspectionFeeProposalResponse reviewProposal(UUID proposalId, UUID reviewerId, boolean approved, String reason);
}
