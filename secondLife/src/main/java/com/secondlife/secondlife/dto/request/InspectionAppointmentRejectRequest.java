package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InspectionAppointmentRejectRequest(
        @NotBlank @Size(max = 500) String reason
) {
}
