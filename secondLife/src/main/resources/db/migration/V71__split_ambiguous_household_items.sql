SET LOCAL lock_timeout = '5s';

-- Reused by both catalog seed paths, including fresh databases where application
-- seeders run after Flyway. Existing posts/templates keep their original Item IDs.
CREATE FUNCTION secondlife_split_inspection_catalog_items_v71() RETURNS INTEGER
LANGUAGE plpgsql SET search_path = public, pg_temp AS $$
DECLARE
    rule RECORD;
    parent_item RECORD;
    inserted_count INTEGER;
    total_inserted INTEGER := 0;
BEGIN
    PERFORM pg_advisory_xact_lock(6002246351243529295);
    FOR rule IN SELECT * FROM (VALUES
        ('Nồi cơm điện','rice-mechanical','Nồi cơm cơ'),
        ('Nồi cơm điện','rice-electronic','Nồi cơm điện tử / Đa chức năng'),
        ('Rice Cooker','rice-mechanical','Nồi cơm cơ'),
        ('Rice Cooker','rice-electronic','Nồi cơm điện tử / Đa chức năng'),
        ('Bếp / Nồi cơm điện','induction','Bếp từ'),
        ('Bếp / Nồi cơm điện','infrared','Bếp hồng ngoại'),
        ('Bếp / Nồi cơm điện','rice-mechanical','Nồi cơm cơ'),
        ('Bếp / Nồi cơm điện','rice-electronic','Nồi cơm điện tử / Đa chức năng'),
        ('Máy giặt','washer','Máy giặt thông thường'),
        ('Máy giặt','washer-dryer','Máy giặt tích hợp sấy'),
        ('Washing Machine','washer','Máy giặt thông thường'),
        ('Washing Machine','washer-dryer','Máy giặt tích hợp sấy'),
        ('Máy sấy','dryer-resistance','Máy sấy điện trở'),
        ('Máy sấy','dryer-heat-pump','Máy sấy bơm nhiệt'),
        ('Clothes Dryer','dryer-resistance','Máy sấy điện trở'),
        ('Clothes Dryer','dryer-heat-pump','Máy sấy bơm nhiệt'),
        ('Dryer','dryer-resistance','Máy sấy điện trở'),
        ('Dryer','dryer-heat-pump','Máy sấy bơm nhiệt'),
        ('Giàn phơi thông minh','drying-manual','Giàn phơi quay tay'),
        ('Giàn phơi thông minh','drying-electric','Giàn phơi điều khiển điện'),
        ('Máy lọc nước gia đình','water-passive','Bộ lọc nước không điện'),
        ('Máy lọc nước gia đình','water-ro','Máy lọc nước RO'),
        ('Máy lọc nước gia đình','water-hot-cold','Máy lọc nước nóng lạnh'),
        ('Máy hút bụi','vacuum','Máy hút bụi thông thường'),
        ('Máy hút bụi','robot-vacuum','Robot hút bụi / Lau nhà'),
        ('Vacuum Cleaner','vacuum','Máy hút bụi thông thường'),
        ('Vacuum Cleaner','robot-vacuum','Robot hút bụi / Lau nhà'),
        ('Lò vi sóng / Lò nướng','microwave','Lò vi sóng'),
        ('Lò vi sóng / Lò nướng','oven','Lò nướng'),
        ('Microwave Oven','microwave','Lò vi sóng'),
        ('Microwave Oven','oven','Lò nướng'),
        ('Bếp từ / Bếp hồng ngoại','induction','Bếp từ'),
        ('Bếp từ / Bếp hồng ngoại','infrared','Bếp hồng ngoại'),
        ('Bình đun siêu tốc / Bình thủy điện','kettle','Bình đun siêu tốc'),
        ('Bình đun siêu tốc / Bình thủy điện','thermo-pot','Bình thủy điện'),
        ('Tủ / Kệ phòng tắm','bath-cabinet','Tủ phòng tắm'),
        ('Tủ / Kệ phòng tắm','bath-shelf','Kệ phòng tắm')
    ) AS rules(source_name,variant_code,target_name)
    LOOP
        FOR parent_item IN SELECT id, category_id FROM items
            WHERE lower(btrim(name)) = lower(rule.source_name)
        LOOP
            INSERT INTO items(id,category_id,name,seed_key,is_selectable,replacement_for_item_id,variant_code)
            SELECT gen_random_uuid(),parent_item.category_id,rule.target_name,
                'variant:' || parent_item.id || ':' || rule.variant_code,TRUE,parent_item.id,rule.variant_code
            WHERE NOT EXISTS (
                SELECT 1 FROM items i WHERE i.category_id=parent_item.category_id
                    AND lower(btrim(i.name))=lower(rule.target_name)
            );
            GET DIAGNOSTICS inserted_count = ROW_COUNT;
            total_inserted := total_inserted + inserted_count;
            UPDATE items SET is_selectable=FALSE WHERE id=parent_item.id AND is_selectable;
        END LOOP;
    END LOOP;
    RETURN total_inserted;
END;
$$;

SELECT secondlife_split_inspection_catalog_items_v71();
