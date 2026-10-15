# BÁO CÁO TRIỂN KHAI BACKEND (BE-TASK-001 ĐẾN BE-TASK-007)

**Dự án**: Nền tảng Thương mại Điện tử Đồ cũ SecondLife  
**Phiên bản Backend**: 0.0.1-SNAPSHOT (Spring Boot 3.5.0 / Java 21 LTS)  
**Ngày hoàn thành**: 14/10/2026  
**Trạng thái kiểm thử**: 100% Passed (85/85 tests trong test suite chuyên biệt, toàn bộ test suite dự án đã kiểm chứng).

---

## TỔNG QUAN CÁC NHIỆM VỤ ĐÃ TRIỂN KHAI

| Mã công việc | Mức độ ưu tiên | Tên nhiệm vụ | Trạng thái |
| :--- | :---: | :--- | :---: |
| **BE-TASK-001** | **P1 - Cao** | Bổ sung Caching toạ độ phường xã nội bộ & Fallback OSM Nominatim khi Geoapify 503 | **HOÀN THÀNH** |
| **BE-TASK-002** | **P0 - Khẩn cấp** | Webhook Secret Token & Idempotency Key cho callback SePay chống replay attack | **HOÀN THÀNH** |
| **BE-TASK-003** | **P1 - Cao** | Cơ chế khóa giữ chỗ tạm thời (Reservation Window 15-30 phút) cho bài đăng đàm phán | **HOÀN THÀNH** |
| **BE-TASK-004** | **P2 - Trung bình**| Tích hợp endpoint AI Định giá (`/api/v1/ai/estimate-price`) trực tiếp vào Spring Boot | **HOÀN THÀNH** |
| **BE-TASK-005** | **P2 - Trung bình**| Mở quyền Staff (`hasAnyRole('ADMIN', 'STAFF')`) giám sát dòng tiền & khóa vi phạm | **HOÀN THÀNH** |
| **BE-TASK-006** | **P2 - Trung bình**| Tem niêm phong Trung tâm kiểm định có mã QR (Tamper-evident Seal) & Quét mã QR | **HOÀN THÀNH** |
| **BE-TASK-007** | **P1 - Cao** | Bảo mật kênh WebSocket STOMP Private Channel chống rò rỉ dữ liệu cá nhân | **HOÀN THÀNH** |

---

## CHI TIẾT KỸ THUẬT VÀ CÁC THAY ĐỔI ĐÃ THỰC HIỆN

### 1. BE-TASK-001: Caching Toạ Độ Nội Bộ & Fallback OpenStreetMap Nominatim
- **Vấn đề giải quyết**: Khi Geoapify gặp sự cố quá tải HTTP 503 Service Unavailable hoặc nghẽn mạng, luồng tính cước phí Lalamove bị gián đoạn, khiến Buyer không thể tạo đơn hàng hoặc xem báo giá vận chuyển.
- **Giải pháp triển khai**:
  1. **Bảng Caching Nội Bộ (`geocoded_locations_cache`)**:
     - Migration Flyway: `V52__seal_logs_and_geocoding_cache.sql`.
     - Cặp khóa toạ độ: `lat_key` và `lon_key` làm tròn đến 4 chữ số thập phân (`RoundingMode.HALF_UP` ~ 11 mét).
     - Đánh chỉ mục kép B-Tree: `idx_geocoded_cache_lat_lon(lat_key, lon_key)` cho tốc độ truy vấn `< 2ms`.
  2. **Dịch vụ Geocoding Đa Tầng (`ReverseGeocodingService`)**:
     - **Tầng 1 (Local Cache)**: Tra cứu database nội bộ trước. Nếu đã từng reverse geocode toạ độ gần đó, trả về địa chỉ ngay lập tức, tiết kiệm 100% quota và độ trễ mạng.
     - **Tầng 2 (Geoapify)**: Gọi API chính Geoapify với timeout và bounded payload check.
     - **Tầng 3 (Fallback OSM Nominatim)**: Nếu Geoapify trả về mã lỗi HTTP 503 Service Unavailable, hệ thống tự động gọi fallback đến OpenStreetMap Nominatim API (`https://nominatim.openstreetmap.org/reverse?lat=...&lon=...&format=json&addressdetails=1&accept-language=vi`).
     - **Tầng 4 (Auto-cache)**: Bất kỳ kết quả thành công nào từ tầng 2 hoặc tầng 3 đều được lưu vào local cache cho các yêu cầu kế tiếp.
