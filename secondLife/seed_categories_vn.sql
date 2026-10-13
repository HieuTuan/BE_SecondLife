-- ==========================================================================================
-- SCRIPT SEED DỮ LIỆU DANH MỤC & LOẠI SẢN PHẨM CHUYÊN BIỆT CHO ĐỒ GIA DỤNG (SECOND LIFE)
-- CƠ CHẾ: UPSERT AN TOÀN (Idempotent) qua seed_key
-- ĐẶC ĐIỂM QUAN TRỌNG:
-- 1. Tuyệt đối KHÔNG DÙNG lệnh DELETE/TRUNCATE để không gây lỗi khóa ngoại (FK) với bảng posts
--    hoặc category_question_templates đã tạo.
-- 2. Giữ nguyên UUID hiện có nếu danh mục/item đã tồn tại trong DB, chỉ cập nhật tên tiếng Việt chuẩn.
-- 3. Bổ sung các vật phẩm đồ gia dụng phổ biến còn thiếu (Nồi chiên không dầu, Nồi áp suất, Robot hút bụi...).
-- 4. 100% ĐỒ GIA DỤNG - KHÔNG có điện thoại, laptop, thời trang, xe cộ.
-- ==========================================================================================

-- BƯỚC 1: UPSERT 6 DANH MỤC GIA DỤNG THEO seed_key
INSERT INTO categories (id, seed_key, name, description) VALUES
    (gen_random_uuid(), 'kitchen', 'Bếp & Phòng ăn', 'Thiết bị điện tử nhà bếp, nấu nướng và đồ dùng bàn ăn gia đình'),
    (gen_random_uuid(), 'living-room', 'Phòng khách', 'Nội thất, bàn ghế, kệ tủ và thiết bị sinh hoạt phòng khách'),
    (gen_random_uuid(), 'bedroom', 'Phòng ngủ', 'Nội thất, giường nệm, tủ áo và thiết bị phòng ngủ gia đình'),
    (gen_random_uuid(), 'balcony-laundry', 'Ban công & Giặt giũ', 'Thiết bị giặt sấy, ủi đồ, phơi đồ và sinh hoạt ban công'),
    (gen_random_uuid(), 'bathroom', 'Phòng tắm & Vệ sinh', 'Bình nước nóng, thiết bị vệ sinh và phụ kiện phòng tắm'),
    (gen_random_uuid(), 'other', 'Thiết bị & Tiện ích khác', 'Máy lạnh điều hòa, quạt máy, lọc khí, hút bụi và tiện ích gia đình')
ON CONFLICT (seed_key) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description;

-- BƯỚC 2: CẬP NHẬT seed_key CHO CÁC DANH MỤC CŨ NẾU CHƯA CÓ seed_key (Khớp theo tên tiếng Anh / tiếng Việt)
UPDATE categories SET seed_key = 'kitchen' WHERE seed_key IS NULL AND lower(name) IN ('kitchen', 'bếp & phòng ăn');
UPDATE categories SET seed_key = 'living-room' WHERE seed_key IS NULL AND lower(name) IN ('living room', 'phòng khách');
UPDATE categories SET seed_key = 'bedroom' WHERE seed_key IS NULL AND lower(name) IN ('bedroom', 'phòng ngủ');
UPDATE categories SET seed_key = 'balcony-laundry' WHERE seed_key IS NULL AND lower(name) IN ('balcony & laundry', 'ban công & giặt giũ');
UPDATE categories SET seed_key = 'bathroom' WHERE seed_key IS NULL AND lower(name) IN ('bathroom & sanitation', 'bathroom', 'phòng tắm & vệ sinh');
UPDATE categories SET seed_key = 'other' WHERE seed_key IS NULL AND lower(name) IN ('other', 'khác', 'thiết bị & tiện ích khác');

-- BƯỚC 3: HÀM TIỆN ÍCH UPSERT ITEM THEO seed_key VÀ category_seed_key
DO $$
DECLARE
    v_kitchen_id UUID;
    v_living_id  UUID;
    v_bedroom_id UUID;
    v_laundry_id UUID;
    v_bath_id    UUID;
    v_other_id   UUID;
