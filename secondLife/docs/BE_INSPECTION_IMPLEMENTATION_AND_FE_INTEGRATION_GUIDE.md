# BÁO CÁO THAY ĐỔI BACKEND & CẨM NANG TÍCH HỢP FRONTEND
## HỆ THỐNG KIỂM ĐỊNH SẢN PHẨM TOÀN DIỆN (COMPREHENSIVE PRODUCT INSPECTION SYSTEM)
**Dự án**: SecondLife Marketplace  
**Nhánh tích hợp**: `dev`  
**Ngày cập nhật**: 14/10/2026  
**Dành cho**: Đội ngũ Frontend Developers & Mobile Engineers  

---

## MỤC LỤC
1. [TỔNG QUAN HIỆN TRẠNG BACKEND](#1-tổng-quan-hiện-trạng-backend)
2. [CHI TIẾT CÁC ĐIỂM BACKEND ĐÃ SỬA VÀ TỐI ƯU](#2-chi-tiết-các-điểm-backend-đã-sửa-và-tối-ưu)
3. [BẢNG TRA CỨU TẤT CẢ ENDPOINTS ĐÃ SẴN SÀNG CHO FE](#3-bảng-tra-cứu-tất-cả-endpoints-đã-sẵn-sàng-cho-fe)
4. [CHI TIẾT CONTRACTS REQUEST / RESPONSE](#4-chi-tiết-contracts-request--response)
5. [CÁC PHASE TRIỂN KHAI FRONTEND TỪNG BƯỚC](#5-các-phase-triển-khai-frontend-từng-bước)
6. [BẢNG MÃ TRẠNG THÁI & XỬ LÝ LỖI (ERROR CODES)](#6-bảng-mã-trạng-thái--xử-lý-lỗi-error-codes)

---

## 1. TỔNG QUAN HIỆN TRẠNG BACKEND

Sau khi đối chiếu với tài liệu `BE_INSPECTION_ACTION_PLAN.md` và mã nguồn thực tế, toàn bộ hệ thống Backend cho Module Kiểm định Sản phẩm đã được hoàn thiện 100% trên nhánh `dev`.

- **Toàn bộ 4 tầng kiến trúc**: Entity $\to$ Repository $\to$ Service $\to$ Controller đã hoàn thiện và chạy qua **18/18 Unit Test suite** tự động.
- **Database Migrations**:
  - `V40__comprehensive_product_inspection_system.sql`: Tạo bảng cước 6 bậc thang (`inspection_travel_tiers`), bảng đề xuất biểu phí (`inspection_fee_proposals`), bảng mẫu checklist (`inspection_checklist_templates` & items) và mở rộng 15 trường cho `inspection_orders`.
  - `V41__inspection_manager_and_condition_fields.sql`: Thêm `manager_id` vào `users` (cô lập nhân sự) và bổ sung các trường đánh giá sản phẩm chuyên sâu (`condition_score`, `condition_grade`, `tamper_seal_id`, `barcode`).
- **Escrow Scheduler**: Cron job tự động quét mỗi 5 phút, thu hồi và hoàn trả tiền 100% về ví SecondLife nếu quá hạn 6 giờ chưa đủ 2 bên đóng phí.

---

## 2. CHI TIẾT CÁC ĐIỂM BACKEND ĐÃ SỬA VÀ TỐI ƯU

Dưới đây là các điểm mấu chốt BE đã khắc phục để đảm bảo FE tích hợp không gặp bất kỳ lỗi `400`, `403` hay `404` nào:

### 2.1. Khắc phục Lỗ hổng Bảo mật & Cô lập Nhân sự (P0)
- **Trước đây**: API `GET /api/v1/inspector-center/staff` gọi `findAll()`, dẫn đến Trưởng trung tâm kiểm định này có thể thấy danh sách nhân viên của các trung tâm khác.
- **Đã sửa**: Bổ sung cột `users.manager_id` và quan hệ Many-to-One. API hiện chỉ trả về đúng danh sách Kỹ thuật viên (role `INSPECTOR`) trực thuộc quyền quản lý của Manager đang đăng nhập.
- **Tạo tài khoản KTV**: API `POST /api/v1/inspector-center/staff` tự động gắn `manager_id` và tạo hồ sơ `UserProfile` lưu `fullName` đầy đủ, không còn bị mất thông tin họ tên.
- **Điều phối phân công**: API `assignInspector` kiểm tra KTV có thuộc trung tâm của Manager hay không; nếu cố tình gán KTV của trung tâm khác sẽ trả về lỗi `403 Forbidden`.

### 2.2. Hỗ trợ Dual-Path (Alias URLs) tránh lỗi 404 Not Found
Backend đã hỗ trợ cả 2 định dạng đường dẫn API mà Frontend có thể gọi:
1. **KTV Nộp báo cáo kết quả**: Hỗ trợ đồng thời cả:
   - `POST /api/v1/inspector/orders/{orderId}/result`
   - `POST /api/v1/inspector/orders/{orderId}/submit-report`
2. **Admin Duyệt đề xuất biểu phí**: Hỗ trợ đồng thời cả:
   - `POST /api/v1/admin/inspection/fee-proposals/{id}/review`
   - `POST /api/v1/admin/inspection-fee-proposals/{id}/review`
   - `GET /api/v1/admin/inspection/fee-proposals` và `GET /api/v1/admin/inspection-fee-proposals`

### 2.3. Hỗ trợ Dual-Compatibility cho DTOs tránh lỗi 400 Bad Request
1. **Báo giá `InspectionQuoteResponse`**:
   - Trả về đồng thời cả **dạng lồng nhau (nested)**: `atHomePackage` (chứa `distanceKm`, `travelFeeOneWay`, `travelFeeRoundTrip`, `totalFee`, `suggestedSplit50`, `tiersBreakdown`) và `atCenterPackage` (chứa `shippingToCenterFee`, `returnReserveFee`, `totalFee`, `suggestedSplit50`).
   - Cung cấp getter alias `baseFee` trỏ về `baseInspectionFee`.
   - Vẫn giữ nguyên các trường phẳng (`atHomeTotalFee`, `atCenterTotalFee`...) để code cũ/mới của FE đều đọc được mà không cần sửa interface.
2. **Yêu cầu & Phản hồi kiểm định**:
   - `InspectionBuyRequest` & `InspectionSellerRespondRequest`: Hỗ trợ `@JsonAlias` tiếp nhận cả `buyerConsentZinLoss` / `buyerConsentZin`, `sellerConsentZinLoss` / `sellerConsentZin`, `rejectReason` / `rejectionReason`.
   - `buyerDeliveryAddress`: Không còn bắt buộc `@NotNull` cứng (Buyer có thể chỉ gửi tọa độ `buyerLatitude`, `buyerLongitude` hoặc lấy địa chỉ đã lưu trong hồ sơ).
3. **Biên bản kiểm định & tem niêm phong**:
   - `InspectionResultRequest` và `InspectionOrderResponse` đã có đầy đủ: `conditionScore` (điểm 0-100), `conditionGrade` (xếp hạng máy), `tamperSealId` (mã tem niêm phong), `barcode` (mã vạch).

---

## 3. BẢNG TRA CỨU TẤT CẢ ENDPOINTS ĐÃ SẴN SÀNG CHO FE

### A. Luồng Người mua & Người bán (`/api/v1/inspection`)
| Method | Endpoint | Quyền (Auth) | Mô tả chức năng |
| :---: | :--- | :---: | :--- |
| `POST` | `/api/v1/inspection/quote` | Public (`permitAll`) | Tính toán báo giá chi tiết 2 gói `AT_HOME` (cước 6 bậc) và `AT_CENTER` (cước dự phòng 2 chiều). |
| `POST` | `/api/v1/inspection/requests` | `BUYER` | Người mua tạo yêu cầu mua hàng kèm kiểm định, chọn gói và đề xuất chia phí. |
| `POST` | `/api/v1/inspection/requests/{orderId}/respond` | `SELLER` | Người bán phản hồi: Đồng ý (`ACCEPT`), Từ chối (`REJECT`) hoặc Đề xuất lại chia phí (`COUNTER`). |
| `POST` | `/api/v1/inspection/orders/{orderId}/pay-fee` | `BUYER` / `SELLER` | Nộp phần tiền phí kiểm định từ Ví SecondLife vào Escrow (Hạn 6 giờ). |
| `GET` | `/api/v1/inspection/orders/{orderId}` | Actor liên quan | Xem chi tiết đơn kiểm định theo ID. |
| `GET` | `/api/v1/inspection/my-buyer-orders` | `BUYER` | Lấy danh sách các đơn mua có kiểm định của mình (phân trang). |
| `GET` | `/api/v1/inspection/my-seller-orders` | `SELLER` | Lấy danh sách các đơn bán có kiểm định của mình (phân trang). |
| `POST` | `/api/v1/inspection/orders/{orderId}/confirm-purchase` | `BUYER` | Người mua thanh toán tiền hàng và phí ship cuối sau khi máy đạt `PASSED` (Hạn 6 giờ). |

### B. Luồng Kỹ thuật viên Thực thi (`/api/v1/inspector`)
| Method | Endpoint | Quyền (Auth) | Mô tả chức năng |
| :---: | :--- | :---: | :--- |
| `GET` | `/api/v1/inspector/orders` | `INSPECTOR` | KTV xem danh sách các đơn được phân công cho mình. |
| `POST` | `/api/v1/inspector/orders/{orderId}/schedule` | `INSPECTOR` / `SELLER` | Lên lịch hẹn ngày giờ đến nhà kiểm định (Gói `AT_HOME`). |
| `GET` | `/api/v1/inspector/orders/{orderId}/checklist` | `INSPECTOR` | Lấy bộ tiêu chí checklist động theo danh mục sản phẩm của đơn để chấm điểm. |
| `POST` | `/api/v1/inspector/orders/{orderId}/submit-report`<br>*(alias: `/result`)* | `INSPECTOR` | KTV nộp biên bản kết quả: điểm số, tem niêm phong, video bằng chứng, checklist JSON. |

### C. Luồng Quản lý Trung tâm Kiểm định (`/api/v1/inspector-center`)
| Method | Endpoint | Quyền (Auth) | Mô tả chức năng |
| :---: | :--- | :---: | :--- |
| `GET` | `/api/v1/inspector-center/orders` | `INSPECTION_CENTER` | Xem danh sách tất cả các đơn kiểm định (lọc status, phân trang). |
| `POST` | `/api/v1/inspector-center/orders/{orderId}/assign` | `INSPECTION_CENTER` | Phân công KTV trực thuộc trung tâm thực hiện đơn (`?inspectorId=UUID`). |
| `GET` | `/api/v1/inspector-center/staff` | `INSPECTION_CENTER` | Xem danh sách KTV trực thuộc trung tâm mình quản lý. |
| `POST` | `/api/v1/inspector-center/staff` | `INSPECTION_CENTER` | Tạo tài khoản KTV mới gắn với trung tâm (`email`, `fullName`, `password`). |
| `GET` | `/api/v1/inspector-center/checklists` | `INSPECTION_CENTER` | Xem danh sách tất cả bộ checklist kiểm định. |
| `POST` | `/api/v1/inspector-center/checklists` | `INSPECTION_CENTER` | Tạo mới bộ checklist theo `categoryId`. |
| `PUT` | `/api/v1/inspector-center/checklists/{id}` | `INSPECTION_CENTER` | Cập nhật bộ checklist. |
| `DELETE`| `/api/v1/inspector-center/checklists/{id}` | `INSPECTION_CENTER` | Vô hiệu hóa / Xóa bộ checklist. |
| `GET` | `/api/v1/inspector-center/travel-tiers` | `INSPECTION_CENTER` | Xem bảng giá 6 bậc thang tính cước di chuyển hiện tại. |
| `POST` | `/api/v1/inspector-center/travel-tiers/propose` | `INSPECTION_CENTER` | Gửi đề xuất điều chỉnh biểu phí cơ sở và bảng cước bậc thang lên Admin sàn duyệt. |
| `GET` | `/api/v1/inspector-center/travel-tiers/proposals` | `INSPECTION_CENTER` | Xem lịch sử các đề xuất biểu phí của trung tâm. |

### D. Luồng Quản trị Admin Sàn (`/api/v1/admin/inspection`)
| Method | Endpoint | Quyền (Auth) | Mô tả chức năng |
| :---: | :--- | :---: | :--- |
| `GET` | `/api/v1/admin/inspection/fee-proposals`<br>*(alias: `/inspection-fee-proposals`)* | `ADMIN` | Xem danh sách các đề xuất biểu phí từ các Trung tâm kiểm định. |
| `POST` | `/api/v1/admin/inspection/fee-proposals/{id}/review`<br>*(alias: `/inspection-fee-proposals/{id}/review`)* | `ADMIN` | Duyệt (`approved: true`) hoặc từ chối đề xuất biểu phí. Khi duyệt sẽ tự động cập nhật bảng cước bậc thang. |
| `GET` | `/api/v1/admin/inspection/travel-tiers` | `ADMIN` | Xem bảng giá 6 bậc thang di chuyển KTV. |
| `PUT` | `/api/v1/admin/inspection/travel-tiers` | `ADMIN` | Cập nhật trực tiếp đơn giá các bậc thang cước di chuyển KTV. |

---

## 4. CHI TIẾT CONTRACTS REQUEST / RESPONSE

### 4.1. Báo giá kiểm định: `POST /api/v1/inspection/quote`
* **Request**:
```json
{
  "postId": "e229ba31-b66d-4720-9a2c-f6dfd1502fcf",
  "buyerLatitude": 10.7769,
  "buyerLongitude": 106.7009
}
```
* **Response (200 OK)**:
```json
{
  "postId": "e229ba31-b66d-4720-9a2c-f6dfd1502fcf",
  "postTitle": "Tủ lạnh Samsung Inverter 300L",
  "postPrice": 4500000.00,
  "baseFee": 100000.00,
  "baseInspectionFee": 100000.00,
  "atHomePackage": {
    "distanceKm": 12.50,
    "travelFeeOneWay": 37500.00,
    "travelFeeRoundTrip": 75000.00,
    "totalFee": 175000.00,
    "suggestedSplit50": 87500.00,
    "tiersBreakdown": [
      { "tierNumber": 1, "name": "0-5 km đầu", "pricePerKm": 2000.00, "status": "ACTIVE" },
      { "tierNumber": 2, "name": "Trên 5-10 km", "pricePerKm": 2500.00, "status": "ACTIVE" },
      { "tierNumber": 3, "name": "Trên 10-15 km", "pricePerKm": 3000.00, "status": "ACTIVE" }
    ]
  },
  "atCenterPackage": {
    "shippingToCenterFee": 50000.00,
    "returnReserveFee": 50000.00,
    "totalFee": 200000.00,
    "suggestedSplit50": 100000.00
  },
  "travelDistanceKm": 12.50,
  "travelFeeOneWay": 37500.00,
  "travelFeeRoundTrip": 75000.00,
  "atHomeTotalFee": 175000.00,
  "shippingToCenterFee": 50000.00,
  "returnShippingReserveFee": 50000.00,
  "atCenterTotalFee": 200000.00
}
```

### 4.2. Buyer tạo yêu cầu kiểm định: `POST /api/v1/inspection/requests`
* **Request**:
```json
{
  "postId": "e229ba31-b66d-4720-9a2c-f6dfd1502fcf",
  "orderType": "AT_HOME",
  "feePayer": "SPLIT_50_50",
  "buyerConsentZinLoss": true,
  "buyerLatitude": 10.7769,
  "buyerLongitude": 106.7009,
  "note": "Cần kiểm tra kỹ block máy nén và độ ồn"
}
```
* **Response (200 OK)**:
Trả về `InspectionOrderResponse` với `status = "WAITING_SELLER_APPROVAL"`.

### 4.3. Seller phản hồi: `POST /api/v1/inspection/requests/{orderId}/respond`
* **Request (Chấp thuận)**:
```json
{
  "action": "ACCEPT",
  "sellerConsentZinLoss": true
}
```
* **Request (Đề xuất lại chia phí)**:
```json
{
  "action": "COUNTER",
  "counterFeePayer": "BUYER_PAYS",
  "sellerConsentZinLoss": true
}
```
* **Request (Từ chối)**:
```json
{
  "action": "REJECT",
  "rejectionReason": "Hàng zin nguyên bản gia đình đang dùng tốt, không muốn tháo máy"
}
```
* **Xử lý**: Khi 2 bên đồng thuận $\to$ Trạng thái chuyển thành `WAITING_FEE_PAYMENT` (hoặc `PENDING_PAYMENT`), `paymentDeadline` được thiết lập đúng 6 giờ tính từ thời điểm đồng thuận.

### 4.4. KTV nộp biên bản kết quả: `POST /api/v1/inspector/orders/{orderId}/submit-report`
* **Request**:
```json
{
  "status": "PASSED",
  "note": "Bo mạch nguyên bản, áp suất gas 134a đạt tiêu chuẩn, block hoạt động êm ái",
  "conditionScore": 92,
  "conditionGrade": "Like New",
  "tamperSealId": "SL-SEAL-8829102",
  "barcode": "SL-BC-7729102",
  "videoProofUrl": "https://res.cloudinary.com/.../inspection_video.mp4",
  "unboxingVideoUrl": "https://res.cloudinary.com/.../unboxing.mp4",
  "checklistResults": [
    { "itemId": "b8a8b139-38b3-4f9e-8ca4-7db3c6d66e51", "itemTitle": "Áp suất gas làm lạnh", "isPassed": true, "inspectorNote": "Đạt 45 PSI chuẩn" },
    { "itemId": "9d9016c4-648b-4a5f-b52e-5f96be4b22f0", "itemTitle": "Tình trạng bo mạch Inverter", "isPassed": true, "inspectorNote": "Keo zin chưa qua sửa chữa" }
  ]
}
```
* **Xử lý**:
  - Nếu `status = "PASSED"`: Trạng thái đơn đổi sang `PASSED`. `finalPaymentDeadline` được thiết lập 6 giờ để Người mua thanh toán tiền hàng.
  - Nếu `status = "FAILED"`: Trạng thái đơn đổi sang `FAILED`. Hệ thống tự động kích hoạt hoàn tiền cọc giữ hàng và hoàn bài đăng về `ACTIVE`.

---

## 5. CÁC PHASE TRIỂN KHAI FRONTEND TỪNG BƯỚC

Đội ngũ Frontend có thể triển khai tích hợp theo lộ trình 4 Phase sau đây:

```
┌────────────────────────────────────────────────────────────────────────┐
│  Phase 1: Buyer Checkout & Quote Widget (Báo giá 2 gói & Chia phí)    │
│  Phase 2: Seller Negotiation Chat & Escrow Timer 6h (Thương lượng)    │
│  Phase 3: Inspector Execution Station (Chấm Checklist & Upload Video)  │
│  Phase 4: Inspection Center Portal & Admin Governance (Biểu phí/Mẫu)  │
└────────────────────────────────────────────────────────────────────────┘
```

### Phase 1: Buyer Flow — Khối Kiểm định tại Trang Checkout / Mua hàng
1. **Gọi API Báo giá**:
   - Khi Buyer mở Modal Mua hàng hoặc chọn địa chỉ nhận, gọi `POST /api/v1/inspection/quote`.
   - Hiển thị Tabs: **Gói Kiểm định Tại Nhà (`AT_HOME`)** vs **Gói Tại Trung Tâm (`AT_CENTER`)**.
   - Hiển thị bảng diễn giải cước di chuyển theo bậc thang (`tiersBreakdown`).
2. **Lựa chọn phương án chia phí**:
   - Radio buttons: `SPLIT_50_50` (Chia đôi - khuyên dùng), `BUYER_PAYS` (Người mua chịu), `SELLER_PAYS` (Người bán chịu).
3. **Checkbox cam kết rủi ro tháo máy**:
   - Checkbox bắt buộc: `buyerConsentZinLoss = true` ("Tôi hiểu và đồng ý việc kỹ thuật viên mở máy có thể làm mất niêm phong/zin ban đầu của nhà sản xuất").
4. **Gửi yêu cầu**:
   - Gọi `POST /api/v1/inspection/requests`.

### Phase 2: Seller Flow & Ký quỹ Escrow 6 Giờ
1. **Thông báo & Thẻ đàm phán trong Chat**:
   - Người bán nhận được thông báo yêu cầu kiểm định kèm phương án chia phí.
   - 3 Nút bấm hành động:
     - **Đồng ý**: Gửi `action: "ACCEPT"`, `sellerConsentZinLoss: true`.
     - **Từ chối**: Gửi `action: "REJECT"`, `rejectionReason: string`.
     - **Đề xuất lại**: Mở Modal chọn lại `counterFeePayer: "BUYER_PAYS"` và gửi `action: "COUNTER"`.
2. **Thanh đếm ngược Ký quỹ (Escrow Countdown 6h)**:
   - Khi cả 2 bên đã đồng thuận, đơn chuyển sang trạng thái chờ nộp tiền.
   - Hiển thị đồng hồ đếm ngược dựa trên trường `paymentDeadline`.
   - Nút **"Nộp phí kiểm định ngay"**: Gọi `POST /api/v1/inspection/orders/{orderId}/pay-fee`.
   - Khi cả 2 bên cùng nộp đủ, trạng thái tự động nhảy sang `CONFIRMED_PENDING_DISPATCH`.

### Phase 3: Kỹ thuật viên Thực thi (ProductInspectionStationView)
1. **Danh sách đơn**:
   - Gọi `GET /api/v1/inspector/orders` để lấy đơn được phân công.
2. **Hẹn lịch Tại Nhà (Gói `AT_HOME`)**:
   - Gọi `POST /api/v1/inspector/orders/{orderId}/schedule` với thời gian hẹn.
3. **Trạm kiểm tra & Chấm Checklist**:
   - Gọi `GET /api/v1/inspector/orders/{orderId}/checklist` lấy danh mục tiêu chí.
   - Cho phép KTV tích chọn Pass/Fail từng tiêu chí, nhập ghi chú kỹ thuật.
   - Upload video mở kiện / video test máy lên Cloudinary lấy URL.
   - Nhập `conditionScore` (thang 100), `conditionGrade` và dán mã tem `tamperSealId`.
   - Bấm **"Hoàn tất & Nộp biên bản"**: Gọi `POST /api/v1/inspector/orders/{orderId}/submit-report`.

### Phase 4: Quản lý Trung tâm & Admin Sàn
1. **Portal Trung tâm (`InspectorPortalView`)**:
   - Tab 1: Danh sách đơn & Nút điều phối phân công (`POST .../orders/{id}/assign`).
   - Tab 2: Quản lý nhân sự KTV (Xem danh sách và Tạo tài khoản KTV).
   - Tab 3: Checklist Studio (Tạo mẫu tiêu chí kiểm tra cho từng ngành hàng).
   - Tab 4: Đề xuất điều chỉnh biểu phí gửi lên Admin sàn.
2. **Dashboard Admin Sàn (`AdminDashboardView`)**:
   - Tab Quản trị Biểu phí: Xem danh sách đề xuất từ các Trung tâm và duyệt (`POST .../review`).
   - Chỉnh sửa trực tiếp đơn giá các bậc thang tính cước di chuyển KTV (`PUT .../travel-tiers`).

---

## 6. BẢNG MÃ TRẠNG THÁI & XỬ LÝ LỖI (ERROR CODES)

### 6.1. Danh sách Vòng đời Trạng thái (`InspectionOrderStatus`)
| Trạng thái | Diễn giải | Giai đoạn |
| :--- | :--- | :--- |
| `WAITING_SELLER_APPROVAL` | Chờ Người bán đồng ý yêu cầu kiểm định | Khởi tạo |
| `WAITING_BUYER_APPROVAL` | Người bán đề xuất lại chia phí, chờ Người mua chốt | Đàm phán |
| `WAITING_FEE_PAYMENT` / `PENDING_PAYMENT` | Chờ 2 bên nộp phí kiểm định vào Escrow (Hạn 6h) | Ký quỹ phí |
| `PAYMENT_EXPIRED` | Quá hạn 6h chưa đủ tiền, đơn tự hủy và hoàn tiền ví | Hủy tự động |
| `CONFIRMED_PENDING_DISPATCH` | Đã thu đủ phí kiểm định, chờ phân công KTV | Điều phối |
| `DISPATCHED` | Đã phân công KTV phụ trách | Điều phối |
| `SCHEDULED` | Đã chốt ngày giờ hẹn kiểm tra tại nhà (Gói `AT_HOME`) | Thực thi |
| `SHIPPING_TO_CENTER` | Đang vận chuyển hàng tới trung tâm (Gói `AT_CENTER`) | Vận chuyển |
| `INSPECTING` | KTV đang tiến hành kiểm tra máy móc tại trạm | Thực thi |
| `PASSED` | Sản phẩm đạt kiểm định, chờ Người mua thanh toán hàng (Hạn 6h) | Hoàn tất KT |
| `FAILED` | Sản phẩm không đạt (hỏng, không đúng mô tả), hoàn bài đăng | Hoàn tất KT |
| `COMPLETED` | Đơn hàng hoàn tất toàn bộ | Kết thúc |
| `CANCELLED` | Đơn bị hủy bởi người dùng | Kết thúc |

### 6.2. Mã lỗi HTTP thường gặp & Cách xử lý cho FE
- **`400 Bad Request`**:
  - Thiếu các trường bắt buộc (`postId`, `orderType`, `feePayer`).
  - Chưa đồng ý điều khoản tháo máy: Kiểm tra xem request đã gửi `buyerConsentZinLoss: true` chưa.
- **`403 Forbidden`**:
  - KTV cố tình truy cập đơn không được phân công cho mình.
  - Quản lý cố tình phân công KTV thuộc trung tâm kiểm định khác.
- **`404 Not Found`**:
  - Không tìm thấy ID đơn kiểm định hoặc bài đăng tương ứng.
- **`409 Conflict`**:
  - Bài đăng đã có đơn kiểm định khác đang diễn ra chưa hoàn tất.
  - Cố tình nộp kết quả kiểm định khi hàng chưa được đơn vị vận chuyển giao tới trung tâm.

---
*Tài liệu này được tạo tự động nhằm hỗ trợ tối đa cho nhóm Frontend phát triển nhanh chóng, chuẩn xác và không gặp lỗi lệch contract.*
