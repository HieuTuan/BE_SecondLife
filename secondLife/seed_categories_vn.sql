-- Chạy script này trong công cụ quản lý Database (pgAdmin, DBeaver, DataGrip...) để tạo lại dữ liệu Category/Item bằng tiếng Việt.
-- LƯU Ý: Lệnh DELETE dưới đây sẽ xóa trắng bảng items và categories hiện tại. 
-- Nếu bạn đã có bài đăng (Post) gắn với item/category cũ, lệnh DELETE sẽ báo lỗi khóa ngoại (Foreign Key constraint). 
-- Trong trường hợp đó, bạn có thể TRUNCATE CASCADE các bảng, hoặc bỏ 2 dòng DELETE đi để chỉ thêm (insert) mới.

DELETE FROM items;
DELETE FROM categories;

-- 1. Thiết bị điện tử
DO $ $
DECLARE cate_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO categories (id, name, description) VALUES (cate_id, 'Thiết bị điện tử', 'Các thiết bị công nghệ, điện thoại, máy tính');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Điện thoại di động');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Máy tính xách tay (Laptop)');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Máy tính bảng (Tablet)');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Phụ kiện điện tử');
END $ $;

-- 2. Đồ gia dụng
DO $ $
DECLARE cate_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO categories (id, name, description) VALUES (cate_id, 'Đồ gia dụng', 'Thiết bị điện lạnh, điện gia dụng trong gia đình');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Tivi, Âm thanh');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Tủ lạnh, Tủ đông');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Máy giặt, Máy sấy');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Máy lạnh, Điều hòa');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Đồ điện nhà bếp');
END $ $;

-- 3. Thời trang & Phụ kiện
DO $ $
DECLARE cate_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO categories (id, name, description) VALUES (cate_id, 'Thời trang & Phụ kiện', 'Quần áo, giày dép, túi xách nam nữ');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Quần áo nam');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Quần áo nữ');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Giày dép');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Túi xách, Balo');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Đồng hồ, Trang sức');
END $ $;

-- 4. Phương tiện di chuyển
DO $ $
DECLARE cate_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO categories (id, name, description) VALUES (cate_id, 'Phương tiện di chuyển', 'Xe máy, xe đạp, ô tô và phụ tùng');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Xe máy');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Xe đạp');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Ô tô');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Phụ tùng, Đồ chơi xe');
END $ $;

-- 5. Nội thất & Đời sống
DO $ $
DECLARE cate_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO categories (id, name, description) VALUES (cate_id, 'Nội thất & Ngoại thất', 'Bàn ghế, giường tủ, đồ trang trí nội thất');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Bàn ghế');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Giường tủ');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Đồ trang trí, Decor');
END $ $;

-- 6. Mẹ & Bé
DO $ $
DECLARE cate_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO categories (id, name, description) VALUES (cate_id, 'Mẹ & Bé', 'Đồ dùng cho mẹ bầu và em bé');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Quần áo, Giày dép trẻ em');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Đồ chơi');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Đồ dùng cho mẹ');
    INSERT INTO items (id, category_id, name) VALUES (gen_random_uuid(), cate_id, 'Xe đẩy, Nôi cũi');
END $ $;