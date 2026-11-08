package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.common.PageResponse;
import com.secondlife.secondlife.dto.request.InspectionChecklistSearchRequest;
import com.secondlife.secondlife.dto.request.ReviewFeeProposalRequest;
import com.secondlife.secondlife.dto.response.InspectionChecklistResponse;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.InspectionChecklistService;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/inspection/checklist-proposals")
@PreAuthorize("hasRole('ADMIN') and hasAuthority('INSPECTION_CHECKLIST_MANAGE')")
public class AdminInspectionChecklistController {
    private final InspectionChecklistService checklists;
    private final CurrentUserProvider currentUser;

    @GetMapping({"", "/search"})
    @Operation(summary = "Admin tìm kiếm đề xuất checklist theo Item, cấp, trung tâm và giá kiểm định",
            description = "keyword tìm một phần nội dung, không phân biệt hoa/thường hoặc dấu tiếng Việt; "
                    + "các từ khớp theo thứ tự. Giá đã duyệt và giá đang đề xuất của trung tâm tác giả được trả riêng trong pricing.")
    public ApiResponse<PageResponse<InspectionChecklistResponse>> list(
            @AuthenticationPrincipal CustomUserDetails principal,
            @Valid @ModelAttribute InspectionChecklistSearchRequest filter,
            @PageableDefault(sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.success(PageResponse.from(checklists.searchProposals(currentUser.resolveUserId(principal), filter, pageable)));
    }

    @GetMapping("/{id}")
    public ApiResponse<InspectionChecklistResponse> get(@AuthenticationPrincipal CustomUserDetails principal,
                                                       @PathVariable UUID id) {
        return ApiResponse.success(checklists.getById(currentUser.resolveUserId(principal), id));
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Admin duyệt hoặc từ chối checklist kèm cấp L1-L4; duyệt đồng thời kích hoạt phiên bản mới")
    public ApiResponse<InspectionChecklistResponse> review(@AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable UUID id, @Valid @RequestBody ReviewFeeProposalRequest request) {
        return ApiResponse.success(checklists.review(currentUser.resolveUserId(principal), id, request.approved(), request.reason()));
    }
}
