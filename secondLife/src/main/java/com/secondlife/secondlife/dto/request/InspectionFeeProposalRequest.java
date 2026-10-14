package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.dto.response.InspectionTravelTierDto;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionFeeProposalRequest {

    private UUID categoryId;

    @NotNull(message = "baseInspectionFee is required")
    private BigDecimal baseInspectionFee;

    private List<InspectionTravelTierDto> tiers;
}
