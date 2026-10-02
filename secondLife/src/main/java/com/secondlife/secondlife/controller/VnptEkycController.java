package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.ekyc.vnpt.VnptResults;
import com.secondlife.secondlife.exception.BadRequestException;
import com.secondlife.secondlife.service.ekyc.vnpt.VnptEkycOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/ekyc")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.ekyc.provider", havingValue = "VNPT")
@Tag(name = "VNPT eKYC", description = "Direct VNPT eKYC verification for authenticated buyers")
public class VnptEkycController {
    private final VnptEkycOrchestrator orchestrator;

    @PostMapping(value = "/verify", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('SELLER_VERIFICATION_SUBMIT')")
    @Operation(summary = "Verify front/back identity images and selfie with VNPT")
    public ResponseEntity<ApiResponse<VnptResults.Verification>> verify(
            @RequestParam("frontImage") MultipartFile frontImage,
            @RequestParam("backImage") MultipartFile backImage,
            @RequestParam("selfieImage") MultipartFile selfieImage,
            @RequestParam("clientSession") String clientSession,
            @RequestParam("token") String token) {
        try {
            return ResponseEntity.ok(ApiResponse.success(orchestrator.verify(
                    frontImage.getBytes(), backImage.getBytes(), selfieImage.getBytes(),
                    clientSession, token, -1)));
        } catch (IOException ex) {
            throw new BadRequestException("Could not read the uploaded images");
        }
    }
}