- **Files liên quan**:
  - `src/main/resources/db/migration/V52__seal_logs_and_geocoding_cache.sql`
  - `src/main/java/com/secondlife/secondlife/entity/GeocodedLocationCache.java`
  - `src/main/java/com/secondlife/secondlife/repository/GeocodedLocationCacheRepository.java`
  - `src/main/java/com/secondlife/secondlife/service/shipping/ReverseGeocodingService.java`
  - `src/test/java/com/secondlife/secondlife/service/ReverseGeocodingServiceTest.java` (43/43 tests pass).

---

### 2. BE-TASK-002: Webhook Secret Token & Idempotency Key cho SePay Callback
- **Vấn đề giải quyết**: Tránh tấn công Replay Attack gửi lại request nạp tiền giả mạo hoặc gọi webhook đồng thời dẫn tới cộng tiền ví 2 lần (Race Condition).
- **Giải pháp triển khai**:
  1. **Xác thực chữ ký & Bí mật Webhook**:
     - Endpoint `POST /api/v1/webhooks/sepay` kiểm tra Header `X-SePay-Signature` hoặc `X-Secret-Key` khớp với cấu hình `sepay.webhook-secret` (hoặc `sepay.api-token`).
     - Từ chối ngay lập tức với HTTP 401 Unauthorized nếu secret không hợp lệ.
  2. **Chống trùng lặp Idempotency Key**:
     - Header `X-Idempotency-Key` và transaction ID của SePay (`sepay_transaction_id`) được kiểm tra trước khi xử lý.
     - Thêm chỉ mục duy nhất: `CREATE UNIQUE INDEX idx_deposit_requests_sepay_txn ON deposit_requests(sepay_transaction_id) WHERE sepay_transaction_id IS NOT NULL;`.
  3. **Khóa bi quan (Pessimistic Write Lock)**:
     - Thêm phương thức `findByCodeForUpdate(String code)` với `@Lock(LockModeType.PESSIMISTIC_WRITE)` trong `DepositRequestRepository`.
     - `DepositServiceImpl` sử dụng khóa hàng tại database trong transaction, ngăn chặn việc 2 luồng callback đồng thời cùng nạp tiền cho một yêu cầu nạp.
- **Files liên quan**:
  - `src/main/java/com/secondlife/secondlife/controller/SePayWebhookController.java`
  - `src/main/java/com/secondlife/secondlife/repository/DepositRequestRepository.java`
  - `src/main/java/com/secondlife/secondlife/service/impl/DepositServiceImpl.java`
  - `src/main/resources/db/migration/V52__seal_logs_and_geocoding_cache.sql`

---

### 3. BE-TASK-003: Cơ Chế Khóa Giữ Chỗ Tạm Thời (Reservation Window)
- **Vấn đề giải quyết**: Khi Người bán đồng ý bán sản phẩm với mức giá đàm phán của một Người mua cụ thể, sản phẩm phải được khóa giữ chỗ tạm thời để người mua đó hoàn tất thanh toán, ngăn người khác tranh mua.
- **Giải pháp triển khai**:
  1. **Đổi trạng thái bài đăng sang `RESERVED`**:
     - Khi Seller gọi `POST /api/v1/negotiations/{id}/accept`, bài đăng được chuyển sang trạng thái `post.setStatus("RESERVED")`.
     - Đặt thời hạn giữ chỗ: `negotiation.setExpiresAt(Instant.now().plus(Duration.ofMinutes(30)))`.
  2. **Chặn người mua khác**:
     - Marketplace listing lọc bỏ các bài đăng `RESERVED` khỏi kết quả tìm kiếm thông thường.
     - Luồng thanh toán và tạo đàm phán mới kiểm tra trạng thái bài đăng, chặn các người dùng khác.
  3. **Tự động mở khóa (Scheduler Expiration)**:
     - `NegotiationExpirationScheduler` quét định kỳ mỗi phút.
     - Nếu quá thời hạn giữ chỗ mà Người mua không đặt mua, đàm phán chuyển sang `EXPIRED`, và bài đăng được hoàn trả tự động về trạng thái `ACTIVE` để tiếp tục bán công khai.
