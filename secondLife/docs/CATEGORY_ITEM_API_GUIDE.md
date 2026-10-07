# Categories và Items — hướng dẫn FE và JSON test

Base URL local: `http://localhost:8080`. `Item` là **loại sản phẩm** để chọn khi đăng tin (ví dụ Sofa, Refrigerator), không phải bài đăng bán hàng của người dùng.

## Dữ liệu tự động khi backend khởi động

| Category | Items mặc định |
| --- | --- |
| Living Room | Sofa, Coffee Table, TV Stand, Armchair, Bookshelf, Floor Lamp |
| Kitchen | Refrigerator, Microwave Oven, Rice Cooker, Induction Cooker, Blender, Dining Table |
| Bedroom | Bed Frame, Mattress, Wardrobe, Bedside Table, Dressing Table, Desk |
| Bathroom & Sanitation | Water Heater, Bathroom Cabinet, Washbasin, Mirror, Shower Set, Toilet |
| Balcony & Laundry | Washing Machine, Clothes Dryer, Drying Rack, Laundry Basket, Iron, Balcony Table |
| Other | Vacuum Cleaner, Fan, Air Purifier, Storage Cabinet, Shoe Rack, Tool Box |

Mỗi lần startup, backend bổ sung các mục mặc định còn thiếu: tổng cộng 6 categories và 36 item types trên DB mới. Các danh mục/loại sản phẩm khác đã có vẫn được giữ. Nếu đã có mục cùng tên trong đúng danh mục, seeder sử dụng ID cũ. Seeder dùng khóa nội bộ `seed_key`, giữ ID, tên/mô tả và việc chuyển danh mục đã được ADMIN sửa; không nhân bản khi restart hoặc nhiều instance cùng startup.

**Nếu xóa một mục mặc định chưa được sử dụng, startup tiếp theo sẽ bổ sung lại mục đó.** Mục tự tạo qua CRUD không tự được tạo lại. Để tắt riêng seed catalog, đặt `APP_CATALOG_SEED_ENABLED=false`; mặc định là bật, độc lập với `SEEDER_ENABLED` của tài khoản/RBAC. Flyway phải được bật để áp dụng migration V21 trước khi seed.

## Quyền và response

Tất cả API yêu cầu đăng nhập và header:

```http
Authorization: Bearer <accessToken>
Content-Type: application/json
```

GET: tài khoản đã đăng nhập có thể đọc. POST/PUT/DELETE: yêu cầu role `ADMIN` **và** permission `ADMIN_CATALOG_MANAGE`. Migration cấp permission này cho role ADMIN; đây là permission hệ thống chỉ dành cho ADMIN. Đăng nhập lại sau khi backend cập nhật để lấy token/quyền mới.

Response thành công của nhóm API này là **object/array trực tiếp**, không bọc trong `data`. Response lỗi theo format chung của backend (`success`, `message`, `errors`, `path`, `timestamp`).

## API categories

| Method | URL | Thành công |
| --- | --- | --- |
| GET | `/api/v1/categories` | 200, mảng categories |
| GET | `/api/v1/categories/{categoryId}` | 200, một category |
| POST | `/api/v1/categories` | 201, category vừa tạo |
| PUT | `/api/v1/categories/{categoryId}` | 200, category sau sửa |
| DELETE | `/api/v1/categories/{categoryId}` | 204, không có body |

POST hoặc PUT:

```json
{
  "name": "Home Office",
  "description": "Đồ dùng cho phòng làm việc"
}
```

- `name`: bắt buộc, không được trắng, tối đa 255 ký tự. Backend chuẩn hóa Unicode, bỏ khoảng trắng đầu/cuối và gộp khoảng trắng liên tiếp. Tên trùng không phân biệt hoa/thường bị trả 409.
- `description`: tùy chọn, tối đa 2.000 ký tự; bỏ field hoặc gửi `null` khi PUT sẽ xóa mô tả. Gửi lại mô tả hiện tại nếu muốn giữ.
- `id`: backend tạo UUID; PUT/DELETE dùng ID trong URL, không gửi trong body. GET có ID để FE chọn danh mục.

Ví dụ response:

