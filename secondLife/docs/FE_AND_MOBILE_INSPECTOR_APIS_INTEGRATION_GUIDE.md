# TÀI LIỆU HƯỚNG DẪN TÍCH HỢP API KỸ THUẬT VIÊN (INSPECTOR) CHO FE & MOBILE APP

> **Phiên bản:** 1.0.0  
> **Áp dụng cho:** `MB_SecondLife_Inspector` (Flutter/React Native), Frontend Web Portal, Mobile Apps (`MB_SecondLife_Buyer`, `MB_SecondLife_Seller`)  
> **Branch Backend:** `dev`  
> **Cập nhật:** 2026-10-16  

---

## 📌 Tổng Quan Các Thay Đổi & Nâng Cấp Backend

Hệ thống đã bổ sung và hoàn thiện 100% các API phục vụ nghiệp vụ Kỹ thuật viên (Inspector) theo yêu cầu kết nối di động:

1. **`GET /api/v1/inspector/stats`**: Lấy thống kê hiệu suất KPI, phân loại số đơn hoàn thành (Đạt / Không đạt), doanh thu công thù lao, phụ cấp di chuyển và số tiền đang chờ đối soát theo tháng/năm.
2. **`GET /api/v1/inspector/payouts`**: Lấy danh sách lịch sử đối soát thù lao, phụ cấp chi tiết theo từng đơn kiểm định (hỗ trợ phân trang `Pageable`).
3. **Cập nhật `GET /api/v1/users/me` (Profile)**: Bổ sung 4 trường nghiệp vụ chuyên biệt cho tài khoản KTV (`badgeCode`, `stationHubId`, `stationHubName`, `certificationTitle`), giữ an toàn `null` đối với Buyer/Seller/Admin.

---

## 1. API Thống Kê Hiệu Suất & Thu Nhập KTV

### 1.1. Thông tin Endpoint
- **URL:** `/api/v1/inspector/stats`
- **Method:** `GET`
- **Quyền hạn (Security):** Yêu cầu Access Token (Bearer JWT) có quyền `INSPECTION_ORDER_READ_SELF` (Tài khoản role `INSPECTOR`).
- **Query Parameters (Tùy chọn):**
  - `month` *(int, 1-12, optional)*: Tháng cần xem thống kê (mặc định tháng hiện tại).
  - `year` *(int, >=2000, optional)*: Năm cần xem thống kê (mặc định năm hiện tại).

### 1.2. Schema Response (`InspectorStatsResponse`)
```json
{
  "inspectorId": "8f3b26c2-4e67-4e92-b435-01e4a5d3f9b1",
  "inspectorName": "Trần Kỹ Thuật",
  "badgeCode": "SL-TECH-F9B1",
  "skillLevel": "Kỹ thuật viên Thẩm định Bậc 3",
  "completedJobs": 18,
  "passedJobs": 15,
  "failedJobs": 3,
  "averageRating": 4.9,
  "onTimeRate": 98.5,
  "monthlyEarnings": 4250000,
  "laborFeeEarned": 3400000,
  "travelAllowanceEarned": 850000,
  "pendingSettlementAmount": 650000
}
```

### 1.3. Diễn giải chi tiết từng trường
| Tên trường | Kiểu dữ liệu | Mô tả & Cách hiển thị trên UI |
| :--- | :--- | :--- |
| `inspectorId` | `UUID` | ID định danh của Kỹ thuật viên |
| `inspectorName` | `String` | Họ tên hiển thị của KTV |
| `badgeCode` | `String` | Mã thẻ/huy hiệu chuyên môn (Ví dụ: `SL-TECH-F9B1`) |
| `skillLevel` | `String` | Cấp bậc chuyên môn (Ví dụ: `Kỹ thuật viên Thẩm định Bậc 3`) |
| `completedJobs` | `int` | Tổng số đơn kiểm định đã hoàn thành trong tháng (`PASSED`, `FAILED`, `COMPLETED`) |
| `passedJobs` | `int` | Số đơn đạt chuẩn chất lượng (`PASSED`, `COMPLETED`) |
| `failedJobs` | `int` | Số đơn không đạt chuẩn (`FAILED`) |
| `averageRating` | `double` | Điểm đánh giá sao trung bình (Ví dụ: `4.9 / 5.0 ★`) |
| `onTimeRate` | `double` | Tỷ lệ đúng giờ lịch hẹn (Ví dụ: `98.5%`) |
| `monthlyEarnings` | `BigDecimal` | **Tổng thu nhập tháng** = `laborFeeEarned + travelAllowanceEarned` (VND) |
| `laborFeeEarned` | `BigDecimal` | Tổng tiền công kiểm định máy tháng này (VND) |
| `travelAllowanceEarned` | `BigDecimal` | Tổng phụ cấp xăng xe/di chuyển tháng này (VND) |
| `pendingSettlementAmount` | `BigDecimal` | Tiền công của các đơn đang xử lý (`INSPECTING`, `SCHEDULED`, `DISPATCHED`, `SHIPPING_TO_CENTER`) |

