# BÁO CÁO KIỂM TOÁN UI 3 APP & DANH MỤC API BACKEND CÒN THIẾU

> **Thời gian thực hiện**: 16/10/2026  
> **Phạm vi kiểm toán**:
> - Mobile Buyer App: `p:\FPT\Capstone\MB_SecondLife_Buyer` (SecondLife)
> - Mobile Seller App: `p:\FPT\Capstone\MB_SecondLife_Seller` (SecondLife Partner)
> - Mobile Inspector App: `p:\FPT\Capstone\MB_SecondLife_Inspector` (SecondLife Expert)
> - Backend Source Code: `P:\FPT\Capstone\BE_SecondLife\secondLife\src\main\java\com\secondlife\secondlife`

---

## 1. TỔNG QUAN KẾT QUẢ KIỂM TOÁN VÀ XỬ LÝ MOCK DATA

Toàn bộ UI của 3 ứng dụng di động đã được quét rà soát toàn diện:
- **100% dữ liệu mock, demo items, simulated quotes, fallback giả lập và backdoor tài khoản đã được loại bỏ hoàn toàn.**
- Các repository và provider đã được chuyển đổi để gọi trực tiếp các API thực tế của Backend.
- Trường hợp danh sách trống từ Backend, ứng dụng hiển thị danh sách rỗng (`[]`) hoặc trạng thái Empty State trực quan, không tự ý inject dữ liệu ảo.
- Trường hợp lỗi mạng hoặc mã phản hồi HTTP lỗi từ máy chủ, ứng dụng ném lỗi `AppException` chuẩn tắc để giao diện hiển thị thông báo lỗi hoặc nút Thử lại (Retry), tuyệt đối không fallback về mock data.

---

## 2. CHI TIẾT CÁC ĐIỂM ĐÃ LOẠI BỎ MOCK VÀ KẾT NỐI API THỰC TẾ

### 2.1. Buyer App (`MB_SecondLife_Buyer`)

| Tệp tin | Dữ liệu Mock cũ đã loại bỏ | API Backend kết nối thực tế |
| :--- | :--- | :--- |
| `marketplace_repository.dart` | Hàm `_getDemoMarketplacePosts()` chứa 4 sản phẩm ảo: Nồi cơm Nagakawa, Nồi chiên Philips XXL, Máy hút bụi Dyson V11, Lò vi sóng Sharp. | `GET /api/v1/posts` và `GET /api/v1/posts/{id}` (`PostController.java:39`) |
| `buyer_order_repository.dart` | Hàm `_getDemoBuyerOrders()` chứa 2 đơn hàng ảo `ORD-20261015-8821` và `ORD-20261014-4190`. | `GET /api/v1/orders/buyer` (`OrderController.java:49`) |
| `checkout_repository.dart` | Fallback báo giá vận chuyển giả lập (`quote-simulated-...`, cước 35.000đ, giá hàng 850.000đ, tổng tiền 885.000đ). | `POST /api/v1/shipping/quotes` (`ShippingController.java:37`) |
| `tamper_seal_lookup_sheet.dart` | Mã tem niêm phong mẫu điền sẵn `'SL-SEAL-20261014-9988'`. | `GET /api/v1/inspection/seal/{tamperSealId}` (`InspectionController.java:117`) |

### 2.2. Seller App (`MB_SecondLife_Seller`)

| Tệp tin | Dữ liệu Mock cũ đã loại bỏ | API Backend kết nối thực tế |
| :--- | :--- | :--- |
| `tamper_seal_lookup_sheet.dart` | Mã tem niêm phong mẫu điền sẵn `'SL-SEAL-20261014-9988'`. | `GET /api/v1/inspection/seal/{tamperSealId}` (`InspectionController.java:117`) |
| `OrderRepository` / `PostRepository` / `WalletRepository` | Đã kiểm tra: 100% tệp đã kết nối API thực tế, không có dữ liệu demo hay mock. | `GET /api/v1/orders/seller`, `POST /api/v1/posts/init`, `GET /api/v1/wallet/me`, ... |

