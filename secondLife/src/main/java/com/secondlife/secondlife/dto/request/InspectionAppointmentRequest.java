package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.secondlife.secondlife.enums.InspectionAppointmentShift;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record InspectionAppointmentRequest(
    @NotNull(message = "scheduledAt is required")
    @Future(message = "Lịch hẹn kiểm định phải ở thời điểm tương lai")
    Instant scheduledAt,
    @Schema(description = "Vietnam local shift. Inferred from scheduledAt when omitted.")
    InspectionAppointmentShift shift,
    @Size(max = 500)
    String note
) {
    @JsonProperty("appointmentTime")
    public Instant appointmentTime() {
        return scheduledAt;
    }
}
