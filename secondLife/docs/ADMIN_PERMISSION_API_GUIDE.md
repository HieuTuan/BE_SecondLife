# Admin quản lý permission

Tất cả API bên dưới yêu cầu `Authorization: Bearer <accessToken>`, role `ADMIN` và authority `ADMIN_RBAC_MANAGE`. Kiểm tra permission luôn bật; biến cũ `APP_SECURITY_PERMISSIONS_ENABLED` không còn tắt được authorization.

## Danh mục permission

| Method | URL | Chức năng | Thành công |
| --- | --- | --- | --- |
| GET | `/api/admin/permissions` | Danh sách permission và role được phép gán | 200 |
| GET | /api/admin/permissions/{permissionCode} | Chi tiết permission | 200 |
| POST | `/api/admin/permissions` | Khởi tạo permission đã được backend định nghĩa và triển khai, nếu chưa có trong DB | 201, có header `Location` |
| PUT | `/api/admin/permissions/{permissionCode}` | Sửa tên, mô tả, role được phép gán | 200 |
| DELETE | `/api/admin/permissions/{permissionCode}` | Xóa permission tùy chỉnh chưa gán vào role | 200 |

Body tạo permission:

```json
{
  "code": "CREDIT_READ_SELF",
  "name": "Read Own Credits",
  "description": "Read own credit balances and pricing",
  "assignableRoles": ["SELLER"]
}
```

`code` phải có trong `PermissionCode`, được đánh dấu đã có nghiệp vụ triển khai và chưa có trong database. Các mã tùy ý như `REPORT_EXPORT`, `ROLE_ADMIN` hoặc mã dành cho chức năng chưa triển khai trả 400. Mã đã có trả 409. Các migration thường đã tạo sẵn toàn bộ permission cần dùng, nên luồng admin chính là đọc catalog rồi gán/thu hồi quyền; POST được giữ để tương thích và khởi tạo mã hệ thống bị thiếu. Thêm chức năng mới cần bổ sung mã, guard và migration trong backend trước.

`name` bắt buộc, không được trắng và dài tối đa 100 ký tự. `description` không bắt buộc, tối đa 255 ký tự. `assignableRoles` bắt buộc khi tạo và phải đúng chính sách cố định của permission hệ thống, ví dụ `CREDIT_READ_SELF` chỉ cho `SELLER`. ADMIN không xuất hiện trong `assignableRoles` vì bộ quyền ADMIN cố định.

Body cập nhật:

```json
{
  "name": "Export operational reports",
  "description": "Xuất báo cáo chi tiết",
  "assignableRoles": ["STAFF", "INSPECTOR"]
}
```

Nếu bỏ `assignableRoles` hoặc truyền `null` khi cập nhật, chính sách gán role được giữ nguyên. Nếu bỏ `description` hoặc truyền `null`, mô tả được xóa. Response dùng envelope `ApiResponse` hiện tại; `data` có `code`, `name`, `description`, `assignableRoles`, `systemPermission`.

- Permission hệ thống (`systemPermission=true`) cho phép sửa tên/mô tả; chính sách gán role cố định và không được xóa.
- Permission tùy chỉnh đang được gán không được xóa. Cần thu hồi khỏi mọi role trước.
- Muốn bỏ một role khỏi `assignableRoles`, cần thu hồi permission khỏi role đó trước.
- Permission custom từ dữ liệu cũ vẫn được đọc, sửa metadata/chính sách và thu hồi/xóa. Backend không tự tạo API hay nghiệp vụ từ các mã này.

## Gán và thu hồi permission cho role

| Method | URL | Chức năng |
| --- | --- | --- |
| GET | `/api/admin/roles` | Danh sách role và quyền hiện tại |
| GET | `/api/admin/roles/{roleCode}` | Chi tiết role, `editable` và `permissionCodes` |
| PUT | `/api/admin/roles/{roleCode}/permissions` | Thay thế toàn bộ quyền của role |
| GET | `/api/admin/roles/{roleCode}/permission-changes` | Lịch sử cấp/thu hồi, có phân trang |

Frontend tải danh mục và role, hiển thị permission theo `assignableRoles`, rồi gửi bộ quyền đã tải và bộ quyền mong muốn:

