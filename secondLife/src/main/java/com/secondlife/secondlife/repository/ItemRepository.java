package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Item;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ItemRepository extends JpaRepository<Item, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Item i where i.id = :id")
    Optional<Item> findByIdForUpdate(@Param("id") UUID id);

    record VariantSplitRule(String sourceName, String variantCode, String targetName) { }

    List<VariantSplitRule> VARIANT_SPLIT_RULES = List.of(
            new VariantSplitRule("Nồi cơm điện", "rice-mechanical", "Nồi cơm cơ"),
            new VariantSplitRule("Nồi cơm điện", "rice-electronic", "Nồi cơm điện tử / Đa chức năng"),
            new VariantSplitRule("Rice Cooker", "rice-mechanical", "Nồi cơm cơ"),
            new VariantSplitRule("Rice Cooker", "rice-electronic", "Nồi cơm điện tử / Đa chức năng"),
            new VariantSplitRule("Bếp / Nồi cơm điện", "induction", "Bếp từ"),
            new VariantSplitRule("Bếp / Nồi cơm điện", "infrared", "Bếp hồng ngoại"),
            new VariantSplitRule("Bếp / Nồi cơm điện", "rice-mechanical", "Nồi cơm cơ"),
            new VariantSplitRule("Bếp / Nồi cơm điện", "rice-electronic", "Nồi cơm điện tử / Đa chức năng"),
            new VariantSplitRule("Máy giặt", "washer", "Máy giặt thông thường"),
            new VariantSplitRule("Máy giặt", "washer-dryer", "Máy giặt tích hợp sấy"),
            new VariantSplitRule("Washing Machine", "washer", "Máy giặt thông thường"),
            new VariantSplitRule("Washing Machine", "washer-dryer", "Máy giặt tích hợp sấy"),
            new VariantSplitRule("Máy sấy", "dryer-resistance", "Máy sấy điện trở"),
            new VariantSplitRule("Máy sấy", "dryer-heat-pump", "Máy sấy bơm nhiệt"),
            new VariantSplitRule("Clothes Dryer", "dryer-resistance", "Máy sấy điện trở"),
            new VariantSplitRule("Clothes Dryer", "dryer-heat-pump", "Máy sấy bơm nhiệt"),
            new VariantSplitRule("Dryer", "dryer-resistance", "Máy sấy điện trở"),
            new VariantSplitRule("Dryer", "dryer-heat-pump", "Máy sấy bơm nhiệt"),
            new VariantSplitRule("Giàn phơi thông minh", "drying-manual", "Giàn phơi quay tay"),
            new VariantSplitRule("Giàn phơi thông minh", "drying-electric", "Giàn phơi điều khiển điện"),
            new VariantSplitRule("Máy lọc nước gia đình", "water-passive", "Bộ lọc nước không điện"),
            new VariantSplitRule("Máy lọc nước gia đình", "water-ro", "Máy lọc nước RO"),
            new VariantSplitRule("Máy lọc nước gia đình", "water-hot-cold", "Máy lọc nước nóng lạnh"),
            new VariantSplitRule("Máy hút bụi", "vacuum", "Máy hút bụi thông thường"),
            new VariantSplitRule("Máy hút bụi", "robot-vacuum", "Robot hút bụi / Lau nhà"),
            new VariantSplitRule("Vacuum Cleaner", "vacuum", "Máy hút bụi thông thường"),
            new VariantSplitRule("Vacuum Cleaner", "robot-vacuum", "Robot hút bụi / Lau nhà"),
            new VariantSplitRule("Lò vi sóng / Lò nướng", "microwave", "Lò vi sóng"),
            new VariantSplitRule("Lò vi sóng / Lò nướng", "oven", "Lò nướng"),
            new VariantSplitRule("Microwave Oven", "microwave", "Lò vi sóng"),
            new VariantSplitRule("Microwave Oven", "oven", "Lò nướng"),
            new VariantSplitRule("Bếp từ / Bếp hồng ngoại", "induction", "Bếp từ"),
            new VariantSplitRule("Bếp từ / Bếp hồng ngoại", "infrared", "Bếp hồng ngoại"),
            new VariantSplitRule("Bình đun siêu tốc / Bình thủy điện", "kettle", "Bình đun siêu tốc"),
            new VariantSplitRule("Bình đun siêu tốc / Bình thủy điện", "thermo-pot", "Bình thủy điện"),
            new VariantSplitRule("Tủ / Kệ phòng tắm", "bath-cabinet", "Tủ phòng tắm"),
            new VariantSplitRule("Tủ / Kệ phòng tắm", "bath-shelf", "Kệ phòng tắm")
    );

    @Transactional(propagation = Propagation.MANDATORY)
    default int splitInspectionVariants() {
        List<Item> existingItems = findAll();
        int totalInserted = 0;
        for (VariantSplitRule rule : VARIANT_SPLIT_RULES) {
            for (Item parentItem : existingItems) {
                if (parentItem.getName() == null || parentItem.getCategory() == null
                        || !parentItem.getName().trim().equalsIgnoreCase(rule.sourceName())) {
                    continue;
                }
                UUID categoryId = parentItem.getCategory().getId();
                if (!nameExists(categoryId, rule.targetName(), null)) {
                    Item variant = new Item();
                    variant.setCategory(parentItem.getCategory());
                    variant.setName(rule.targetName());
                    variant.setSeedKey("variant:" + parentItem.getId() + ":" + rule.variantCode());
                    variant.setSelectable(true);
                    variant.setReplacementForItemId(parentItem.getId());
                    variant.setVariantCode(rule.variantCode());
                    save(variant);
                    totalInserted++;
                }
                if (parentItem.isSelectable()) {
                    parentItem.setSelectable(false);
                    save(parentItem);
                }
            }
        }
        return totalInserted;
    }
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
                 OR EXISTS(SELECT 1 FROM inspection_checklist_templates WHERE catalog_item_id = :itemId)
                 OR EXISTS(SELECT 1 FROM items WHERE replacement_for_item_id = :itemId)
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
