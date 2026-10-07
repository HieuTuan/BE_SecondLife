# Admin quản lý permission

Tất cả API bên dưới yêu cầu `Authorization: Bearer <accessToken>`, role `ADMIN` và authority `ADMIN_RBAC_MANAGE`. Kiểm tra permission luôn bật; biến cũ `APP_SECURITY_PERMISSIONS_ENABLED` không còn tắt được authorization.

Các API quản lý catalog permission, permission của role và role của user trong tài liệu này chỉ dành cho ADMIN có `ADMIN_RBAC_MANAGE`. Điều này không có nghĩa mọi permission chỉ dành cho ADMIN: permission nghiệp vụ như `CREDIT_READ_SELF` dành cho SELLER, `SELLER_VERIFICATION_SUBMIT` dành cho BUYER và `USER_READ_ANY` có thể cấp cho STAFF.

Main Flow 1 bổ sung `LISTING_VALUATION_SELF`, gắn mặc định cho SELLER/ADMIN và có `assignableRoles: ["SELLER"]`.
Quyền này cho định giá/xem lịch sử bài của chính người gọi; chi tiết API và JSON test ở [MAIN_FLOW_1_API_GUIDE.md](MAIN_FLOW_1_API_GUIDE.md).

Quản lý danh mục/loại sản phẩm bổ sung `ADMIN_CATALOG_MANAGE`, chỉ cấp cho ADMIN và không cho gán sang các role khác. GET catalog dành cho tài khoản đã đăng nhập; POST/PUT/DELETE yêu cầu cả ADMIN và quyền này. Xem API và JSON test ở [CATEGORY_ITEM_API_GUIDE.md](CATEGORY_ITEM_API_GUIDE.md).

## Tích hợp FE và luồng màn hình

### Kết nối API

Backend local: `http://localhost:8080`. Đăng nhập qua `POST /api/auth/login`:

```json
{
  "email": "<email tài khoản admin>",
  "password": "<mật khẩu>"
}
```

Lấy token từ `response.data.accessToken`. Response đăng nhập có `data.roles` và `data.permissions`; FE có thể dùng để hiển thị màn hình quản trị khi có cả `ADMIN` và `ADMIN_RBAC_MANAGE`. Backend vẫn kiểm tra quyền ở mỗi request; việc ẩn nút trên FE không thay thế authorization.

Gửi các request quản trị với header:

```http
Authorization: Bearer <accessToken>
Accept: application/json
Content-Type: application/json
```

`Content-Type` cần khi có JSON body. Response thành công dùng `{ "success": true, "message": "...", "data": ... }`; FE đọc dữ liệu trong `data`. Khi thất bại, hiển thị `message` và `errors` nếu có.

Nếu FE chạy trên origin khác backend, cấu hình origin đó trong `CORS_ALLOWED_ORIGINS` hoặc dùng dev proxy. Giao diện React thử nghiệm trong `permission-ui` chuyển tiếp `/api` sang `http://127.0.0.1:8080`, nên có thể dùng URL tương đối như `/api/admin/permissions`. Xem [hướng dẫn chạy Permission Lab](../permission-ui/README.md).

### Màn hình 1: Danh mục permission

1. Khi mở trang, gọi `GET /api/admin/permissions` và `GET /api/admin/roles`.
2. Hiển thị mã, tên, mô tả, loại hệ thống/tùy chỉnh và `assignableRoles`.
3. Khi chọn một dòng, có thể tải chi tiết qua `GET /api/admin/permissions/{permissionCode}` để cập nhật form.
4. Nếu `systemPermission=true`, cho sửa tên/mô tả; khóa chính sách gán role và nút xóa. Khi lưu, bỏ `assignableRoles` khỏi JSON.
5. Nếu `systemPermission=false`, cho sửa chính sách role của permission tùy chỉnh cũ. Trước khi bỏ một role khỏi chính sách, cần thu hồi permission khỏi role đó.
6. Lưu bằng PUT rồi tải lại catalog để hiển thị dữ liệu backend đã lưu.

Ví dụ hợp lệ cho `PUT /api/admin/permissions/ADMIN_COMMISSION_MANAGE`:

```json
{
  "name": "Quản lý hoa hồng",
  "description": "Quản lý chính sách hoa hồng"
}
```

| Field | FE gửi / hiển thị |
| --- | --- |
| `code` | Mã định danh trong URL, chỉ đọc khi cập nhật; đổi tên hiển thị không đổi mã kiểm tra quyền |
| `name` | Bắt buộc, không trắng, tối đa 100 ký tự; backend trim khoảng trắng đầu/cuối |
| `description` | Tối đa 255 ký tự; bỏ field hoặc gửi `null` sẽ xóa mô tả, nên gửi lại nội dung hiện tại nếu muốn giữ |
| `assignableRoles` | Những role được phép nhận permission, không phải role đã được cấp quyền; bỏ field hoặc gửi `null` khi PUT để giữ chính sách |
| `systemPermission` | Chỉ có trong response; dùng để khóa sửa chính sách và xóa permission hệ thống |

`ADMIN` không xuất hiện trong `assignableRoles` vì quyền ADMIN được quản lý cố định. `assignableRoles: []` không có nghĩa ADMIN không có quyền; nó có nghĩa không cho gán permission đó cho các role thông thường. Với `ADMIN_COMMISSION_MANAGE`, gửi `["ADMIN"]` trả 400; gửi `["BUYER"]` trả 409 vì thay chính sách hệ thống. Mã này hiện chưa có API nghiệp vụ commission triển khai; sửa metadata không tạo chức năng commission.