```json
{
  "expectedPermissionCodes": ["PROFILE_READ_SELF"],
  "permissionCodes": ["PROFILE_READ_SELF", "USER_READ_ANY"]
}
```

Ví dụ trên chỉ hợp lệ nếu quyền hiện tại của role đúng bằng `expectedPermissionCodes`. Luôn dùng bộ quyền thật từ GET; đây là thao tác thay thế toàn bộ, nên mọi quyền không có trong `permissionCodes` sẽ bị thu hồi. Gửi `permissionCodes: []` để thu hồi toàn bộ quyền của một role có thể sửa.

Role `ADMIN` có `editable=false` và bộ quyền cố định để tránh khóa quyền quản trị. Những permission chỉ dành cho admin không được gán cho role khác. Permission mới dùng cùng luồng gán/thu hồi và được ghi lịch sử `GRANT`/`REVOKE`, kèm admin thực hiện và thời điểm. Xóa permission tùy chỉnh không xóa lịch sử này. JWT được kiểm tra lại authority từ database ở mỗi request, nên thay đổi quyền có hiệu lực từ request tiếp theo.

Luồng xóa trên frontend: tải các role đang có permission → thu hồi khỏi từng role bằng PUT và bộ quyền vừa tải → DELETE permission → tải lại danh mục. Khi cập nhật chính sách role của permission, cũng cần thu hồi khỏi các role bị loại trước khi PUT permission.

## Xử lý lỗi và migration

| HTTP | Ý nghĩa / xử lý |
| --- | --- |
| 400 | Dữ liệu sai, mã permission không có trong danh mục, role không hợp lệ hoặc gán sai chính sách |
| 401 | Chưa đăng nhập hoặc token không hợp lệ |
| 403 | Thiếu role admin hoặc thiếu `ADMIN_RBAC_MANAGE` |
| 404 | Permission hoặc role không tồn tại |
| 409 | Trùng code, thay đổi permission hệ thống bị cấm, permission còn được gán, hoặc bộ quyền role đã thay đổi; tải lại dữ liệu trước khi lưu |

Migration V16 bổ sung chính sách permission custom; V17 điều chỉnh thứ tự khóa liên quan constraint. V18 bổ sung quyền chat/media/kiểm định và audit role user. Flyway chạy migration khi khởi động; không sửa migration đã áp dụng và không cần bật seeder để sử dụng các API. Kiểm thử dùng PostgreSQL riêng, không tự áp dụng vào database ứng dụng đang có dữ liệu.

## Gán và thu hồi role của user

| Method | URL | Chức năng |
| --- | --- | --- |
| GET | `/api/v1/admin/users/{userId}/roles` | Role đã gán và permission hiệu lực |
| PUT | `/api/v1/admin/users/{userId}/roles` | Thay thế toàn bộ role của user |
| GET | `/api/v1/admin/users/{userId}/role-changes` | Audit phân trang |

Ví dụ thêm STAFF cho user đang chỉ có BUYER:

```json
{
  "expectedRoleCodes": ["BUYER"],
  "roleCodes": ["BUYER", "STAFF"]
}
```

Luôn tải bộ role hiện tại trước khi PUT; không khớp `expectedRoleCodes` trả 409. `roleCodes: []` thu hồi toàn bộ role nếu không vi phạm bảo vệ ADMIN. Không cho tự gỡ ADMIN, gỡ ADMIN hoạt động cuối cùng hoặc thêm SELLER khi chưa có hồ sơ APPROVED. Role không hợp lệ trả 400, user không tồn tại trả 404. Thay đổi có audit và transaction; no-op không tăng phiên bản token hay tạo audit.

Thay **permission của role** có hiệu lực với JWT hiện có từ request tiếp theo. Thay **role của user** tăng `tokenVersion`, thu hồi mọi refresh token; JWT cũ trả 401 và user phải đăng nhập lại. Frontend tải lại `/api/users/me` để cập nhật trạng thái quyền sau khi đăng nhập.

Xem [báo cáo RBAC đầy đủ](RBAC_COMPLETION_REPORT.md) để biết mapping, API được bảo vệ và trace request.
