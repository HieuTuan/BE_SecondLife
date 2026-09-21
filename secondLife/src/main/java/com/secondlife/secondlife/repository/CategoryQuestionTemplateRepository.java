package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.CategoryQuestionTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryQuestionTemplateRepository extends JpaRepository<CategoryQuestionTemplate, UUID> {
    Optional<CategoryQuestionTemplate> findByCategoryId(UUID categoryId);
}