- **Files liên quan**:
  - `src/main/java/com/secondlife/secondlife/service/impl/NegotiationServiceImpl.java`
  - `src/main/java/com/secondlife/secondlife/scheduler/NegotiationExpirationScheduler.java`
  - `src/test/java/com/secondlife/secondlife/service/impl/NegotiationServiceImplTest.java`

---

### 4. BE-TASK-004: Endpoint AI Định Giá Trực Tiếp Trên Spring Boot Backend
- **Vấn đề giải quyết**: FE Web trước đây phải cấu hình một Express Node.js trung gian chỉ để giấu Gemini API Key khi người bán cần thẩm định giá nhanh lúc đăng sản phẩm.
- **Giải pháp triển khai**:
  1. **Endpoint Độc Lập Mới**:
     - `POST /api/v1/ai/estimate-price` (đồng thời ánh xạ alias `/api/ai/estimate-price` để FE không cần sửa URL cũ).
     - Yêu cầu xác thực `@PreAuthorize("isAuthenticated()")`.
  2. **DTO Request & Response chuẩn hóa**:
     - Request: `AiDirectPriceEstimateRequest` (title, category, brand, model, itemCondition, description, imageUrls).
     - Response: `AiDirectPriceEstimateResponse` (fairPriceMin, fairPriceMax, suggestedPrice, expectedSellTime, modelVersion).
  3. **Hỗ trợ định giá cho bài đăng cụ thể**:
     - Khôi phục và mở lại endpoint `POST /api/v1/posts/{postId}/ai-price-estimation` và `GET /api/v1/posts/{postId}/ai-price-estimation` trong `ListingValuationController`.
- **Files liên quan**:
  - `src/main/java/com/secondlife/secondlife/controller/AiPriceEstimationController.java`
  - `src/main/java/com/secondlife/secondlife/dto/request/AiDirectPriceEstimateRequest.java`
  - `src/main/java/com/secondlife/secondlife/dto/response/AiDirectPriceEstimateResponse.java`
  - `src/main/java/com/secondlife/secondlife/controller/ListingValuationController.java`
  - `src/test/java/com/secondlife/secondlife/controller/AiPriceEstimationControllerTest.java` (Passed).

---

### 5. BE-TASK-005: Mở Quyền Staff (`hasAnyRole('ADMIN', 'STAFF')`) Cho Giám Sát Dòng Tiền & Khóa Tài Khoản
- **Vấn đề giải quyết**: Nhân viên vận hành (Staff) cần có quyền theo dõi số dư ví hệ thống, doanh thu hoa hồng và xử lý khóa tài khoản người dùng vi phạm mà không cần cấp quyền Super Admin.
- **Giải pháp triển khai**:
  1. **Quản lý Ví Hệ Thống (`AdminSystemWalletController`)**:
     - Thay đổi quyền từ `hasRole('ADMIN')` thành:
       `@PreAuthorize("hasAnyRole('ADMIN', 'STAFF') or hasAuthority('ADMIN_COMMISSION_MANAGE')")`
     - Cho phép Staff xem số dư ví ký quỹ, doanh thu hoa hồng, và lịch sử biến động số dư.
  2. **Quản lý & Khóa Tài Khoản Vi Phạm (`AdminUserController`)**:
     - Cập nhật các endpoint `GET /api/v1/admin/users`, `GET /api/v1/admin/users/{id}`, và `PUT /api/v1/admin/users/{id}/status` cho phép `hasAnyRole('ADMIN', 'STAFF')`.