Nút POST nên có tên **Khởi tạo mã backend còn thiếu**, không hiển thị như chức năng tạo permission tùy ý. Nó chỉ nhận mã đã được backend định nghĩa và có nghiệp vụ triển khai, chưa có trong DB. Ví dụ `CREDIT_READ_SELF` ở phần dưới chỉ dùng nếu mã này đang thiếu; nếu đã tồn tại sẽ trả 409. Không có luồng FE tạo mã tùy ý như `DANG_XUAT_KHOI_HE_THONG`.

### Màn hình 2: Permission của role

1. Tải danh sách role qua `GET /api/admin/roles`; chọn role và tải lại chi tiết qua `GET /api/admin/roles/{roleCode}`.
2. Giữ nguyên snapshot `data.permissionCodes` để dùng làm `expectedPermissionCodes` khi lưu.
3. Hiển thị checkbox permission. Chỉ cho cấp permission khi `permission.assignableRoles` chứa role đang chọn. Các quyền đang gán vẫn cần hiển thị để kiểm tra và thu hồi khi phù hợp với chính sách backend.
4. Nếu `data.editable=false` (ADMIN), hiển thị chỉ đọc và khóa nút lưu.
5. Gửi toàn bộ tập quyền mong muốn bằng `PUT /api/admin/roles/{roleCode}/permissions`.
6. Thành công: tải lại role và lịch sử. Thay đổi có hiệu lực từ request tiếp theo với token hiện có.
7. Nếu trả 409 do snapshot cũ: tải lại dữ liệu và để admin xem lại lựa chọn; không tự ghi đè bằng snapshot mới.

Ví dụ `PUT /api/admin/roles/STAFF/permissions` (chỉ hợp lệ nếu GET vừa trả đúng tập quyền minh họa này):

```json
{
  "expectedPermissionCodes": ["PROFILE_READ_SELF", "USER_READ_ANY"],
  "permissionCodes": ["PROFILE_READ_SELF"]
}
```

Request này thu hồi `USER_READ_ANY`. Đây là thay thế toàn bộ, không phải thêm/bớt từng phần; FE phải gửi cả các quyền muốn giữ. Không sao chép cố định tập quyền ví dụ vào code FE.

Lịch sử: `GET /api/admin/roles/STAFF/permission-changes?page=0&size=20`. Đọc `data.items`, `data.page`, `data.totalPages`, `data.totalElements`, `data.isLast`; mỗi item có `permissionCode`, `action`, `changedBy`, `changedAt`.

### Màn hình 3: Role của user

1. Chọn user hoặc nhập UUID, gọi `GET /api/v1/admin/users/{userId}/roles`.
2. Hiển thị `data.roleCodes` bằng checkbox và `data.permissionCodes` dưới dạng quyền hiệu lực chỉ đọc.
3. Giữ `data.roleCodes` làm `expectedRoleCodes`, gửi toàn bộ role mong muốn bằng PUT vào cùng URL.
4. Sau khi lưu, tải lại role và lịch sử; thông báo user cần đăng nhập lại vì token cũ mất hiệu lực.
5. Nếu 409, tải lại và yêu cầu admin xem lại. Các bảo vệ ADMIN và điều kiện duyệt SELLER vẫn do backend kiểm tra.

```json
{
  "expectedRoleCodes": ["BUYER"],
  "roleCodes": ["BUYER", "STAFF"]
}
```

Ví dụ chỉ hợp lệ khi user hiện có đúng role BUYER. Lịch sử: `GET /api/v1/admin/users/{userId}/role-changes?page=0&size=20`; item có `roleCode`, `action`, `changedBy`, `changedAt`.

Nếu FE dùng danh sách user, `GET /api/v1/admin/users?page=0&size=20` yêu cầu `USER_READ_ANY`; API danh sách user không thuộc guard ADMIN + `ADMIN_RBAC_MANAGE` của nhóm quản lý RBAC. Không suy luận mọi API có chữ `/admin` đều có cùng điều kiện quyền.

### Xử lý HTTP trên FE

| HTTP | Hành vi FE |
| --- | --- |
| 200 / 201 | Hiển thị thành công, đọc `data`, tải lại dữ liệu liên quan |
| 400 | Hiển thị lỗi nhập liệu/chính sách; không gửi lại nguyên request lỗi |
| 401 | Yêu cầu đăng nhập lại hoặc cấp token hợp lệ; token hết hạn cũng thuộc trường hợp này |
| 403 | Hiển thị không có quyền truy cập; tài khoản phải có ADMIN và ADMIN_RBAC_MANAGE cho nhóm RBAC |
| 404 | Thông báo đối tượng không tồn tại và tải lại danh sách |
| 409 | Đọc `message`: nếu snapshot cũ thì tải lại; nếu chính sách hệ thống cố định hoặc permission còn đang gán thì hướng dẫn xử lý điều kiện đó |

FE có thể hiển thị JSON request/response và HTTP status để thử API. Không hiển thị access token hoặc mật khẩu trong log request.

## Danh mục permission

| Method | URL | Chức năng | Thành công |
| --- | --- | --- | --- |
| GET | `/api/admin/permissions` | Danh sách permission và role được phép gán | 200 |
| GET | `/api/admin/permissions/{permissionCode}` | Chi tiết permission | 200 |
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

Body cập nhật chính sách của permission tùy chỉnh cũ (không dùng mẫu này để thay chính sách permission hệ thống):

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