### 1.4. Hướng dẫn tích hợp trên UI Mobile App (`MB_SecondLife_Inspector`)
- **Màn hình Dashboard (Trang chủ KTV):**
  - **Header Card:** Hiển thị `inspectorName`, `badgeCode` (dưới dạng badge màu xanh lá hoặc viền vàng đồng) và `skillLevel`.
  - **KPI Cards (Hàng ngang):**
    - Card 1: **Tổng đơn hoàn thành** (`completedJobs`) kèm tỷ lệ Đạt/Không đạt (`passedJobs` / `failedJobs`).
    - Card 2: **Đánh giá & Đúng giờ** (`averageRating` ★ và `onTimeRate`%).
  - **Wallet & Earnings Card:**
    - Số dư lớn nổi bật: `monthlyEarnings` formatted VND (Ví dụ: `4.250.000 ₫`).
    - Phân tích chi tiết:
      - 🔧 Tiền công thẩm định: `laborFeeEarned` (Ví dụ: `3.400.000 ₫`)
      - 🛵 Phụ cấp di chuyển: `travelAllowanceEarned` (Ví dụ: `850.000 ₫`)
      - ⏳ Đang chờ quyết toán: `pendingSettlementAmount` (Ví dụ: `650.000 ₫`)
  - **Bộ lọc tháng/năm:** Cho phép vuốt ngang hoặc chọn dropdown tháng `month=10&year=2026`.

---

## 2. API Lịch Sử Đối Soát Thù Lao KTV

### 2.1. Thông tin Endpoint
- **URL:** `/api/v1/inspector/payouts`
- **Method:** `GET`
- **Quyền hạn (Security):** `INSPECTION_ORDER_READ_SELF` (Tài khoản `INSPECTOR`).
- **Query Parameters (Phân trang):**
  - `page` *(int, default 0)*: Số trang (0-indexed).
  - `size` *(int, default 10 hoặc 20)*: Số bản ghi trên mỗi trang.
  - `sort` *(string, optional)*: Sắp xếp (mặc định mới nhất trước).

### 2.2. Schema Response (`Page<InspectorPayoutResponse>`)
```json
{
  "content": [
    {
      "payoutId": "PO-E10F92A1",
      "orderId": "e10f92a1-6380-457f-b258-005bf394e101",
      "postTitle": "iPhone 14 Pro Max 256GB Tím Deep Purple",
      "orderType": "AT_HOME",
      "completedAt": "2026-10-15T08:30:00Z",
      "laborFee": 150000,
      "travelFee": 35000,
      "totalPayout": 185000,
      "status": "SETTLED"
    },
    {
      "payoutId": "PO-99BA01CC",
      "orderId": "99ba01cc-1234-4321-abcd-987654321000",
      "postTitle": "MacBook Pro M2 16GB 512GB Space Gray",
      "orderType": "AT_CENTER",
      "completedAt": "2026-10-14T14:15:00Z",
      "laborFee": 200000,
      "travelFee": 0,
      "totalPayout": 200000,
      "status": "SETTLED"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10
  },
  "totalElements": 18,
  "totalPages": 2,
  "last": false,
  "first": true
}
```

