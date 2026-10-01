# Admin quản lý permission

Tất cả API bên dưới yêu cầu `Authorization: Bearer <accessToken>`, role `ADMIN` và authority `ADMIN_RBAC_MANAGE`. Nhóm API này vẫn kiểm tra quyền khi `APP_SECURITY_PERMISSIONS_ENABLED=false`.

## Danh mục permission

| Method | URL | Chức năng | Thành công |
| --- | --- | --- | --- |
| GET | `/api/admin/permissions` | Danh sách permission và role được phép gán | 200 |
| GET | `/api/admin/permissions/{permissionCode}` | Chi tiết permission | 200 |
| POST | `/api/admin/permissions` | Tạo permission tùy chỉnh | 201, có header `Location` |
| PUT | `/api/admin/permissions/{permissionCode}` | Sửa tên, mô tả, role được phép gán | 200 |
| DELETE | `/api/admin/permissions/{permissionCode}` | Xóa permission tùy chỉnh chưa gán vào role | 200 |

Body tạo permission:

```json
{
  "code": "REPORT_EXPORT",
  "name": "Export reports",
  "description": "Xuất báo cáo vận hành",
  "assignableRoles": ["STAFF"]
}
```

`code` dài tối đa 100 ký tự, bắt đầu bằng chữ cái in hoa, chỉ gồm `A-Z`, `0-9`, `_` và không đổi sau khi tạo. Không được trùng mã đã có hoặc mã trong `PermissionCode`; tiền tố `ROLE_` và `ADMIN_` được dành riêng để tránh giả mạo role/quyền quản trị.

`name` bắt buộc, không được trắng và dài tối đa 100 ký tự. `description` không bắt buộc, tối đa 255 ký tự. `assignableRoles` bắt buộc khi tạo, có thể là mảng rỗng; chỉ nhận role đã được khởi tạo trong `BUYER`, `SELLER`, `INSPECTOR`, `STAFF`, `INSPECTION_CENTER`.

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
- Tạo permission chỉ tạo authority có thể cấp cho role. Để bảo vệ chức năng mới, backend phải kiểm tra authority tương ứng, ví dụ `@PreAuthorize("hasRole('STAFF') and hasAuthority('REPORT_EXPORT')")`.

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
  "expectedPermissionCodes": ["PROFILE_READ_SELF", "ROLE_READ"],
  "permissionCodes": ["PROFILE_READ_SELF", "ROLE_READ", "REPORT_EXPORT"]
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

Migration `V16__permission_catalog_assignable_roles.sql` bổ sung bảng chính sách gán role cho permission tùy chỉnh. `V17__align_permission_policy_constraints.sql` điều chỉnh constraint để cập nhật chính sách không xung đột thứ tự khóa khi cấp quyền. Flyway chạy migration khi khởi động; không sửa các migration đã áp dụng và không cần bật seeder để sử dụng các API.