### 2.3. Inspector App (`MB_SecondLife_Inspector`)

| Tệp tin | Dữ liệu Mock cũ đã loại bỏ | API Backend kết nối thực tế |
| :--- | :--- | :--- |
| `auth_provider.dart` | 1. `isAuthenticated = true` mặc định.<br>2. Profile KTV giả: `KTV. Trần Minh Long`, `insp-ktv-8821`, 148 đơn, rating 4.96, thu nhập 18.450.000đ.<br>3. Backdoor đăng nhập: Bỏ qua mật khẩu nếu email chứa 'ktv' hoặc 'inspector'.<br>4. Endpoint sai: `/api/v1/auth/login`. | 1. `isAuthenticated = false` mặc định.<br>2. Endpoint chuẩn: `POST /api/auth/login` (`AuthController.java:43`).<br>3. Tự động lấy hồ sơ người dùng thực tế từ `GET /api/v1/users/me` (`UserProfileController.java:29`).<br>4. Lưu token và thông tin KTV thực tế vào Secure Storage. |
| `inspection_provider.dart` | 1. Hàm `_buildDefaultMockOrders()` chứa 4 đơn hàng ảo `INS-8921` đến `INS-8924`.<br>2. Khởi tạo danh sách checklist mẫu hardcode 7 tiêu chí `_generateDefaultChecklist()`. | 1. `GET /api/v1/inspector/orders` (`InspectorController.java:46`).<br>2. `GET /api/v1/inspector/orders/{orderId}/checklist` (`InspectorController.java:65`). |
| `seal_scanner_screen.dart` | Mã tem điền sẵn `SL-SEAL-8924-A1` và nhãn nút `Demo Sandbox`. | Trả về ô nhập trống, nhãn chuẩn `Nhập mã tem thủ công`, kết nối trực tiếp bộ quét camera và API xác thực tem. |
| `inspector_profile_screen.dart` | Các số tiền hardcode: 14.200.000đ (thù lao), 4.250.000đ (phụ cấp xăng xe), mã thẻ `SL-TECH-8821`, tỉ lệ `98.6%`. | Hiển thị mã KTV thực tế từ `auth.inspectorId`, hiển thị trạng thái đối soát thực tế. |

---

## 3. DANH MỤC CÁC API CÒN THIẾU Ở BACKEND (MISSING BACKEND APIS)

Qua quá trình rà soát đối chiếu giữa UI của 3 Mobile App và mã nguồn Backend Spring Boot (`P:\FPT\Capstone\BE_SecondLife\secondLife\src\main\java\com\secondlife\secondlife`), các API sau đây **chưa tồn tại ở Backend** và cần được bổ sung để hoàn thiện 100% luồng nghiệp vụ trên di động:

### 3.1. API Thống kê Hiệu suất & Thu nhập Kỹ thuật viên (Inspector KPI & Earnings)
- **Tình trạng hiện tại ở Backend**:
  - `InspectorController.java` chỉ mới có các API tác vụ:
    - `GET /api/v1/inspector/orders`: Lấy danh sách đơn phân công.
    - `POST /api/v1/inspector/orders/{orderId}/schedule`: Hẹn giờ kiểm định.
    - `GET /api/v1/inspector/orders/{orderId}/checklist`: Lấy biểu mẫu kiểm định.
    - `POST /api/v1/inspector/orders/{orderId}/start`: Bắt đầu kiểm định.
    - `POST /api/v1/inspector/orders/{orderId}/result`: Nộp biên bản kết quả.
    - `POST /api/v1/inspector/orders/{orderId}/generate-seal`: Sinh mã tem niêm phong.
  - **Chưa có API** trả về tổng hợp thu nhập trong tháng, số đơn đã hoàn thành, điểm đánh giá trung bình, và chi tiết phụ cấp di chuyển của KTV đăng nhập.
- **API đề xuất Backend cần thêm**:
  ```http
  GET /api/v1/inspector/stats
  ```