### 2.3. Diễn giải chi tiết từng trường
| Tên trường | Kiểu dữ liệu | Mô tả |
| :--- | :--- | :--- |
| `payoutId` | `String` | Mã đối soát thu nhập (Ví dụ: `PO-E10F92A1`) |
| `orderId` | `UUID` | ID đơn kiểm định (dùng để điều hướng đến chi tiết đơn) |
| `postTitle` | `String` | Tên sản phẩm được kiểm định |
| `orderType` | `String` | `AT_HOME` (Kiểm định tận nơi) hoặc `AT_CENTER` (Kiểm định tại trạm) |
| `completedAt` | `Instant` | Thời điểm hoàn tất kiểm định (ISO-8601 UTC) |
| `laborFee` | `BigDecimal` | Tiền công kiểm định máy |
| `travelFee` | `BigDecimal` | Phụ cấp di chuyển (chỉ áp dụng `AT_HOME`, đối với `AT_CENTER` = 0) |
| `totalPayout` | `BigDecimal` | **Tổng thù lao nhận được** = `laborFee + travelFee` |
| `status` | `String` | Trạng thái đối soát: `SETTLED` (Đã quyết toán) hoặc `PENDING` (Chờ quyết toán) |

### 2.4. Hướng dẫn tích hợp trên UI Mobile App
- **Danh sách lịch sử (Infinite Scroll ListView):**
  - Mỗi item card hiển thị:
    - Tiêu đề: `postTitle` (Ví dụ: *"iPhone 14 Pro Max 256GB Tím Deep Purple"*).
    - Tag hình thức: Badge xanh lam `Tận nơi` (`AT_HOME`) hoặc Badge tím `Tại trạm` (`AT_CENTER`).
    - Số tiền nhận được (Màu xanh lá): `+185.000 ₫`.
    - Dòng phụ: Tiền công `150.000 ₫` + Phụ cấp `35.000 ₫`.
    - Trạng thái: Badge xanh `Đã quyết toán` (`SETTLED`).
    - Thời gian hoàn thành: Định dạng `HH:mm - dd/MM/yyyy`.
  - **Hành động Click:** Chuyển sang màn hình chi tiết đơn kiểm định (`/inspector/orders/{orderId}`).

---

## 3. Bổ Sung Thông Tin Nghiệp Vụ KTV Vào `GET /api/v1/users/me`

### 3.1. Thông tin Endpoint
- **URL:** `/api/v1/users/me`
- **Method:** `GET`
- **Quyền hạn (Security):** Bất kỳ người dùng đã đăng nhập (Buyer, Seller, Inspector, Admin).

### 3.2. Cập nhật Model Response (`UserProfileResponse`)
Đã bổ sung thêm 4 trường vào cuối payload:
```json
{
  "id": "8f3b26c2-4e67-4e92-b435-01e4a5d3f9b1",
  "email": "inspector.tech@secondlife.com",
  "fullName": "Trần Kỹ Thuật",
  "phone": "0901234567",
  "avatarUrl": "https://res.cloudinary.com/secondlife/image/upload/avatar.jpg",
  "accountStatus": "ACTIVE",
  "emailVerified": true,
  "roles": ["INSPECTOR"],
  "permissions": ["INSPECTION_ORDER_READ_SELF", "INSPECTION_REPORT_SUBMIT"],
  "createdAt": "2026-01-10T10:00:00Z",
  "updatedAt": "2026-10-15T12:00:00Z",
  "lastLoginAt": "2026-10-16T08:00:00Z",
  "province": "TP. Hồ Chí Minh",
  "district": "TP. Thủ Đức",
  "ward": "Phường Linh Trung",
  "streetAddress": "Khu Công Nghệ Cao",
  "latitude": 10.8500,
  "longitude": 106.7700,
  "gender": "MALE",

  "badgeCode": "SL-TECH-F9B1",
  "stationHubId": "1a2b3c4d-5e6f-7a8b-9c0d-1e2f3a4b5c6d",
  "stationHubName": "Trung Tâm SecondLife - Trạm Khu CNC Thủ Đức",
  "certificationTitle": "Kỹ thuật viên Thẩm định Thiết bị Bậc 3"
}
```

### 3.3. Quy Tắc Null Safety Cho Mobile & Web
- **Khi tài khoản là Kỹ thuật viên (`roles` chứa `INSPECTOR`):**
  - `badgeCode`: Chuỗi định dạng chuẩn `SL-TECH-` + 4 ký tự ID viết hoa.
  - `stationHubId`: ID Trưởng trạm / Trung tâm kiểm định trực thuộc (`user.manager.id`).
  - `stationHubName`: Tên Trung tâm kiểm định trực thuộc (lấy từ profile quản lý trạm).
  - `certificationTitle`: Danh hiệu chứng chỉ nghề (mặc định: *"Kỹ thuật viên Thẩm định Thiết bị Bậc 3"*).
