package com.secondlife.secondlife.dto.reputation;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public final class ReportRequests {
    private ReportRequests() { }
    public enum ReasonCode { MISLEADING_DESCRIPTION, COUNTERFEIT, FRAUD, ITEM_NOT_RECEIVED, OTHER, LATE_HANDOVER, SELLER_CANCELLATION, RETURN_NOT_AS_DESCRIBED }
    public enum Status { OPEN, IN_REVIEW, CONFIRMED, DISMISSED }
    public enum Decision { CONFIRMED, DISMISSED }
    public enum ResponsibleParty { SELLER, BUYER, CARRIER, NONE }
    public record Create(@NotNull UUID requestId,
            @Schema(description = "Provide exactly one of postId or orderId") UUID postId,
            @Schema(description = "Only this order's buyer may report it") UUID orderId,
            @NotNull ReasonCode reasonCode, @NotBlank @Size(max=2000) String description,
            @Schema(description="Private evidenceUrl values returned by POST /api/v1/reports/evidence; public CDN links are rejected")
            List<@NotBlank @Size(max=2048) String> evidenceUrls) {
        public Create { if (evidenceUrls == null) evidenceUrls = List.of(); }
        @AssertTrue(message = "Provide exactly one of postId or orderId")
        @Schema(hidden=true)
        public boolean isTargetValid() { return (postId == null) != (orderId == null); }
    }
    public record Resolve(@NotNull Decision decision,
            @Schema(description = "Required for CONFIRMED; only SELLER affects seller reputation") ResponsibleParty responsibleParty,
            @NotBlank @Size(max=2000) String reason) { }
    public record LiftRestriction(@NotBlank @Size(max=2000) String reason) { }
}