```json
{
  "id": "<categoryId backend trả về>",
  "name": "Home Office",
  "description": "Đồ dùng cho phòng làm việc"
}
```

## API items

| Method | URL | Thành công |
| --- | --- | --- |
| GET | `/api/v1/items` | 200, mảng tất cả items |
| GET | `/api/v1/items?categoryId={categoryId}` | 200, items của category |
| GET | `/api/v1/items/category/{categoryId}` | 200, giữ route đọc hiện có |
| GET | `/api/v1/items/{itemId}` | 200, một item |
| POST | `/api/v1/items` | 201, item vừa tạo |
| PUT | `/api/v1/items/{itemId}` | 200, item sau sửa |
| DELETE | `/api/v1/items/{itemId}` | 204, không có body |

POST hoặc PUT (thay placeholder bằng UUID lấy từ GET/POST categories):

```json
{
  "categoryId": "<UUID category thực tế>",
  "name": "Office Chair"
}
```

- `categoryId`: bắt buộc, UUID của category đang tồn tại. PUT phải gửi field này dù chỉ sửa tên. Chỉ chuyển category khi item chưa được bài đăng/template sử dụng.
- `name`: bắt buộc, không trắng, tối đa 255 ký tự; chuẩn hóa giống tên category. Tên item chỉ phải duy nhất **trong cùng category**.
- `id`: UUID do backend sinh, dùng trong URL GET/PUT/DELETE.
- `category`: response có object category để tương thích route đọc cũ; đồng thời có `categoryId` để FE dùng trực tiếp.

Ví dụ response:

```json
{
  "id": "<itemId backend trả về>",
  "name": "Office Chair",
  "categoryId": "<categoryId backend trả về>",
  "category": {
    "id": "<categoryId backend trả về>",
    "name": "Home Office",
    "description": "Đồ dùng cho phòng làm việc"
  }
}
```

## Luồng FE và thứ tự test

1. Đăng nhập ADMIN, lấy `data.accessToken` từ `POST /api/auth/login`.
2. GET categories để hiển thị 6 danh mục mặc định cùng các danh mục hiện có.
3. Chọn category → GET `/api/v1/items/category/{categoryId}` → hiển thị loại sản phẩm tương ứng. Khi tạo draft, gửi `categoryId` và `itemId` lấy từ hai danh sách này theo [MAIN_FLOW_1_API_GUIDE.md](MAIN_FLOW_1_API_GUIDE.md).
4. Màn hình quản trị: POST category Home Office bằng JSON trên; lưu `id` từ response.
5. POST item Office Chair với `categoryId` vừa tạo; lưu `itemId`.
6. PUT category để sửa tên/mô tả; PUT item để sửa tên (gửi lại categoryId).
7. DELETE category khi còn item → 409. DELETE item trước → 204, sau đó DELETE category → 204. Tải lại danh sách sau mỗi thao tác thành công; không parse JSON ở response 204.
8. Đổi sang token BUYER/SELLER: GET được, các thao tác thay đổi bị 403.
9. Restart backend: các mục mặc định không bị nhân bản, nội dung đã sửa được giữ. Các mục mặc định đã xóa được bổ sung lại.

## Lỗi cần xử lý trên FE

| HTTP | Trường hợp |
| --- | --- |
| 400 | JSON không hợp lệ, UUID sai format, thiếu categoryId, tên trắng/quá dài |
| 401 | Thiếu token hoặc token không hợp lệ/hết hạn |
| 403 | Không có role ADMIN hoặc thiếu ADMIN_CATALOG_MANAGE khi thay đổi dữ liệu |
| 404 | Category/item không tồn tại; GET items theo category không tồn tại cũng trả 404 |
| 409 | Trùng tên; xóa category còn items/bài đăng/template; xóa item đang được dùng; chuyển category của item đang được dùng |

Để giữ lịch sử và liên kết bài đăng, phải xử lý dữ liệu đang tham chiếu trước khi xóa. Backend có kiểm tra trong service và khóa ngoại PostgreSQL cho các ghi mới; migration giữ lại dữ liệu cũ thay vì xóa các tham chiếu lịch sử.
