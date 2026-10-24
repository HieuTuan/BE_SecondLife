package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.dto.request.ReportRequests.ResolutionAction;
import com.secondlife.secondlife.dto.request.ReportRequests.Status;
import com.secondlife.secondlife.dto.request.ReportRequests.TargetType;
import com.secondlife.secondlife.entity.UserReport;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserReportReturnMapperTest {
    private final ObjectMapper json = new ObjectMapper();
    private final UserReportMapper mapper = MapperTestSupport.mapper(UserReportMapper.class);

    @Test
    void reportResponseExposesApprovalAndRefundAuditWithoutLosingEvidence() {
        UUID orderId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        UUID shipmentId = UUID.randomUUID();
        UUID refundedBy = UUID.randomUUID();
        UserReport report = new UserReport();
        report.setOrderId(orderId);
        report.setReviewerId(reviewerId);
        report.setStatus(Status.CONFIRMED);
        report.setResolutionAction(ResolutionAction.RETURN_AND_REFUND);
        report.setReturnShipmentId(shipmentId);
        report.setReturnApprovedAt(Instant.parse("2026-10-20T10:00:00Z"));
        report.setRefundedAt(Instant.parse("2026-10-21T11:00:00Z"));
        report.setRefundedBy(refundedBy);
        report.setRefundAmount(new BigDecimal("1500000.00"));
        report.setEvidenceUrls("[\"private/evidence.jpg\"]");

        var response = mapper.toResponse(report, json);

        assertEquals(orderId, response.orderId());
        assertEquals(TargetType.ORDER, response.targetType());
        assertEquals(reviewerId, response.reviewerId());
        assertEquals(ResolutionAction.RETURN_AND_REFUND, response.resolutionAction());
        assertEquals(shipmentId, response.returnShipmentId());
        assertEquals(Instant.parse("2026-10-20T10:00:00Z"), response.returnApprovedAt());
        assertEquals(Instant.parse("2026-10-21T11:00:00Z"), response.refundedAt());
        assertEquals(refundedBy, response.refundedBy());
        assertEquals(new BigDecimal("1500000.00"), response.refundAmount());
        assertEquals(List.of("private/evidence.jpg"), response.evidenceUrls());
        var payload = json.readTree(json.writeValueAsString(response));
        assertEquals("RETURN_AND_REFUND", payload.get("resolutionAction").asString());
        assertEquals(shipmentId.toString(), payload.get("returnShipmentId").asString());
        assertEquals(refundedBy.toString(), payload.get("refundedBy").asString());
    }

    @Test
    void historicalListingReportKeepsNoReturnActionAndNullAudit() {
        UserReport report = new UserReport();
        report.setEvidenceUrls("[]");

        var response = mapper.toResponse(report, json);

        assertEquals(TargetType.LISTING, response.targetType());
        assertEquals(ResolutionAction.NONE, response.resolutionAction());
        assertNull(response.returnShipmentId());
        assertNull(response.returnApprovedAt());
        assertNull(response.refundedAt());
        assertNull(response.refundedBy());
        assertNull(response.refundAmount());
    }
}