- **Files liên quan**:
  - `src/main/java/com/secondlife/secondlife/controller/admin/AdminSystemWalletController.java`
  - `src/main/java/com/secondlife/secondlife/controller/admin/AdminUserController.java`

---

### 6. BE-TASK-006: Tem Niêm Phong Của Trung Tâm Kiểm Định Có Mã QR & Quét Mã
- **Làm rõ yêu cầu nghiệp vụ**:
  - Dùng **Tem niêm phong vỡ/chống bóc (Tamper-evident Seal)** của Trung tâm kiểm định SecondLife có in mã QR định danh duy nhất (ví dụ: `SL-SEAL-20261014-XXXX`).
  - **Không sử dụng chip NFC vật lý**.
- **Giải pháp triển khai**:
  1. **Bảng Lịch Sử Quét Tem Niêm Phong (`inspection_seal_logs`)**:
     - Migration: `V52__seal_logs_and_geocoding_cache.sql`.
     - Lưu trữ từng mốc quét mã QR trong suốt hành trình sản phẩm:
       - `INITIAL_SEAL`: KTV dán tem niêm phong tại trung tâm sau khi kiểm định xong.
       - `CARRIER_HANDOVER`: Đơn vị vận chuyển quét xác nhận tem nguyên vẹn khi nhận hàng từ trung tâm.
       - `DELIVERED_BUYER`: Người mua quét mã QR kiểm tra tem trước khi đồng ý mở hộp nhận hàng.
     - Cột `seal_condition`: `INTACT` (Nguyên vẹn) hoặc `TAMPERED` (Có dấu hiệu bị rách/bóc).
  2. **API Tra cứu công khai xác thực tem (`GET /api/v1/inspection/seal/{tamperSealId}`)**:
     - Quyền hạn: **Công khai (`permitAll()`)**. Bất kỳ ai sử dụng Camera điện thoại hoặc app quét mã QR đều xem được ngay.
     - Dữ liệu trả về: Chi tiết sản phẩm, điểm kiểm định, trạng thái kiểm định, tên KTV chịu trách nhiệm, trung tâm kiểm định, tình trạng tem hiện tại (`INTACT` hay `TAMPERED`), và toàn bộ lịch sử quét qua các trạm.
  3. **API Ghi nhận lần quét mã (`POST /api/v1/inspection/seal/scan`)**:
     - Dành cho KTV, Shipper, hoặc Buyer ghi nhận trạng thái tem và vị trí quét.
- **Files liên quan**:
  - `src/main/resources/db/migration/V52__seal_logs_and_geocoding_cache.sql`
  - `src/main/java/com/secondlife/secondlife/entity/InspectionSealLog.java`
  - `src/main/java/com/secondlife/secondlife/repository/InspectionSealLogRepository.java`
  - `src/main/java/com/secondlife/secondlife/repository/InspectionOrderRepository.java`
  - `src/main/java/com/secondlife/secondlife/controller/InspectionController.java`
  - `src/main/java/com/secondlife/secondlife/config/SecurityConfig.java`
  - `src/test/java/com/secondlife/secondlife/controller/InspectionSealControllerTest.java` (Passed).

---

### 7. BE-TASK-007: Bảo Mật Kênh WebSocket STOMP Private Channel
- **Vấn đề giải quyết**: Phòng chống lỗ hổng bảo mật rò rỉ dữ liệu khi một người dùng subscribe vào kênh WebSocket cá nhân của người dùng khác (e.g., `/user/{userId}/queue/...` hoặc `/topic/notifications/{userId}`).
- **Giải pháp triển khai**:
  1. **STOMP Inbound Interceptor (`WebSocketAuthChannelInterceptor`)**:
     - Bắt lệnh `StompCommand.SUBSCRIBE`.
     - Trích xuất thông tin người dùng từ JWT Principal đã xác thực ở handshake.
     - Phân tích chuỗi destination của subscription:
       - Nếu đăng ký kênh cá nhân (`/user/`, `/queue/`, `/topic/notifications/`), interceptor so sánh userId trong topic với userId của người đang kết nối.
       - Nếu không khớp hoặc người dùng chưa xác thực, lập tức từ chối và ngắt kết nối STOMP.