- **Khi tài khoản KHÔNG PHẢI Kỹ thuật viên (Buyer, Seller, Admin):**
  - Cả 4 trường trên đều trả về `null`.
  - Frontend và Mobile App của Buyer/Seller không cần thay đổi logic hiện tại, đảm bảo không bị xung đột hay crash app.

---

## 4. Bảng Tra Cứu Toàn Bộ Bộ API Kỹ Thuật Viên (Full Inspector API Reference)

| STT | Phương thức | Đường dẫn API | Mục đích nghiệp vụ | Quyền hạn |
| :---: | :---: | :--- | :--- | :--- |
| 1 | `GET` | `/api/v1/inspector/stats` | Xem thống kê KPI, số đơn & thu nhập tháng | `INSPECTION_ORDER_READ_SELF` |
| 2 | `GET` | `/api/v1/inspector/payouts` | Xem lịch sử đối soát thù lao theo đơn (phân trang) | `INSPECTION_ORDER_READ_SELF` |
| 3 | `GET` | `/api/v1/inspector/orders` | Danh sách các đơn kiểm định được phân công | `INSPECTION_ORDER_READ_SELF` |
| 4 | `POST` | `/api/v1/inspector/orders/{orderId}/schedule` | Hẹn giờ kiểm định tận nhà (gói `AT_HOME`) | Đã đăng nhập |
| 5 | `GET` | `/api/v1/inspector/orders/{orderId}/checklist` | Lấy checklist danh mục máy để chấm điểm | Đã đăng nhập |
| 6 | `POST` | `/api/v1/inspector/orders/{orderId}/start` | Bắt đầu kiểm tra máy (chuyển sang `INSPECTING`) | `INSPECTION_REPORT_SUBMIT` |
| 7 | `POST` | `/api/v1/inspector/orders/{orderId}/submit-report` | Nộp biên bản, video bằng chứng & checklist kết quả | `INSPECTION_REPORT_SUBMIT` |
| 8 | `POST` | `/api/v1/inspector/orders/{orderId}/generate-seal` | Yêu cầu sinh mã tem niêm phong số chuẩn | `INSPECTION_REPORT_SUBMIT` |
| 9 | `GET` | `/api/v1/users/me` | Lấy thông tin cá nhân & thẻ nghiệp vụ KTV | Đã đăng nhập |

---

## 5. Danh Sách Tệp Mã Nguồn Đã Chỉnh Sửa & Tạo Mới Tại Backend

### 5.1. DTOs Tạo Mới
- `src/main/java/com/secondlife/secondlife/dto/response/InspectorStatsResponse.java`
- `src/main/java/com/secondlife/secondlife/dto/response/InspectorPayoutResponse.java`

### 5.2. DTO & Mapper Cập Nhật
- `src/main/java/com/secondlife/secondlife/dto/response/UserProfileResponse.java`: Bổ sung 4 trường KTV với constructor tương thích ngược.
- `src/main/java/com/secondlife/secondlife/mapper/UserMapper.java`: Xử lý tự động sinh badgeCode, gán stationHubId/Name và certificationTitle khi role là INSPECTOR.

### 5.3. Repository, Service & Controller Cập Nhật
- `src/main/java/com/secondlife/secondlife/repository/InspectionOrderRepository.java`: Thêm query `findPayoutOrdersByInspectorId(...)`.
- `src/main/java/com/secondlife/secondlife/service/InspectionService.java`: Khai báo 2 method `getInspectorStats` và `getPayoutHistory`.
- `src/main/java/com/secondlife/secondlife/service/impl/InspectionServiceImpl.java`: Hiện thực logic tính toán thù lao, KPI và phân trang payouts.
- `src/main/java/com/secondlife/secondlife/controller/InspectorController.java`: Mở 2 endpoint `/stats` và `/payouts`.

### 5.4. Unit Test Mới & Bổ Sung
- `src/test/java/com/secondlife/secondlife/service/InspectorStatsTest.java`: Kiểm tra 100% tính toán KPI, doanh thu tháng, phụ cấp, đơn chờ quyết toán, phân trang payouts và UserMapper inspector mapping.
- `src/test/java/com/secondlife/secondlife/controller/InspectorControllerTest.java`: Bổ sung kiểm thử MockMvc cho endpoint `/api/v1/inspector/stats` và `/api/v1/inspector/payouts`.
