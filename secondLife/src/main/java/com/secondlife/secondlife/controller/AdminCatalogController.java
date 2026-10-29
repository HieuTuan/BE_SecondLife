package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.ApproveTemplateProposalRequest;
import com.secondlife.secondlife.dto.request.CategoryQuestionTemplateRequest;
import com.secondlife.secondlife.dto.request.CategoryRequest;
import com.secondlife.secondlife.dto.request.ItemRequest;
import com.secondlife.secondlife.dto.request.RejectTemplateProposalRequest;
import com.secondlife.secondlife.dto.request.UpdateTemplateProposalRequest;
import com.secondlife.secondlife.dto.response.CategoryQuestionTemplateProposalResponse;
import com.secondlife.secondlife.dto.response.CategoryQuestionTemplateResponse;
import com.secondlife.secondlife.dto.response.CategoryResponse;
import com.secondlife.secondlife.dto.response.ItemResponse;
import com.secondlife.secondlife.enums.TemplateProposalStatus;
import com.secondlife.secondlife.security.CurrentUserProvider;
import com.secondlife.secondlife.security.userdetails.CustomUserDetails;
import com.secondlife.secondlife.service.CatalogManagementService;
import com.secondlife.secondlife.service.CategoryTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/catalog")
@PreAuthorize("hasAnyAuthority('ADMIN_CONFIG_MANAGE', 'ADMIN_CATALOG_MANAGE')")
public class AdminCatalogController {

    private final CatalogManagementService catalogService;
    private final CategoryTemplateService templateService;
    private final CurrentUserProvider currentUserProvider;

    public AdminCatalogController(
            CatalogManagementService catalogService,
            CategoryTemplateService templateService,
            CurrentUserProvider currentUserProvider
    ) {
        this.catalogService = catalogService;
        this.templateService = templateService;
        this.currentUserProvider = currentUserProvider;
    }

    // Category Endpoints
    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        return ResponseEntity.ok(catalogService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.ok(catalogService.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<String> deleteCategory(@PathVariable UUID id) {
        catalogService.deleteCategory(id);
        return ResponseEntity.ok("Category deleted successfully");
    }

    @GetMapping("/categories")
    public ResponseEntity<List<CategoryResponse>> getAllCategories() {
        return ResponseEntity.ok(catalogService.getAllCategories());
    }

    // Item Endpoints
    @PostMapping("/items")
    public ResponseEntity<ItemResponse> createItem(@Valid @RequestBody ItemRequest request) {
        return ResponseEntity.ok(catalogService.createItem(request));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<ItemResponse> updateItem(
            @PathVariable UUID id,
            @Valid @RequestBody ItemRequest request
    ) {
        return ResponseEntity.ok(catalogService.updateItem(id, request));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<String> deleteItem(@PathVariable UUID id) {
        catalogService.deleteItem(id);
        return ResponseEntity.ok("Item deleted successfully");
    }

    @GetMapping("/items")
    public ResponseEntity<List<ItemResponse>> getAllItems() {
        return ResponseEntity.ok(catalogService.getAllItems());
    }

    @GetMapping("/categories/{categoryId}/items")
    public ResponseEntity<List<ItemResponse>> getItemsByCategory(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(catalogService.getItemsByCategory(categoryId));
    }

    // Official Question Template Endpoints
    @PostMapping("/templates")
    public ResponseEntity<CategoryQuestionTemplateResponse> createTemplate(
            @Valid @RequestBody CategoryQuestionTemplateRequest request
    ) {
        return ResponseEntity.ok(templateService.createTemplate(request));
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<CategoryQuestionTemplateResponse> updateTemplate(
            @PathVariable UUID id,
            @Valid @RequestBody CategoryQuestionTemplateRequest request
    ) {
        return ResponseEntity.ok(templateService.updateTemplate(id, request));
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<String> deleteTemplate(@PathVariable UUID id) {
        templateService.deleteTemplate(id);
        return ResponseEntity.ok("Template deleted successfully");
    }

    @GetMapping("/templates")
    public ResponseEntity<List<CategoryQuestionTemplateResponse>> getAllTemplates() {
        return ResponseEntity.ok(templateService.getAllTemplates());
    }

    // AI Question Template Proposal Review Endpoints
    @GetMapping("/template-proposals")
    public ResponseEntity<List<CategoryQuestionTemplateProposalResponse>> getTemplateProposals(
            @RequestParam(required = false) TemplateProposalStatus status
    ) {
        return ResponseEntity.ok(templateService.getProposals(status));
    }

    @PutMapping("/template-proposals/{id}")
    public ResponseEntity<CategoryQuestionTemplateProposalResponse> updateTemplateProposal(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTemplateProposalRequest request
    ) {
        return ResponseEntity.ok(templateService.updateProposal(id, request));
    }

    @PostMapping("/template-proposals/{id}/approve")
    public ResponseEntity<CategoryQuestionTemplateResponse> approveTemplateProposal(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody ApproveTemplateProposalRequest request
    ) {
        UUID adminId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(templateService.approveProposal(id, adminId, request));
    }

    @PostMapping("/template-proposals/{id}/reject")
    public ResponseEntity<CategoryQuestionTemplateProposalResponse> rejectTemplateProposal(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody(required = false) RejectTemplateProposalRequest request
    ) {
        UUID adminId = currentUserProvider.resolveUserId(userDetails);
        return ResponseEntity.ok(templateService.rejectProposal(id, adminId, request));
    }
}
