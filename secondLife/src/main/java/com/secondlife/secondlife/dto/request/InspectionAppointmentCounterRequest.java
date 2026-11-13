package com.secondlife.secondlife.dto.request;

import com.secondlife.secondlife.enums.InspectionAppointmentShift;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record InspectionAppointmentCounterRequest(
        @NotNull @Future Instant counterScheduledAt,
        @NotNull InspectionAppointmentShift counterShift,
        @NotBlank @Size(max = 500) String counterNote
) {
}
