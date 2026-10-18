package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {
    List<Item> findByCategoryId(UUID categoryId);
    boolean existsByCategory_Id(UUID categoryId);
    @Query("select (count(i) > 0) from Item i where i.category.id = :categoryId and lower(trim(i.name)) = lower(:name) and (:excludedId is null or i.id <> :excludedId)")
    boolean nameExists(@Param("categoryId") UUID categoryId,
                       @Param("name") String name,
                       @Param("excludedId") UUID excludedId);

    public record ItemSeed(String key, String categoryKey, String name) { }

    default void seedItems(List<ItemSeed> seeds) {
        if (!seeds.isEmpty()) seedItemsJson(JsonMapper.builder().build().writeValueAsString(seeds));
    }

    @Query(value = """
            SELECT EXISTS(SELECT 1 FROM posts WHERE item_id = :itemId)
                OR EXISTS(SELECT 1 FROM category_question_templates WHERE item_id = :itemId)
            """, nativeQuery = true)
    boolean isItemReferenced(@Param("itemId") UUID itemId);

    @Modifying
    @Transactional
    @Query(value = """
            WITH seeds AS (
                SELECT s.key AS seed_key, s."categoryKey" AS category_key, s.name
                FROM jsonb_to_recordset(CAST(:seeds AS jsonb)) AS s(key text, "categoryKey" text, name text)
            ), candidates AS (
                SELECT DISTINCT ON (s.seed_key) s.seed_key, i.id
                FROM seeds s JOIN categories c ON c.seed_key = s.category_key
                JOIN items i ON i.category_id = c.id AND lower(btrim(i.name)) = lower(s.name)
                WHERE i.seed_key IS NULL
                  AND NOT EXISTS (SELECT 1 FROM items x WHERE x.seed_key = s.seed_key)
                ORDER BY s.seed_key, i.id
            ), adopted AS (
                UPDATE items i SET seed_key = x.seed_key FROM candidates x WHERE i.id = x.id
                RETURNING i.seed_key
            )
            INSERT INTO items(id, seed_key, category_id, name)
            SELECT gen_random_uuid(), s.seed_key, c.id, s.name FROM seeds s
            JOIN categories c ON c.seed_key = s.category_key
            WHERE NOT EXISTS (SELECT 1 FROM items i WHERE i.seed_key = s.seed_key)
              AND NOT EXISTS (SELECT 1 FROM adopted a WHERE a.seed_key = s.seed_key)
            ON CONFLICT (seed_key) DO NOTHING
            """, nativeQuery = true)
    void seedItemsJson(@Param("seeds") String seeds);
}
