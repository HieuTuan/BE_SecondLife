package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.dto.request.ItemRequest;
import com.secondlife.secondlife.dto.response.ItemResponse;
import com.secondlife.secondlife.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/items")
public class ItemController {

    private final CatalogService catalog;

    @GetMapping
    public List<ItemResponse> getItems(@RequestParam(required = false) UUID categoryId) { return catalog.items(categoryId); }

    @GetMapping("/category/{categoryId}")
    public List<ItemResponse> getItemsByCategory(@PathVariable UUID categoryId) { return catalog.items(categoryId); }

    @GetMapping("/{id}")
    public ItemResponse getItem(@PathVariable UUID id) { return catalog.item(id); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_CATALOG_MANAGE')")
    public ItemResponse createItem(@Valid @RequestBody ItemRequest request) { return catalog.createItem(request); }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_CATALOG_MANAGE')")
    public ItemResponse updateItem(@PathVariable UUID id, @Valid @RequestBody ItemRequest request) {
        return catalog.updateItem(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ADMIN_CATALOG_MANAGE')")
    public void deleteItem(@PathVariable UUID id) { catalog.deleteItem(id); }
}