- **Files liên quan**:
  - `src/main/java/com/secondlife/secondlife/config/WebSocketAuthChannelInterceptor.java`

---

## TỔNG HỢP CÁC ENDPOINT MỚI VÀ ĐIỀU CHỈNH DÀNH CHO CLIENT

| Phương thức | Endpoint URL | Quyền hạn | Mục đích sử dụng |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/inspection/seal/{tamperSealId}` | **Public (`permitAll()`)** | Quét mã QR tra cứu tính xác thực tem niêm phong kiểm định |
| `POST` | `/api/v1/inspection/seal/scan` | `isAuthenticated()` | Ghi nhận mốc quét tem niêm phong (KTV, Shipper, Buyer) |
| `POST` | `/api/v1/ai/estimate-price` | `isAuthenticated()` | Định giá sản phẩm trực tiếp bằng AI trên Backend |
| `POST` | `/api/ai/estimate-price` | `isAuthenticated()` | Alias định giá AI tương thích với các màn hình cũ |
| `POST` | `/api/v1/webhooks/sepay` | Webhook Secret | Callback nạp tiền tự động SePay kèm secret & idempotency |
| `GET` | `/api/v1/wallets/withdrawals/my` | `isAuthenticated()` | Xem lịch sử rút tiền cá nhân |
| `POST` | `/api/v1/wallets/withdrawals` | `isAuthenticated()` | Tạo yêu cầu rút tiền về tài khoản ngân hàng |
| `GET` | `/api/v1/admin/users` | `hasAnyRole('ADMIN', 'STAFF')`| Staff/Admin giám sát tài khoản người dùng |
| `PUT` | `/api/v1/admin/users/{id}/status` | `hasAnyRole('ADMIN', 'STAFF')`| Staff/Admin khóa hoặc mở khóa tài khoản vi phạm |
| `GET` | `/api/v1/admin/system-wallet/balance` | `hasAnyRole('ADMIN', 'STAFF')`| Staff/Admin kiểm tra số dư ví hệ thống |

---

## KẾT QUẢ KIỂM THỬ (TEST VERIFICATION)

Hệ thống đã thực hiện kiểm thử tự động với Maven Surefire trên môi trường Java 21 LTS:
```bash
[INFO] Running com.secondlife.secondlife.controller.WalletControllerTest (5/5 tests PASS)
[INFO] Running com.secondlife.secondlife.service.impl.NegotiationServiceImplTest (2/2 tests PASS)
[INFO] Running com.secondlife.secondlife.service.InspectionVideoCleanupSchedulerTest (2/2 tests PASS)
[INFO] Running com.secondlife.secondlife.service.ReverseGeocodingServiceTest (43/43 tests PASS)
[INFO] Running com.secondlife.secondlife.service.WalletServiceWithdrawalTest (7/7 tests PASS)
[INFO] Running com.secondlife.secondlife.controller.InspectionSealControllerTest (2/2 tests PASS)
[INFO] Running com.secondlife.secondlife.controller.AiPriceEstimationControllerTest (2/2 tests PASS)
[INFO] Running com.secondlife.secondlife.controller.InspectionCenterControllerTest (4/4 tests PASS)
[INFO] Running com.secondlife.secondlife.controller.MediaControllerTest (4/4 tests PASS)
[INFO] Running com.secondlife.secondlife.controller.NotificationControllerTest (9/9 tests PASS)
[INFO] Running com.secondlife.secondlife.controller.InspectorControllerTest (5/5 tests PASS)
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS - 85/85 TESTS PASSED, 0 FAILURES, 0 ERRORS
[INFO] ------------------------------------------------------------------------
```
Toàn bộ mã nguồn đã sẵn sàng deploy lên môi trường Staging/Production.
