package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.common.ApiResponse;
import com.secondlife.secondlife.dto.response.MarketplaceListingResponse;
import com.secondlife.secondlife.service.MarketplaceListingService;
import com.secondlife.secondlife.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/listings")
@PreAuthorize("isAuthenticated()")
public class MarketplaceListingController {
    private final MarketplaceListingService listings;
    private final int maxPageSize;

    public MarketplaceListingController(MarketplaceListingService listings,
            @Value("${app.pagination.max-size}") int maxPageSize) {
        this.listings = listings;
        this.maxPageSize = maxPageSize;
    }

    @GetMapping
    public ApiResponse<Page<MarketplaceListingResponse>> list(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID itemId,
            @RequestParam(defaultValue = "${app.pagination.default-page}") int page,
            @RequestParam(defaultValue = "${app.pagination.default-size}") int size) {
        if (page < 0 || size < 1 || size > maxPageSize) throw new BadRequestException("Invalid page or size");
        return ApiResponse.success(listings.list(categoryId, itemId,
                PageRequest.of(page, size, Sort.by("publishedAt").descending())));
    }

    @GetMapping("/{postId}")
    public ApiResponse<MarketplaceListingResponse> get(@PathVariable UUID postId) {
        return ApiResponse.success(listings.get(postId));
    }
}
