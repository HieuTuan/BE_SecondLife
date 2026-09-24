-- Insert Categories
INSERT INTO categories (id, name, description) VALUES ('11111111-1111-1111-1111-111111111111', 'Gia dụng nhà bếp', 'Các thiết bị sử dụng trong nhà bếp');
INSERT INTO categories (id, name, description) VALUES ('22222222-2222-2222-2222-222222222222', 'Thiết bị điện tử', 'Các thiết bị điện tử gia đình');
INSERT INTO categories (id, name, description) VALUES ('33333333-3333-3333-3333-333333333333', 'Nội thất', 'Đồ nội thất trong gia đình');
INSERT INTO categories (id, name, description) VALUES ('44444444-4444-4444-4444-444444444444', 'Giặt ủi & Làm sạch', 'Máy giặt, máy hút bụi, v.v.');

-- Insert Items for Gia dụng nhà bếp
INSERT INTO items (id, category_id, name) VALUES ('11111111-1111-1111-1111-000000000001', '11111111-1111-1111-1111-111111111111', 'Nồi cơm điện');
INSERT INTO items (id, category_id, name) VALUES ('11111111-1111-1111-1111-000000000002', '11111111-1111-1111-1111-111111111111', 'Bếp từ');
INSERT INTO items (id, category_id, name) VALUES ('11111111-1111-1111-1111-000000000003', '11111111-1111-1111-1111-111111111111', 'Bếp gas');
INSERT INTO items (id, category_id, name) VALUES ('11111111-1111-1111-1111-000000000004', '11111111-1111-1111-1111-111111111111', 'Lò vi sóng');
INSERT INTO items (id, category_id, name) VALUES ('11111111-1111-1111-1111-000000000005', '11111111-1111-1111-1111-111111111111', 'Lò nướng');

-- Insert Items for Thiết bị điện tử
INSERT INTO items (id, category_id, name) VALUES ('22222222-2222-2222-2222-000000000001', '22222222-2222-2222-2222-222222222222', 'Tivi');
INSERT INTO items (id, category_id, name) VALUES ('22222222-2222-2222-2222-000000000002', '22222222-2222-2222-2222-222222222222', 'Quạt điện');
INSERT INTO items (id, category_id, name) VALUES ('22222222-2222-2222-2222-000000000003', '22222222-2222-2222-2222-222222222222', 'Máy lạnh');

-- Insert Items for Nội thất
INSERT INTO items (id, category_id, name) VALUES ('33333333-3333-3333-3333-000000000001', '33333333-3333-3333-3333-333333333333', 'Bàn ghế ăn');
INSERT INTO items (id, category_id, name) VALUES ('33333333-3333-3333-3333-000000000002', '33333333-3333-3333-3333-333333333333', 'Tủ quần áo');
INSERT INTO items (id, category_id, name) VALUES ('33333333-3333-3333-3333-000000000003', '33333333-3333-3333-3333-333333333333', 'Giường ngủ');

-- Insert Items for Giặt ủi & Làm sạch
INSERT INTO items (id, category_id, name) VALUES ('44444444-4444-4444-4444-000000000001', '44444444-4444-4444-4444-444444444444', 'Máy giặt');
INSERT INTO items (id, category_id, name) VALUES ('44444444-4444-4444-4444-000000000002', '44444444-4444-4444-4444-444444444444', 'Máy hút bụi');
