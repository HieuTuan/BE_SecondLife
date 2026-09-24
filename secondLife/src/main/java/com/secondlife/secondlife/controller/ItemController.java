package com.secondlife.secondlife.controller;

import com.secondlife.secondlife.entity.Item;
import com.secondlife.secondlife.repository.ItemRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/items")
public class ItemController {

    private final ItemRepository itemRepository;

    public ItemController(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @GetMapping("/category/{categoryId}")
    public ResponseEntity<List<Item>> getItemsByCategory(@PathVariable UUID categoryId) {
        return ResponseEntity.ok(itemRepository.findByCategoryId(categoryId));
    }
}
