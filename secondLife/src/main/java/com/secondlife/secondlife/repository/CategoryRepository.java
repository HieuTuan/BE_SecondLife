package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    @org.springframework.data.jpa.repository.Query("select (count(c) > 0) from Category c where lower(trim(c.name)) = lower(:name) and (:excludedId is null or c.id <> :excludedId)")
    boolean nameExists(@org.springframework.data.repository.query.Param("name") String name,
                       @org.springframework.data.repository.query.Param("excludedId") UUID excludedId);
}
