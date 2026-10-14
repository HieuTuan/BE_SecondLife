package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InspectionAppointmentRequest {

    @NotNull(message = "scheduledAt is required")
    @Future(message = "Lịch hẹn kiểm định phải ở thời điểm tương lai")
    private Instant scheduledAt;

    private String note;

    public Instant getAppointmentTime() {
        return scheduledAt;
    }
}
