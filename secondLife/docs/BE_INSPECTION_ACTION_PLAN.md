# ĐỐI CHIẾU THỰC TẾ & KẾ HOẠCH HÀNH ĐỘNG BACKEND (BE ACTION PLAN)
## MODULE KIỂM ĐỊNH SẢN PHẨM — SECONDLIFE MARKETPLACE
**Tài liệu đối chiếu**: [secondLife/docs/PRODUCT_INSPECTION_END_TO_END_PLAN.md](file:///d:/Capstone/BE_SecondLife/secondLife/docs/PRODUCT_INSPECTION_END_TO_END_PLAN.md)  
**Ngày lập**: 14/10/2026  
**Người thực hiện**: Senior Full-stack Engineer kiêm Software Auditor  
**Trạng thái**: Hướng dẫn kỹ thuật và danh sách công việc Backend bắt buộc triển khai  

---

## I. TỔNG QUAN ĐỐI CHIẾU: THỰC TẾ BACKEND HIỆN TẠI VS BẢN KẾ HOẠCH

### 1. Nhận định sơ bộ
Bản kế hoạch [PRODUCT_INSPECTION_END_TO_END_PLAN.md](file:///d:/Capstone/BE_SecondLife/secondLife/docs/PRODUCT_INSPECTION_END_TO_END_PLAN.md) mô tả quy trình thương lượng kiểm định 2 gói chuyên sâu (`AT_HOME` cước bậc thang 6 cấp và `AT_CENTER` cước dự phòng 2 chiều) tích hợp ký quỹ Escrow 6 giờ, video đối chiếu 3 bên và checklist động theo ngành hàng.

Tuy nhiên, **Backend hiện tại chỉ có một khung prototype đơn giản**: Tự động sinh `InspectionOrder` khi Người bán đăng bài có giá trị cao (`> 5.000.000 đ`), sau đó Kỹ thuật viên nhập một chuỗi ghi chú thô để chuyển bài đăng sang `ACTIVE`.

> [!CAUTION]
> **Kết luận đối chiếu**: Hơn **85% chức năng nghiệp vụ của Bản kế hoạch chưa hề có trong mã nguồn Backend**. Nếu Frontend chỉ chuyển hướng trang và gọi API theo bản kế hoạch, toàn bộ các request sẽ nhận về lỗi **`404 Not Found`**, **`403 Forbidden`** hoặc **`500 Internal Server Error`**.

---

## II. MA TRẬN ĐỐI CHIẾU TỪNG ENDPOINT VÀ TÍNH NĂNG

| STT | Endpoint trong Kế hoạch | HTTP | Mô tả nghiệp vụ trong Kế hoạch | Hiện trạng trong Source Code BE | Đánh giá | Việc BE bắt buộc phải làm |
| :---: | :--- | :---: | :--- | :--- | :---: | :--- |
| **1** | `/api/v1/inspection/quote` | `POST` | Báo giá chi tiết 2 gói `AT_HOME` (6 bậc) và `AT_CENTER` (cước dự phòng) | Chỉ có dòng `permitAll()` trong `SecurityConfig.java:L68`. **Chưa có Controller & Service**. | ❌ **CHƯA CÓ** | Viết `InspectionQuoteController`, DTO Request/Response và Service tính cước Haversine 6 bậc. |
| **2** | `/api/v1/inspection/requests` | `POST` | Buyer tạo yêu cầu mua hàng kèm kiểm định, chọn gói và phương án chia phí | Hoàn toàn chưa có. | ❌ **CHƯA CÓ** | Viết endpoint tạo đơn kiểm định từ phía Buyer gắn với Escrow Order. |
| **3** | `/api/v1/inspection/requests/{id}/respond` | `POST` | Seller chấp thuận / từ chối / phản đối tỷ lệ chia phí | Hoàn toàn chưa có. | ❌ **CHƯA CÓ** | Viết endpoint xử lý thỏa thuận 2 chiều (Dual-consent) và chốt phương án chia phí. |
| **4** | `/api/v1/inspection/orders/{id}/pay-fee` | `POST` | Buyer / Seller nộp phí kiểm định từ Ví SecondLife vào Escrow (Hạn 6h) | Chưa có API nộp phí kiểm định riêng lẻ cho 2 bên. | ❌ **CHƯA CÓ** | Viết endpoint trừ ví SecondLife, ghi nhận `buyer_paid_at` / `seller_paid_at`. |
| **5** | *Scheduler quét 6 giờ* | `CRON` | Quét đơn quá 6 giờ chưa đủ 2 bên đóng tiền $\to$ Hủy đơn, tự hoàn tiền ví | Chưa có Scheduler cho kiểm định. | ❌ **CHƯA CÓ** | Viết `@Scheduled` mỗi 5 phút quét timeout và hoàn tiền tự động 100%. |
| **6** | `/api/v1/inspector-center/checklists` | `GET/POST` | Trưởng trung tâm quản lý biểu mẫu Checklist theo Category | Hoàn toàn chưa có bảng và API Checklist trong BE. | ❌ **CHƯA CÓ** | Tạo bảng `inspection_checklist_templates` & items; viết Controller CRUD checklist. |
| **7** | `/api/v1/inspector-center/travel-tiers/propose` | `POST` | Trưởng trung tâm đề xuất điều chỉnh bảng giá bậc thang di chuyển | Hoàn toàn chưa có. | ❌ **CHƯA CÓ** | Tạo bảng `inspection_fee_proposals`; viết API gửi đề xuất kèm snapshot JSON. |
| **8** | `/api/v1/admin/inspection-fee-proposals/{id}/review` | `POST` | Admin sàn duyệt hoặc từ chối đề xuất bảng giá của Trung tâm | Hoàn toàn chưa có. | ❌ **CHƯA CÓ** | Viết Controller Admin duyệt biểu phí. |
| **9** | `/api/v1/inspector-center/staff` | `GET` | Xem danh sách KTV trực thuộc Trung tâm | Đang gọi `userRepository.findAll()` $\implies$ **Lộ KTV của tất cả các trung tâm khác!** | ⚠️ **LỖ HỔNG (P0)** | Sửa truy vấn: Chỉ trả về KTV do chính Manager đó quản lý (`findByManagerId`). |
| **10** | `/api/v1/inspector-center/staff` | `POST` | Tạo tài khoản KTV kiểm định (`INSPECTOR`) | Đang bỏ qua `managerId`, **vứt bỏ `fullName` (không tạo UserProfile)**. | ⚠️ **LỖI NẶNG (P1)** | Sửa Service: Lưu `manager_id` và tạo `UserProfile` lưu `fullName`, `phone`. |
| **11** | `/api/v1/inspector/orders/{id}/schedule` | `POST` | KTV và Seller hẹn mốc thời gian kiểm tra tại nhà | Hoàn toàn chưa có. | ❌ **CHƯA CÓ** | Thêm cột `appointment_scheduled_at` và viết endpoint đặt lịch. |
| **12** | `/api/v1/inspector/orders/{id}/submit-report` | `POST` | KTV nộp kết quả kèm Checklist chi tiết + Video khui hộp & test | Hiện chỉ có `/result` nhận `{status, note}`. **Vứt bỏ toàn bộ ảnh, video và checklist!** | ⚠️ **THIẾU TRẦM TRỌNG** | Mở rộng DTO nhận link video Cloudinary, JSON checklist, điểm số, tem niêm phong. |

---

## III. CHI TIẾT CÁC THAY ĐỔI BACKEND BẮT BUỘC (ARCHITECTURE & CODE SPEC)

### 1. Database Schema Migration: `V40__comprehensive_product_inspection_system.sql`
* **Vị trí file**: [secondLife/src/main/resources/db/migration/V40__comprehensive_product_inspection_system.sql](file:///d:/Capstone/BE_SecondLife/secondLife/src/main/resources/db/migration/V40__comprehensive_product_inspection_system.sql)
* **Nội dung migration cần thực thi**:
  1. Tạo bảng `inspection_travel_tiers`: Lưu 6 bậc lũy tiến tính cước di chuyển tại nhà (Seed sẵn dữ liệu từ 2.000 đ/km đến 4.500 đ/km).
  2. Tạo bảng `inspection_fee_proposals`: Lưu lịch sử đề xuất và duyệt biểu phí bậc thang.
  3. Tạo bảng `inspection_checklist_templates` & `inspection_checklist_items`: Lưu bộ tiêu chuẩn kiểm tra theo `category_id`.
  4. Bổ sung liên kết trung tâm vào bảng `users`:
     ```sql
     ALTER TABLE users ADD COLUMN manager_id UUID NULL REFERENCES users(id);
     CREATE INDEX idx_users_manager_id ON users(manager_id);
     ```
  5. Mở rộng bảng `inspection_orders` thêm 15 trường nghiệp vụ:
     * `order_type VARCHAR(30)`: `AT_HOME` hoặc `AT_CENTER`.
     * `fee_payer VARCHAR(30)`: `BUYER_PAYS`, `SELLER_PAYS`, `SPLIT_50_50`.
     * `buyer_fee_share NUMERIC(18,2)`, `seller_fee_share NUMERIC(18,2)`.
     * `travel_distance_km NUMERIC(8,2)`, `travel_fee NUMERIC(18,2)`.
     * `return_shipping_reserve_fee NUMERIC(18,2)`.
     * `payment_deadline TIMESTAMPTZ`, `buyer_paid_at TIMESTAMPTZ`, `seller_paid_at TIMESTAMPTZ`.
     * `appointment_scheduled_at TIMESTAMPTZ`.
     * `checklist_result_json TEXT`, `video_proof_url TEXT`, `unboxing_video_url TEXT`.
     * `condition_score INT`, `condition_grade VARCHAR(50)`, `tamper_seal_id VARCHAR(100)`.
     * `barcode VARCHAR(100) UNIQUE`.
  6. Bổ sung các Permission Codes mới:
     * `INSPECTION_CHECKLIST_MANAGE`, `INSPECTION_FEE_MANAGE`, `INSPECTION_REQUEST`, `INSPECTION_RESPOND`.

---

### 2. Các Entities & Repositories mới / cần chỉnh sửa

#### A. Tạo mới các Entity trong package `com.secondlife.secondlife.entity`:
1. `InspectionTravelTier.java`: Mapping bảng `inspection_travel_tiers` (tierNumber, name, fromKm, toKm, pricePerKm, status).
2. `InspectionFeeProposal.java`: Mapping bảng `inspection_fee_proposals` (category, baseFee, tiersJson, proposedBy, reviewedBy, status).
3. `InspectionChecklistTemplate.java`: Mapping bảng `inspection_checklist_templates` (category, title, description, items).
4. `InspectionChecklistItem.java`: Mapping bảng `inspection_checklist_items` (template, itemTitle, criteriaDescription, isMandatory, orderIndex).

#### B. Chỉnh sửa Entity hiện có:
1. `User.java`: Bổ sung quan hệ tự tham chiếu (Self-reference):
   ```java
   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "manager_id")
   private User manager;
   ```
2. `InspectionOrder.java`: Khai báo bổ sung toàn bộ các trường nghiệp vụ mới đã tạo trong migration `V40`.

#### C. Tạo mới các Repository trong package `com.secondlife.secondlife.repository`:
* `InspectionTravelTierRepository.java`: `List<InspectionTravelTier> findByStatusOrderByTierNumberAsc(String status);`
* `InspectionFeeProposalRepository.java`: `List<InspectionFeeProposal> findByStatusOrderByCreatedAtDesc(String status);`
* `InspectionChecklistTemplateRepository.java`: `Optional<InspectionChecklistTemplate> findByCategoryIdAndIsActiveTrue(UUID categoryId);`
* `InspectionChecklistItemRepository.java`: `List<InspectionChecklistItem> findByTemplateIdOrderByOrderIndexAsc(UUID templateId);`
* Cập nhật `UserRepository.java`: Thêm `List<User> findByManagerId(UUID managerId);`

---

### 3. Core Logic & Services bắt buộc triển khai

#### A. Service tính cước di chuyển lũy tiến 6 bậc (`InspectionTravelFeeCalculator.java`)
* **Vị trí**: `com.secondlife.secondlife.service.impl.InspectionTravelFeeCalculator`
* **Thuật toán**:
  1. Nhận vào tọa độ Người bán $(lat_1, lon_1)$ và Trung tâm kiểm định $(lat_2, lon_2)$.
  2. Tính khoảng cách chim bay theo công thức Haversine:
     $$d = 2R \times \arcsin\left(\sqrt{\sin^2\left(\frac{\Delta lat}{2}\right) + \cos(lat_1)\cos(lat_2)\sin^2\left(\frac{\Delta lon}{2}\right)}\right)$$
  3. Nhân hệ số uốn lượn đường bộ: $D = d \times 1.25$ (km).
  4. Tính cước 1 chiều theo 6 bậc lũy tiến từ DB:
     * $0 \to 5$ km: $2.000$ đ/km.
     * $5 \to 10$ km: $2.500$ đ/km.
     * $10 \to 15$ km: $3.000$ đ/km.
     * $15 \to 20$ km: $3.500$ đ/km.
     * $20 \to 30$ km: $4.000$ đ/km.
     * $> 30$ km: $4.500$ đ/km.
  5. Cước di chuyển 2 chiều = Cước 1 chiều $\times 2$.

#### B. Service báo giá 2 gói (`InspectionPricingServiceImpl.java`)
* **Gói `AT_HOME`**: $\text{Phí cơ sở} + \text{Phí di chuyển 2 chiều}$.
* **Gói `AT_CENTER`**: $\text{Phí cơ sở} + \text{Cước ship đi} + \text{Cước ship hoàn dự phòng (Lalamove quote)}$.
* Trả về bảng phân tích chi phí minh bạch và gợi ý chia 50/50 cho từng bên.

#### C. Scheduler tự động quét timeout 6 giờ (`InspectionEscrowScheduler.java`)
* **Cấu hình**: `@Scheduled(cron = "0 */5 * * * *")` (Chạy mỗi 5 phút).
* **Nghiệp vụ**:
  1. Quét các `InspectionOrder` có `status = 'PENDING_PAYMENT'` và `payment_deadline < Instant.now()`.
  2. Nếu Buyer đã nộp tiền mà Seller chưa nộp (hoặc ngược lại) $\to$ Gọi `WalletService` hoàn trả $100\%$ số tiền đã ký quỹ về ví SecondLife của người đó.
  3. Cập nhật `order.setStatus("PAYMENT_EXPIRED")`.
  4. Gửi thông báo Notification qua WebSocket và Email cho cả 2 bên.

#### D. Vá lỗi Bảo mật & Phân quyền trong `InspectionServiceImpl.java`
1. **Sửa `getStaffList(UUID managerId)`**:
   * *Hiện tại*: `userRepository.findAll().stream().filter(...)` $\implies$ Lộ KTV toàn hệ thống.
   * *Sửa thành*: `userRepository.findByManagerId(managerId).stream().map(userMapper::toSummaryResponse)...`
2. **Sửa `createInspectorAccount(...)`**:
   * *Hiện tại*: Không tạo profile, bỏ qua họ tên và managerId.
   * *Sửa thành*: Tạo `User`, gán `newInspector.setManager(manager)`, tạo `UserProfile(newInspector, fullName, phone, null)`.
3. **Sửa `assignInspector(UUID orderId, UUID inspectorId)`**:
   * Kiểm tra KTV có đúng là nhân viên trực thuộc Manager đang gọi API hay không:
     `if (!inspector.getManager().getId().equals(currentManagerId)) throw new ForbiddenException("Kỹ thuật viên không thuộc trung tâm của bạn");`
   * Cập nhật `order.setStatus("ASSIGNED")` thay vì giữ nguyên `PENDING`.

---

### 4. Chi tiết các REST Controllers & Endpoints cần viết mới

#### Endpoint 1: Báo giá kiểm định 2 gói
* **URL**: `POST /api/v1/inspection/quote`
* **Quyền**: Public (`permitAll()`)
* **Request Body**:
  ```json
  {
    "postId": "550e8400-e29b-41d4-a716-446655440000",
    "buyerLatitude": 10.7769,
    "buyerLongitude": 106.7009
  }
  ```
* **Response Body (200 OK)**:
  ```json
  {
    "postId": "550e8400-e29b-41d4-a716-446655440000",
    "baseFee": 100000,
    "atHomePackage": {
      "distanceKm": 35.0,
      "travelFeeOneWay": 117500,
      "travelFeeRoundTrip": 235000,
      "totalFee": 335000,
      "suggestedSplit50": 167500,
      "tiersBreakdown": [
        { "tier": "0-5 km", "km": 5, "rate": 2000, "amount": 10000 },
        { "tier": "5-10 km", "km": 5, "rate": 2500, "amount": 12500 },
        { "tier": "10-15 km", "km": 5, "rate": 3000, "amount": 15000 },
        { "tier": "15-20 km", "km": 5, "rate": 3500, "amount": 17500 },
        { "tier": "20-30 km", "km": 10, "rate": 4000, "amount": 40000 },
        { "tier": ">30 km", "km": 5, "rate": 4500, "amount": 22500 }
      ]
    },
    "atCenterPackage": {
      "shippingToCenterFee": 45000,
      "returnReserveFee": 45000,
      "totalFee": 190000,
      "suggestedSplit50": 95000
    }
  }
  ```

#### Endpoint 2: Người mua tạo yêu cầu kiểm định
* **URL**: `POST /api/v1/inspection/requests`
* **Quyền**: `@PreAuthorize("hasRole('BUYER')")`
* **Request Body**:
  ```json
  {
    "postId": "550e8400-e29b-41d4-a716-446655440000",
    "orderType": "AT_HOME",
    "feePayer": "SPLIT_50_50",
    "buyerConsentZinLoss": true
  }
  ```
* **Xử lý**: Tạo `InspectionOrder` ở trạng thái `WAITING_SELLER_APPROVAL`. Gửi notification cho Người bán.

#### Endpoint 3: Người bán phản hồi yêu cầu
* **URL**: `POST /api/v1/inspection/requests/{orderId}/respond`
* **Quyền**: `@PreAuthorize("hasRole('SELLER')")`
* **Request Body**:
  ```json
  {
    "action": "ACCEPT", // ACCEPT, REJECT, COUNTER
    "counterFeePayer": "BUYER_PAYS", // Chỉ có nếu COUNTER
    "sellerConsentZinLoss": true
  }
  ```
* **Xử lý**: Khi 2 bên thống nhất $\to$ Đơn chuyển sang `PENDING_PAYMENT`, thiết lập `payment_deadline = Instant.now().plus(6, ChronoUnit.HOURS)`.

#### Endpoint 4: Nộp tiền ký quỹ phí kiểm định (Hạn 6h)
* **URL**: `POST /api/v1/inspection/orders/{orderId}/pay-fee`
* **Quyền**: `@PreAuthorize("hasAnyRole('BUYER', 'SELLER')")`
* **Xử lý**:
  * Kiểm tra đơn có đúng hạn 6 giờ không (`Instant.now().isBefore(order.getPaymentDeadline())`).
  * Trừ tiền ví SecondLife của người gọi số tiền tương ứng với phần trách nhiệm (`buyer_fee_share` hoặc `seller_fee_share`).
  * Ghi nhận `buyer_paid_at` hoặc `seller_paid_at`.
  * Nếu cả 2 bên đã nộp đủ $\to$ Chuyển trạng thái đơn sang `CONFIRMED_PENDING_DISPATCH`.

#### Endpoint 5: KTV đặt lịch hẹn Tại nhà
* **URL**: `POST /api/v1/inspector/orders/{orderId}/schedule`
* **Quyền**: `@PreAuthorize("hasRole('INSPECTOR')")`
* **Request Body**: `{ "scheduledAt": "2026-10-15T09:30:00Z" }`
* **Xử lý**: Cập nhật `order.setAppointmentScheduledAt(...)`, gửi thông báo cho Seller và Buyer.

#### Endpoint 6: KTV nộp biên bản kiểm định kèm Video bằng chứng
* **URL**: `POST /api/v1/inspector/orders/{orderId}/submit-report`
* **Quyền**: `@PreAuthorize("hasRole('INSPECTOR')")`
* **Request Body**:
  ```json
  {
    "status": "PASSED", // PASSED, FAILED
    "note": "Bo mạch nguyên bản, áp suất gas chuẩn",
    "conditionScore": 94,
    "conditionGrade": "Like New",
    "tamperSealId": "SL-SEAL-8829102",
    "videoProofUrl": "https://res.cloudinary.com/.../inspection_test.mp4",
    "unboxingVideoUrl": "https://res.cloudinary.com/.../unboxing.mp4",
    "checklistResults": [
      { "itemId": "uuid-1", "isPassed": true, "testedValue": "-19.2°C", "note": "Lạnh sâu chuẩn" },
      { "itemId": "uuid-2", "isPassed": true, "testedValue": "0.85A", "note": "Dòng tải Inverter chuẩn" }
    ]
  }
  ```
* **Xử lý**: Lưu đầy đủ vào `checklist_result_json`, `video_proof_url`, `unboxing_video_url`, `tamper_seal_id`. Kích hoạt AI Scan (Google Gemini).

---

## IV. BẢNG CHECKLIST CÔNG VIỆC TRIỂN KHAI CHO ĐỘI NGŨ BACKEND

- [x] **Task BE-DB-01**: Tạo file Flyway migration `V40__comprehensive_product_inspection_system.sql`.
- [x] **Task BE-DB-02**: Chạy thử nghiệm migration trên môi trường dev, xác nhận tạo đủ 4 bảng và mở rộng đủ cột cho `inspection_orders` và `users` (bổ sung `V41__inspection_manager_and_condition_fields.sql`).
- [x] **Task BE-ENT-01**: Tạo 4 Entity mới (`InspectionTravelTier`, `InspectionFeeProposal`, `InspectionChecklistTemplate`, `InspectionChecklistItem`).
- [x] **Task BE-ENT-02**: Bổ sung `manager` vào `User.java` và bổ sung 15+ trường mới vào `InspectionOrder.java` (kèm `conditionScore`, `conditionGrade`, `tamperSealId`, `barcode`).
- [x] **Task BE-REPO-01**: Tạo 4 Repository mới và bổ sung method `findByManagerId` vào `UserRepository`.
- [x] **Task BE-SRV-01**: Viết `InspectionTravelFeeCalculator` tính cước lũy tiến 6 bậc chuẩn Haversine.
- [x] **Task BE-SRV-02**: Viết `InspectionPricingService` tính toán chi tiết phí 2 gói `AT_HOME` và `AT_CENTER` (hỗ trợ cả nested DTOs và flat fields).
- [x] **Task BE-SRV-03**: Viết `InspectionEscrowScheduler` quét tự động hủy và hoàn tiền sau 6 giờ.
- [x] **Task BE-SEC-01**: Sửa lỗi rò rỉ KTV trong `InspectionServiceImpl.getStaffList` (cô lập theo `managerId`) và gán họ tên KTV qua `UserProfile` trong `createInspectorAccount`.
- [x] **Task BE-SEC-02**: Cập nhật trạng thái `DISPATCHED` khi phân công KTV và chặn phân công xuyên trung tâm (kiểm tra `managerId`).
- [x] **Task BE-API-01**: Viết `InspectionQuoteController` (`POST /api/v1/inspection/quote`) hỗ trợ 2 gói, cước bậc thang và tương thích cả 2 chuẩn response.
- [x] **Task BE-API-02**: Viết `InspectionRequestController` (Luồng Request, Respond, Pay-fee, hỗ trợ alias `*ZinLoss` và optional địa chỉ).
- [x] **Task BE-API-03**: Viết `InspectionChecklistController` (CRUD mẫu checklist theo Category).
- [x] **Task BE-API-04**: Nâng cấp API nộp biên bản kiểm định tiếp nhận video URL, checklist JSON, tem niêm phong và hỗ trợ cả 2 URL alias `/result` và `/submit-report`.

---
*Bản kế hoạch hành động này đã được đội ngũ Backend rà soát, vá lỗi logic, khắc phục lỗ hổng bảo mật và hoàn tất 100% các đầu việc.*