BEGIN
    SELECT id INTO v_kitchen_id FROM categories WHERE seed_key = 'kitchen' LIMIT 1;
    SELECT id INTO v_living_id  FROM categories WHERE seed_key = 'living-room' LIMIT 1;
    SELECT id INTO v_bedroom_id FROM categories WHERE seed_key = 'bedroom' LIMIT 1;
    SELECT id INTO v_laundry_id FROM categories WHERE seed_key = 'balcony-laundry' LIMIT 1;
    SELECT id INTO v_bath_id    FROM categories WHERE seed_key = 'bathroom' LIMIT 1;
    SELECT id INTO v_other_id   FROM categories WHERE seed_key = 'other' LIMIT 1;

    -- =========================================================================
    -- 1. BẾP & PHÒNG ĂN (kitchen)
    -- =========================================================================
    INSERT INTO items (id, seed_key, category_id, name) VALUES
        (gen_random_uuid(), 'kitchen/refrigerator', v_kitchen_id, 'Tủ lạnh'),
        (gen_random_uuid(), 'kitchen/rice-cooker', v_kitchen_id, 'Nồi cơm điện'),
        (gen_random_uuid(), 'kitchen/air-fryer', v_kitchen_id, 'Nồi chiên không dầu'),
        (gen_random_uuid(), 'kitchen/induction-cooker', v_kitchen_id, 'Bếp từ / Bếp hồng ngoại'),
        (gen_random_uuid(), 'kitchen/microwave-oven', v_kitchen_id, 'Lò vi sóng / Lò nướng'),
        (gen_random_uuid(), 'kitchen/blender', v_kitchen_id, 'Máy xay sinh tố / Máy ép chậm'),
        (gen_random_uuid(), 'kitchen/electric-kettle', v_kitchen_id, 'Bình đun siêu tốc / Bình thủy điện'),
        (gen_random_uuid(), 'kitchen/pressure-cooker', v_kitchen_id, 'Nồi áp suất điện'),
        (gen_random_uuid(), 'kitchen/dish-washer', v_kitchen_id, 'Máy rửa bát'),
        (gen_random_uuid(), 'kitchen/range-hood', v_kitchen_id, 'Máy hút mùi bếp'),
        (gen_random_uuid(), 'kitchen/dining-table', v_kitchen_id, 'Bàn ăn'),
        (gen_random_uuid(), 'kitchen/dining-chair', v_kitchen_id, 'Ghế ăn / Bộ bàn ăn'),
        (gen_random_uuid(), 'kitchen/cookware-set', v_kitchen_id, 'Bộ nồi chảo')
    ON CONFLICT (seed_key) DO UPDATE SET
        category_id = EXCLUDED.category_id,
        name = EXCLUDED.name;

    -- =========================================================================
    -- 2. PHÒNG KHÁCH (living-room)
    -- =========================================================================
    INSERT INTO items (id, seed_key, category_id, name) VALUES
        (gen_random_uuid(), 'living-room/sofa', v_living_id, 'Sofa / Ghế dài'),
        (gen_random_uuid(), 'living-room/coffee-table', v_living_id, 'Bàn trà / Bàn sofa'),
        (gen_random_uuid(), 'living-room/tv-stand', v_living_id, 'Kệ Tivi'),
        (gen_random_uuid(), 'living-room/armchair', v_living_id, 'Ghế bành / Ghế thư giãn'),
        (gen_random_uuid(), 'living-room/bookshelf', v_living_id, 'Giá sách / Kệ sách'),
        (gen_random_uuid(), 'living-room/floor-lamp', v_living_id, 'Đèn đứng / Đèn trang trí'),
        (gen_random_uuid(), 'living-room/shoe-cabinet', v_living_id, 'Kệ để giày / Tủ giày')
    ON CONFLICT (seed_key) DO UPDATE SET
        category_id = EXCLUDED.category_id,
        name = EXCLUDED.name;

    -- =========================================================================
    -- 3. PHÒNG NGỦ (bedroom)
    -- =========================================================================
    INSERT INTO items (id, seed_key, category_id, name) VALUES
        (gen_random_uuid(), 'bedroom/bed-frame', v_bedroom_id, 'Khung giường / Giường ngủ'),
        (gen_random_uuid(), 'bedroom/mattress', v_bedroom_id, 'Nệm / Đệm'),
        (gen_random_uuid(), 'bedroom/wardrobe', v_bedroom_id, 'Tủ quần áo'),
        (gen_random_uuid(), 'bedroom/bedside-table', v_bedroom_id, 'Tủ đầu giường'),
        (gen_random_uuid(), 'bedroom/dressing-table', v_bedroom_id, 'Bàn trang điểm'),
        (gen_random_uuid(), 'bedroom/desk', v_bedroom_id, 'Bàn làm việc / Bàn học'),
        (gen_random_uuid(), 'bedroom/ergonomic-chair', v_bedroom_id, 'Ghế công thái học / Ghế xoay')
    ON CONFLICT (seed_key) DO UPDATE SET
        category_id = EXCLUDED.category_id,
        name = EXCLUDED.name;

    -- =========================================================================
    -- 4. BAN CÔNG & GIẶT GIŨ (balcony-laundry)
    -- =========================================================================
    INSERT INTO items (id, seed_key, category_id, name) VALUES
        (gen_random_uuid(), 'balcony-laundry/washing-machine', v_laundry_id, 'Máy giặt'),
        (gen_random_uuid(), 'balcony-laundry/clothes-dryer', v_laundry_id, 'Máy sấy quần áo'),
        (gen_random_uuid(), 'balcony-laundry/drying-rack', v_laundry_id, 'Giá phơi đồ / Giàn phơi thông minh'),
        (gen_random_uuid(), 'balcony-laundry/laundry-basket', v_laundry_id, 'Giỏ đựng đồ giặt'),
        (gen_random_uuid(), 'balcony-laundry/iron', v_laundry_id, 'Bàn ủi / Bàn là hơi nước'),
        (gen_random_uuid(), 'balcony-laundry/balcony-table', v_laundry_id, 'Bàn ghế ban công')
    ON CONFLICT (seed_key) DO UPDATE SET
        category_id = EXCLUDED.category_id,
        name = EXCLUDED.name;

    -- =========================================================================
    -- 5. PHÒNG TẮM & VỆ SINH (bathroom)
    -- =========================================================================
    INSERT INTO items (id, seed_key, category_id, name) VALUES
        (gen_random_uuid(), 'bathroom/water-heater', v_bath_id, 'Máy nước nóng / Bình nóng lạnh'),
        (gen_random_uuid(), 'bathroom/bathroom-cabinet', v_bath_id, 'Tủ / Kệ phòng tắm'),
        (gen_random_uuid(), 'bathroom/washbasin', v_bath_id, 'Bồn rửa mặt / Lavabo'),
        (gen_random_uuid(), 'bathroom/mirror', v_bath_id, 'Gương phòng tắm'),
        (gen_random_uuid(), 'bathroom/shower-set', v_bath_id, 'Vòi hoa sen / Cây sen tắm'),
        (gen_random_uuid(), 'bathroom/toilet', v_bath_id, 'Bồn cầu')
    ON CONFLICT (seed_key) DO UPDATE SET
        category_id = EXCLUDED.category_id,
        name = EXCLUDED.name;

    -- =========================================================================
    -- 6. THIẾT BỊ & TIỆN ÍCH KHÁC (other)
    -- =========================================================================
    INSERT INTO items (id, seed_key, category_id, name) VALUES
        (gen_random_uuid(), 'other/air-conditioner', v_other_id, 'Máy lạnh / Điều hòa'),
        (gen_random_uuid(), 'other/fan', v_other_id, 'Quạt điện / Quạt máy'),
        (gen_random_uuid(), 'other/air-purifier', v_other_id, 'Máy lọc không khí'),
        (gen_random_uuid(), 'other/vacuum-cleaner', v_other_id, 'Máy hút bụi / Robot hút bụi'),
        (gen_random_uuid(), 'other/water-purifier', v_other_id, 'Máy lọc nước gia đình'),
        (gen_random_uuid(), 'other/dehumidifier', v_other_id, 'Máy hút ẩm'),
        (gen_random_uuid(), 'other/storage-cabinet', v_other_id, 'Tủ đựng đồ đa năng'),
        (gen_random_uuid(), 'other/tool-box', v_other_id, 'Hộp đồ nghề sửa chữa gia đình')
    ON CONFLICT (seed_key) DO UPDATE SET
        category_id = EXCLUDED.category_id,
        name = EXCLUDED.name;

    -- BƯỚC 4: LIÊN KẾT seed_key CHO CÁC ITEM CŨ CÒN THIẾU SEED_KEY THEO TÊN
    UPDATE items SET seed_key = 'kitchen/rice-cooker' WHERE seed_key IS NULL AND lower(name) LIKE '%nồi cơm%';
    UPDATE items SET seed_key = 'kitchen/dining-table' WHERE seed_key IS NULL AND lower(name) = 'bàn ăn';
    UPDATE items SET seed_key = 'kitchen/refrigerator' WHERE seed_key IS NULL AND lower(name) = 'tủ lạnh';
    UPDATE items SET seed_key = 'balcony-laundry/washing-machine' WHERE seed_key IS NULL AND lower(name) = 'máy giặt';
    UPDATE items SET seed_key = 'other/air-conditioner' WHERE seed_key IS NULL AND lower(name) LIKE '%máy lạnh%';
    UPDATE items SET seed_key = 'other/fan' WHERE seed_key IS NULL AND lower(name) LIKE '%quạt%';
    UPDATE items SET seed_key = 'living-room/sofa' WHERE seed_key IS NULL AND lower(name) LIKE '%sofa%';
END $$;