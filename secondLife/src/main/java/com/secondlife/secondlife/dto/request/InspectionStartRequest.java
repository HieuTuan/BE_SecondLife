package com.secondlife.secondlife.dto.request;

public record InspectionStartRequest(
        String checkInCode,
        String orderCode,
        String barcodeInput
) {
}