- **Quyền hạn truy cập**: `@PreAuthorize("hasAuthority('INSPECTION_ORDER_READ_SELF')")`
- **Request Headers**: `Authorization: Bearer <accessToken>`
- **Query Parameters**:
  - `month` (int, optional - mặc định tháng hiện tại, ví dụ: `10`)
  - `year` (int, optional - mặc định năm hiện tại, ví dụ: `2026`)
- **Phản hồi mẫu mong đợi (Response DTO)**:
  ```json
  {
    "status": 200,
    "message": "Lấy thống kê hiệu suất KTV thành công",
    "data": {
      "inspectorId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "inspectorName": "Nguyễn Văn A",
      "badgeCode": "SL-TECH-2026-089",
      "skillLevel": "Kỹ thuật viên Thiết bị Gia dụng & Cơ điện Bậc 3",
      "completedJobs": 24,
      "passedJobs": 21,
      "failedJobs": 3,
      "averageRating": 4.9,
      "onTimeRate": 97.5,
      "monthlyEarnings": 4850000,
      "laborFeeEarned": 3600000,
      "travelAllowanceEarned": 1250000,
      "pendingSettlementAmount": 650000
    }
  }
  ```

---

### 3.2. API Lịch sử Đối soát Thù lao Kỹ thuật viên (Inspector Payout History)
- **Tình trạng hiện tại ở Backend**:
  - Backend đã có `WalletController` cho Seller (`/api/v1/wallet/me`), nhưng chưa có endpoint cho KTV theo dõi các khoản thù lao từ từng đơn kiểm định đã hoàn tất.
- **API đề xuất Backend cần thêm**:
  ```http
  GET /api/v1/inspector/payouts
  ```
- **Quyền hạn truy cập**: `@PreAuthorize("hasAuthority('INSPECTION_ORDER_READ_SELF')")`
- **Query Parameters**: `page=0&size=20&sort=createdAt,desc`
- **Phản hồi mẫu mong đợi (Response DTO)**:
  ```json
  {
    "status": 200,
    "data": {
      "content": [
        {
          "payoutId": "payout-001",
          "orderId": "692c8153-f725-455b-bf74-4b574fa7522d",
          "postTitle": "Tủ lạnh Samsung Inverter 236L",
          "orderType": "AT_HOME",
          "completedAt": "2026-10-15T14:30:00Z",
          "laborFee": 150000,
          "travelFee": 55000,
          "totalPayout": 205000,
          "status": "SETTLED"
        }
      ],
      "page": 0,
      "size": 20,
      "totalElements": 1,
      "totalPages": 1
    }
  }
  ```

---

### 3.3. Bổ sung thông tin nghiệp vụ KTV vào Hồ sơ Người dùng (`GET /api/v1/users/me`)
- **Tình trạng hiện tại ở Backend**:
  - `UserProfileController.java` trả về `UserSummaryResponse` gồm: `id`, `email`, `fullName`, `phone`, `avatarUrl`, `accountStatus`, `emailVerified`, `gender`.
- **Đề xuất nâng cấp Backend**:
  - Đối với tài khoản có role `INSPECTOR`, bổ sung các trường thông tin:
    - `badgeCode` (Mã thẻ KTV)
    - `stationHubId` (ID trạm trung tâm trực thuộc)
    - `stationHubName` (Tên trạm trung tâm kiểm định, ví dụ: *Trung Tâm SecondLife - Khu CNC Thủ Đức*)
    - `certificationTitle` (Bằng cấp/chứng chỉ hành nghề)

---

## 4. KẾT LUẬN & KIỂM TRA ĐẢM BẢO

1. Toàn bộ mã nguồn trên cả 3 ứng dụng di động:
   - `MB_SecondLife_Buyer`
   - `MB_SecondLife_Seller`
   - `MB_SecondLife_Inspector`
   đã sạch hoàn toàn các dữ liệu mock/demo.
2. Đã khớp nối các endpoint thực tế tương ứng trong mã nguồn Backend Spring Boot.
3. Các thiếu sót về API đã được tổng hợp chi tiết theo chuẩn REST DTO ở mục 3 để Backend lập trình viên triển khai tiếp tục.
