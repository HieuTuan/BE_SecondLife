package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.CreateInspectorRequest;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.InspectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inspector-center")
@RequiredArgsConstructor
@Tag(name = "Inspection Center Management", description = "APIs dành cho Inspection Center Manager quản lý nhân viên")
public class InspectionCenterController {

    private final InspectionService inspectionService;
    private final CurrentUserProvider currentUserProvider;

    /** Manager: Tạo tài khoản nhân viên kiểm định */
    @PostMapping("/staff")
    @Operation(summary = "Tạo tài khoản nhân viên kiểm định (role INSPECTOR)")
    public ResponseEntity<String> createStaff(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody CreateInspectorRequest request) {
        UUID managerId = currentUserProvider.resolveUserId(userDetails);
        inspectionService.createInspectorAccount(
                request.getEmail(), request.getFullName(), request.getPassword(), managerId);
        return ResponseEntity.ok("Inspector account created successfully");
    }

    /** Manager: Xem danh sách nhân viên */
    @GetMapping("/staff")
    @Operation(summary = "Xem danh sách nhân viên kiểm định")
    public ResponseEntity<List<?>> getStaffList(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UUID managerId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(inspectionService.getStaffList(managerId));
    }
}
