package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    @Query("select (count(c) > 0) from Category c where lower(trim(c.name)) = lower(:name) and (:excludedId is null or c.id <> :excludedId)")
    boolean nameExists(@Param("name") String name,
                       @Param("excludedId") UUID excludedId);

    public record CategorySeed(String key, String name, String description) { }

    default void seedCategories(List<CategorySeed> seeds) {
        if (!seeds.isEmpty()) seedCategoriesJson(JsonMapper.builder().build().writeValueAsString(seeds));
    }

    @Query(value = """
            SELECT EXISTS(SELECT 1 FROM posts WHERE category_id = :categoryId)
                OR EXISTS(SELECT 1 FROM category_question_templates WHERE category_id = :categoryId)
            """, nativeQuery = true)
    boolean isCategoryReferenced(@Param("categoryId") UUID categoryId);

    /** Serialize catalog names/seeding across application instances until transaction end. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(6002246351243529295)", nativeQuery = true)
    void lockCatalog();

    @Modifying
    @Transactional
    @Query(value = """
            WITH seeds AS (
                SELECT s.key AS seed_key, s.name, s.description
                FROM jsonb_to_recordset(CAST(:seeds AS jsonb)) AS s(key text, name text, description text)
            ), candidates AS (
                SELECT DISTINCT ON (s.seed_key) s.seed_key, c.id
                FROM seeds s JOIN categories c ON lower(btrim(c.name)) = lower(s.name)
                WHERE c.seed_key IS NULL
                  AND NOT EXISTS (SELECT 1 FROM categories x WHERE x.seed_key = s.seed_key)
                ORDER BY s.seed_key, c.id
            ), adopted AS (
                UPDATE categories c SET seed_key = x.seed_key FROM candidates x WHERE c.id = x.id
                RETURNING c.seed_key
            )
            INSERT INTO categories(id, seed_key, name, description)
            SELECT gen_random_uuid(), s.seed_key, s.name, s.description FROM seeds s
            WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE c.seed_key = s.seed_key)
              AND NOT EXISTS (SELECT 1 FROM adopted a WHERE a.seed_key = s.seed_key)
            ON CONFLICT (seed_key) DO NOTHING
            """, nativeQuery = true)
    void seedCategoriesJson(@Param("seeds") String seeds);
}
