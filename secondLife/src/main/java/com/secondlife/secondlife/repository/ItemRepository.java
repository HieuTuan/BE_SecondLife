package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {
    java.util.List<Item> findByCategoryId(UUID categoryId);
    boolean existsByCategory_Id(UUID categoryId);
    @org.springframework.data.jpa.repository.Query("select (count(i) > 0) from Item i where i.category.id = :categoryId and lower(trim(i.name)) = lower(:name) and (:excludedId is null or i.id <> :excludedId)")
    boolean nameExists(@org.springframework.data.repository.query.Param("categoryId") UUID categoryId,
                       @org.springframework.data.repository.query.Param("name") String name,
                       @org.springframework.data.repository.query.Param("excludedId") UUID excludedId);
}
