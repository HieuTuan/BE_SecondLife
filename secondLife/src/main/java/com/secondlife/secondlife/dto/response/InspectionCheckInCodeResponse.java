package com.secondlife.secondlife.dto.response;

import java.time.Instant;
import java.util.UUID;

public record InspectionCheckInCodeResponse(UUID code, Instant appointmentScheduledAt) {
}
