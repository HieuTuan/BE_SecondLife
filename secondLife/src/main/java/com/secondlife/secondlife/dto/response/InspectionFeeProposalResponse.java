package com.secondlife.secondlife.dto.response;

import com.secondlife.secondlife.enums.InspectionFeeProposalStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InspectionFeeProposalResponse {
    private UUID id;
    private UUID categoryId;
    private String categoryName;
    private BigDecimal baseInspectionFee;
    private String tiersJson;
    private UUID proposedById;
    private String proposedByName;
    private String proposedByEmail;
    private UUID reviewedById;
    private String reviewedByName;
    private InspectionFeeProposalStatus status;
    private String rejectReason;
    private Instant createdAt;
    private Instant reviewedAt;
}
