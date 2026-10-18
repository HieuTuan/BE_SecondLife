package com.secondlife.secondlife.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record InspectionAppointmentRequest(
    @NotNull(message = "scheduledAt is required")
    @Future(message = "Lịch hẹn kiểm định phải ở thời điểm tương lai")
    Instant scheduledAt,
    String note
) {
    @JsonProperty("appointmentTime")
    public Instant appointmentTime() {
        return scheduledAt;
    }
}
