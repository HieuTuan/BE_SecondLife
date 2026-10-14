# KẾ HOẠCH TRIỂN KHAI FRONTEND: MOCK APIS & THIẾT KẾ GIAO DIỆN KIỂM ĐỊNH SẢN PHẨM (PRODUCT INSPECTION SYSTEM)
**Dự án**: SecondLife Marketplace (Capstone Project - Đồ án tốt nghiệp FPT)  
**Tác giả**: DinhPhat1401 & Antigravity  
**Ngày lập**: 13/10/2026  
**Mục tiêu**: Xây dựng hệ thống Mock API toàn diện (giúp FE chạy độc lập hoặc tích hợp song song) và thiết kế chi tiết tất cả các màn hình UI/UX cho toàn bộ 5 luồng người dùng (Người mua, Người bán, Kỹ thuật viên, Quản lý Trung tâm kiểm định, Admin sàn).

---

## MỤC LỤC
1. [KIẾN TRÚC MOCK API & SERVICE LAYER](#1-kiến-trúc-mock-api--service-layer)
2. [DANH MỤC MOCK CONTRACTS & DỮ LIỆU GIẢ LẬP (MOCK FIXTURES)](#2-danh-mục-mock-contracts--dữ-liệu-giả-lập-mock-fixtures)
3. [KỊCH BẢN GIẢ LẬP ĐA TRẠNG THÁI (STATE PRESETS & TEST SCENARIOS)](#3-kịch-bản-giả-lập-đa-trạng-thái-state-presets--test-scenarios)
4. [THIẾT KẾ CÁC MÀN HÌNH UI & COMPONENT WIREFRAMES](#4-thiết-kế-các-màn-hình-ui--component-wireframes)
   - 4.1. [UI 1: Khối Kiểm định Sản phẩm trong Checkout & Chat Modals](#41-ui-1-khối-kiểm-định-sản-phẩm-trong-checkout--chat-modals)
   - 4.2. [UI 2: Thẻ Đàm phán Kiểm định trong Chat & Quản lý đơn Người bán](#42-ui-2-thẻ-đàm-phán-kiểm-định-trong-chat--quản-lý-đơn-người-bán)
   - 4.3. [UI 3: Thanh Đếm ngược & Nộp phí Ký quỹ 6 Giờ (Escrow Timer & Payment)](#43-ui-3-thanh-đếm-ngược--nộp-phí-ký-quỹ-6-giờ-escrow-timer--payment)
   - 4.4. [UI 4: Màn hình Theo dõi & Biên bản Kiểm định (Tracking & Result Modal)](#44-ui-4-màn-hình-theo-dõi--biên-bản-kiểm-định-tracking--result-modal)
   - 4.5. [UI 5: Trạm Kỹ thuật viên Thực thi (ProductInspectionStationView)](#45-ui-5-trạm-kỹ-thuật-viên-thực-thi-productinspectionstationview)
   - 4.6. [UI 6: Portal Quản lý Trung tâm Kiểm định (InspectorPortalView)](#46-ui-6-portal-quản-lý-trung-tâm-kiểm-định-inspectorportalview)
   - 4.7. [UI 7: Tab Quản trị Biểu phí & Bậc thang Admin Sàn (AdminDashboardView)](#47-ui-7-tab-quản-trị-biểu-phí--bậc-thang-admin-sàn-admindashboardview)
5. [LỘ TRÌNH TRIỂN KHAI FRONTEND TỪNG BƯỚC](#5-lộ-trình-triển-khai-frontend-từng-bước)

---

## 1. KIẾN TRÚC MOCK API & SERVICE LAYER

### 1.1. Cơ chế Chuyển đổi Real API vs Mock API (Feature Toggle)
Hệ thống Frontend hỗ trợ bật/tắt chế độ Mock linh hoạt thông qua biến môi trường hoặc cờ trong `localStorage`:
```typescript
// src/services/inspectorService.ts
const USE_MOCK = import.meta.env.VITE_USE_MOCK_INSPECTION === 'true' || localStorage.getItem('USE_MOCK_INSPECTION') === 'true';
```
- Khi `USE_MOCK = false`: Gọi thẳng Backend Spring Boot API (`/api/v1/inspection/...`).
- Khi `USE_MOCK = true`: Trả về dữ liệu từ `mockInspectionData.ts` có giả lập độ trễ mạng (Network Latency $300\text{ms} - 500\text{ms}$) và cập nhật trạng thái lưu trong `sessionStorage`/`localStorage` để người dùng có thể thao tác liền mạch xuyên suốt các màn hình demo.

### 1.2. Cấu trúc thư mục Frontend đề xuất
```
src/
├── data/
│   └── mockInspectionData.ts           <-- Toàn bộ Fixtures & Mock Generators cho 5 kịch bản
├── services/
│   ├── apiClient.ts
│   ├── inspectorService.ts             <-- Nâng cấp toàn diện API kết nối BE hoặc Mock
├── components/
│   ├── inspection/
│   │   ├── InspectionQuoteCard.tsx     <-- Hiển thị báo giá chi tiết 2 gói & cước 6 bậc thang
│   │   ├── TravelTierBreakdownModal.tsx<-- Modal diễn giải cách tính bậc thang (5km*2k + 5km*2.5k...)
│   │   ├── ZinConsentCheckbox.tsx      <-- Checkbox cảnh báo tháo máy mất zin bắt buộc
│   │   ├── EscrowCountdownClock.tsx    <-- Đồng hồ đếm ngược 6 giờ nộp cọc / thanh toán hàng
│   │   ├── InspectionStatusBadge.tsx   <-- Badge hiển thị trạng thái đơn
│   │   ├── InspectionTimeline.tsx      <-- Stepper 6 bước trạng thái
│   │   └── VideoProofViewer.tsx        <-- Trình phát video khui kiện & video test máy
│   └── modals/
│       ├── InspectionCounterModal.tsx  <-- Modal người bán đề xuất lại phương án chia tiền
│       ├── ScheduleAppointmentModal.tsx<-- Modal hẹn lịch tại nhà (Gói AT_HOME)
│       └── DisputeArbitrationModal.tsx <-- Modal khiếu nại tranh chấp 3 bên đối chiếu 3 video
└── pages/
    ├── InspectorPortalView.tsx         <-- Cập nhật 4 Tabs cho Quản lý Trung tâm
    ├── ProductInspectionStationView.tsx<-- Cập nhật Trạm làm việc chấm Checklist & Video KTV
    └── AdminDashboardView.tsx          <-- Thêm Tab Quản trị Biểu phí 6 bậc thang
```

---

## 2. DANH MỤC MOCK CONTRACTS & DỮ LIỆU GIẢ LẬP (MOCK FIXTURES)

### 2.1. TypeScript Interfaces chuẩn khớp 100% Backend DTOs

```typescript
// src/types/inspection.ts
export type InspectionOrderType = 'AT_HOME' | 'AT_CENTER';
export type InspectionFeePayer = 'BUYER_PAYS' | 'SELLER_PAYS' | 'SPLIT_50_50';
export type InspectionOrderStatus =
  | 'WAITING_SELLER_APPROVAL'
  | 'WAITING_BUYER_APPROVAL'
  | 'WAITING_FEE_PAYMENT'
  | 'PAYMENT_EXPIRED'
  | 'CONFIRMED_PENDING_DISPATCH'
  | 'DISPATCHED'
  | 'SCHEDULED'
  | 'SHIPPING_TO_CENTER'
  | 'INSPECTING'
  | 'PASSED'
  | 'FAILED'
  | 'DELIVERING_TO_BUYER'
  | 'COMPLETED'
  | 'DISPUTED'
  | 'CANCELLED';

export interface InspectionTravelTierDto {
  tierNumber: number;
  name: string;
  fromKm: number;
  toKm: number | null;
  pricePerKm: number;
  distanceChargedInTier?: number;
  feeInTier?: number;
  status: string;
}

export interface InspectionQuoteResponse {
  postId: string;
  postTitle: string;
  postPrice: number;
  baseInspectionFee: number;
  
  // Gói Tại Nhà (AT_HOME)
  travelDistanceKm: number;
  travelFeeOneWay: number;
  travelFeeRoundTrip: number;
  atHomeTotalFee: number;
  appliedTiers: InspectionTravelTierDto[];
  atHomeSplitOptions: Record<InspectionFeePayer, number>;
  
  // Gói Tại Trung Tâm (AT_CENTER)
  shippingToCenterFee: number;
  returnShippingReserveFee: number;
  atCenterTotalFee: number;
  atCenterSplitOptions: Record<InspectionFeePayer, number>;
}

export interface ChecklistItemResultDto {
  itemId: string;
  itemTitle: string;
  isPassed: boolean;
  inspectorNote: string;
}

export interface InspectionOrderResponse {
  id: string;
  postId: string;
  postTitle: string;
  postImageUrls: string[];
  postPrice: number;

  buyerId: string;
  buyerEmail: string;
  buyerName: string;

  sellerId: string;
  sellerEmail: string;
  sellerName: string;

  inspectorId?: string;
  inspectorName?: string;

  orderType: InspectionOrderType;
  feePayer: InspectionFeePayer;
  status: InspectionOrderStatus;
  note?: string;

  inspectionFee: number;
  shippingFee: number;
  travelDistanceKm: number;
  travelFee: number;
  returnShippingReserveFee: number;

  buyerFeeShare: number;
  sellerFeeShare: number;

  paymentDeadline?: string;
  buyerPaidAt?: string;
  sellerPaidAt?: string;

  appointmentScheduledAt?: string;

  checklistResultJson?: string;
  videoProofUrl?: string;
  unboxingVideoUrl?: string;

  buyerConsentZin: boolean;
  sellerConsentZin: boolean;

  finalPaymentDeadline?: string;
  finalPaymentPaidAt?: string;
  finalItemPrice?: number;
  finalShippingFee?: number;

  createdAt: string;
  updatedAt: string;
}

export interface InspectionChecklistItemResponse {
  id: string;
  itemTitle: string;
  criteriaDescription: string;
  mandatory: boolean;
  orderIndex: number;
}

export interface InspectionChecklistResponse {
  id: string;
  categoryId?: string;
  categoryName?: string;
  title: string;
  description?: string;
  active: boolean;
  items: InspectionChecklistItemResponse[];
  createdAt?: string;
  updatedAt?: string;
}

export interface InspectionFeeProposalResponse {
  id: string;
  categoryId?: string;
  categoryName?: string;
  baseInspectionFee: number;
  tiersJson: string;
  tiers: InspectionTravelTierDto[];
  proposedById: string;
  proposedByName: string;
  reviewedById?: string;
  reviewedByName?: string;
  status: 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';
  rejectReason?: string;
  createdAt: string;
  reviewedAt?: string;
}
```

---

## 3. KỊCH BẢN GIẢ LẬP ĐA TRẠNG THÁI (STATE PRESETS & TEST SCENARIOS)

File `src/data/mockInspectionData.ts` sẽ cung cấp sẵn 5 kịch bản (Scenario Presets) đại diện cho các ca kiểm thử:

### Kịch bản 1: Đơn Tại Nhà - Đang chờ 2 bên nộp phí (Escrow 6h)
- **ID**: `mock-order-home-escrow-01`
- **Gói**: `AT_HOME`, khoảng cách $35.0\text{ km}$.
- **Cước**: Phí cơ sở $100.000\text{ đ}$ + Cước 6 bậc di chuyển 2 chiều $235.000\text{ đ}$ = **$335.000\text{ đ}$**.
- **Chia phí**: `SPLIT_50_50` $\to$ Buyer chịu $167.500\text{ đ}$, Seller chịu $167.500\text{ đ}$.
- **Trạng thái**: `WAITING_FEE_PAYMENT`.
- **Dữ liệu**: Buyer đã nộp tiền (`buyerPaidAt` có giá trị), Seller chưa nộp (`sellerPaidAt = null`), thời gian còn lại: 4 giờ 30 phút.

### Kịch bản 2: Đơn Tại Nhà - Đã phân công KTV & Đã hẹn lịch (Scheduled)
- **ID**: `mock-order-home-scheduled-02`
- **Gói**: `AT_HOME`.
- **Trạng thái**: `SCHEDULED`.
- **KTV phụ trách**: Hoàng Minh Tuấn (`inspector-01`).
- **Thời gian hẹn**: Ngày mai lúc 09:30 AM tại địa chỉ Người bán (123 Nguyễn Thị Minh Khai, Q.1).

### Kịch bản 3: Đơn Tại Trung Tâm - Đã kiểm định ĐẠT CHUẨN (PASSED)
- **ID**: `mock-order-center-passed-03`
- **Gói**: `AT_CENTER`.
- **Trạng thái**: `PASSED`.
- **Biên bản**: Đầy đủ 6/6 tiêu chí Đạt, có Unboxing Video khui kiện tại Hub và Video test màn hình/pin.
- **Thanh toán cuối**: Đang mở đếm ngược 6 giờ chờ Buyer thanh toán tiền hàng ($15.000.000\text{ đ}$) + cước ship giao hàng cuối ($35.000\text{ đ}$).

### Kịch bản 4: Đơn Tại Trung Tâm - Kiểm định KHÔNG ĐẠT (FAILED)
- **ID**: `mock-order-center-failed-04`
- **Gói**: `AT_CENTER`.
- **Trạng thái**: `FAILED`.
- **Lý do**: *"Màn hình đã bị thay linh kiện lô, FaceID không nhận diện được, khác mô tả nguyên bản."*
- **Quyết toán**: Hàng tự động hoàn trả lại cho Người bán sử dụng khoản phí dự phòng $50.000\text{ đ}$ đã nộp trước đó. Mở lại bài đăng hoặc kết thúc đơn.

### Kịch bản 5: Đơn Khiếu nại Tranh chấp 3 bên (DISPUTED)
- **ID**: `mock-order-dispute-05`
- **Trạng thái**: `DISPUTED`.
- **Bối cảnh**: Buyer nhận hàng bị nứt mặt kính và khiếu nại trong 24h.
- **Bằng chứng**: Có đủ 3 nguồn video (Seller gửi hàng, Center khui kiện test máy, Buyer đập hộp) để Staff sàn đối chiếu phân xử.

---

## 4. THIẾT KẾ CÁC MÀN HÌNH UI & COMPONENT WIREFRAMES

### 4.1. UI 1: Khối Kiểm định Sản phẩm trong Checkout & Chat Modals
Tích hợp trực tiếp vào [CheckoutModal.tsx](file:///p:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/components/modals/CheckoutModal.tsx) và [ChatModal.tsx](file:///p:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/components/modals/ChatModal.tsx).

```
+-----------------------------------------------------------------------+
|  [v] YÊU CẦU KIỂM ĐỊNH CHẤT LƯỢNG TRƯỚC KHI MUA                        |
|  Đảm bảo sản phẩm đúng mô tả và hoạt động chuẩn qua bên thứ 3          |
+-----------------------------------------------------------------------+
|  CHỌN GÓI KIỂM ĐỊNH:                                                  |
|  ( ) Gói 1: Tại Trung tâm SecondLife Hub   [150.000 đ]                |
|      - Phí kiểm định: 100.000 đ | Phí ship 2 chiều dự phòng: 100.000 đ|
|                                                                       |
|  (*) Gói 2: Kỹ thuật viên đến tận nhà     [335.000 đ]  (Khuyên dùng)   |
|      - Phí kiểm định cơ sở: 100.000 đ                                  |
|      - Cước di chuyển KTV (35 km x 2 chiều): 235.000 đ [Xem chi tiết i]|
|        (Phù hợp cho đồ điện tử giá trị cao, nội thất, hàng cồng kềnh)  |
+-----------------------------------------------------------------------+
|  THỎA THUẬN PHÂN CHIA CHI PHÍ VỚI NGƯỜI BÁN:                          |
|  ( ) Tôi (Người mua) trả 100%                 [ 335.000 đ ]           |
|  ( ) Người bán trả 100% (cần người bán duyệt) [       0 đ ]           |
|  (*) Mỗi bên chia đôi 50/50                   [ 167.500 đ / bên ]     |
+-----------------------------------------------------------------------+
|  [v] BẮT BUỘC: Tôi hiểu và đồng ý rằng quá trình kiểm định kỹ thuật    |
|      sẽ cần tháo máy/ốc vít và có thể làm mất trạng thái zin ban đầu. |
+-----------------------------------------------------------------------+
|  [ HỦY ]                           [ GỬI YÊU CẦU MUA CÓ KIỂM ĐỊNH ]    |
+-----------------------------------------------------------------------+
```

#### Modal Phụ: Diễn giải bảng tính cước 6 bậc thang (`TravelTierBreakdownModal.tsx`)
Khi người dùng bấm vào `[Xem chi tiết i]`, hiển thị Modal minh bạch chi phí:
```
Khoảng cách thực tế: 35.0 km (Haversine x 1.25 hệ số đường bộ)
- Bậc 1 (0 -> 5 km):     5.0 km x 2.000 đ =  10.000 đ
- Bậc 2 (5 -> 10 km):    5.0 km x 2.500 đ =  12.500 đ
- Bậc 3 (10 -> 15 km):   5.0 km x 3.000 đ =  15.000 đ
- Bậc 4 (15 -> 20 km):   5.0 km x 3.500 đ =  17.500 đ
- Bậc 5 (20 -> 30 km):  10.0 km x 4.000 đ =  40.000 đ
- Bậc 6 (> 30 km):       5.0 km x 4.500 đ =  22.500 đ
------------------------------------------------------
Tổng 1 chiều:                                117.500 đ
Nhân 2 chiều di chuyển (KTV đi và về): x 2 = 235.000 đ
Phí kiểm định máy móc cơ sở:                + 100.000 đ
------------------------------------------------------
TỔNG CHI PHÍ GÓI TẠI NHÀ:                    335.000 đ
```

---

### 4.2. UI 2: Thẻ Đàm phán Kiểm định trong Chat & Quản lý đơn Người bán
Tích hợp trong tin nhắn hệ thống tại [ChatModal.tsx](file:///p:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/components/modals/ChatModal.tsx) và [SellerDashboardView.tsx](file:///p:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/pages/SellerDashboardView.tsx).

```
+-----------------------------------------------------------------------+
|  [ICON KIỂM ĐỊNH] YÊU CẦU KIỂM ĐỊNH TỪ NGƯỜI MUA                      |
|  Người mua "Nguyễn Văn A" muốn đặt mua bài đăng: iPhone 13 Pro Max    |
|  - Hình thức kiểm định: KỸ THUẬT VIÊN ĐẾN TẬN NHÀ (AT_HOME)           |
|  - Phương án chi phí: CHIA ĐÔI 50/50                                  |
|  - Phần phí bạn cần thanh toán: 167.500 đ                             |
+-----------------------------------------------------------------------+
|  Lưu ý: Bạn bắt buộc phải đồng ý điều khoản cho phép tháo máy để kiểm |
|  định bo mạch và linh kiện bên trong.                                 |
+-----------------------------------------------------------------------+
|  [ TỪ CHỐI ]         [ ĐỀ XUẤT LẠI CHIA PHÍ ]        [ ĐỒNG Ý BÁN ]   |
+-----------------------------------------------------------------------+
```

- **Khi bấm [ĐỒNG Ý BÁN]**: Mở Modal xác nhận bắt buộc checkbox: *"Tôi là người bán và đồng ý cho phép KTV tháo máy kiểm định"*. Bấm Xác nhận $\to$ Chuyển sang bước Ký quỹ 6h.
- **Khi bấm [ĐỀ XUẤT LẠI CHIA PHÍ]**: Mở popup chọn: Đề xuất Người mua trả 100% hoặc đổi sang Gói Trung tâm.
- **Khi bấm [TỪ CHỐI]**: Mở popup nhập lý do (VD: *"Không muốn tháo máy mất zin"*, *"Bận không tiếp KTV được"*). Bài đăng tự động mở lại `ACTIVE`.

---

### 4.3. UI 3: Thanh Đếm ngược & Nộp phí Ký quỹ 6 Giờ (Escrow Timer & Payment)
Hiển thị trên Chi tiết đơn hàng của cả Người mua và Người bán:

```
+-----------------------------------------------------------------------+
|  [DONG HO CAT] THỜI GIAN KÝ QUỸ PHÍ KIỂM ĐỊNH: 04:28:15               |
|  Vui lòng nộp phí kiểm định trong vòng 6 giờ để xác nhận đơn.         |
|  Nếu quá hạn, đơn sẽ tự hủy và hoàn 100% tiền đã nộp về ví của bạn.    |
+-----------------------------------------------------------------------+
|  Trạng thái nộp phí:                                                  |
|  - Người mua (Bạn): [ ĐÃ THANH TOÁN 167.500 đ (10:15:30) ]            |
|  - Người bán:       [ ĐANG CHỜ THANH TOÁN 167.500 đ...   ]            |
+-----------------------------------------------------------------------+
|  Số dư Ví SecondLife của bạn: 2.500.000 đ                             |
|  [ NỘP PHÍ KIỂM ĐỊNH (167.500 đ) ]            [ NẠP TIỀN THÊM VÀO VÍ ]|
+-----------------------------------------------------------------------+
```

---

### 4.4. UI 4: Màn hình Theo dõi & Biên bản Kiểm định (Tracking & Result Modal)
Hiển thị cho cả Người mua và Người bán theo dõi trạng thái đơn hàng:

```
[1. Tạo yêu cầu] -> [2. Ký quỹ 6h] -> [3. Phân công] -> [4. Kiểm định] -> [5. Trả tiền hàng] -> [6. Hoàn tất]
                                                             |
                                                       (ĐANG THỰC HIỆN)

+-----------------------------------------------------------------------+
|  KẾT QUẢ KIỂM ĐỊNH: [ PASSED - ĐẠT CHUẨN CHẤT LƯỢNG ]                 |
|  Kỹ thuật viên: Hoàng Minh Tuấn | Ngày kiểm định: 13/10/2026 14:30    |
+-----------------------------------------------------------------------+
|  VIDEO BẰNG CHỨNG KIỂM ĐỊNH (KTV thực hiện):                          |
|  [ > Xem Video Test Bo Mạch & Màn Hình (03:45) ]                     |
|  [ > Xem Video Khui Kiện Hàng Niêm Phong (01:20) ]                    |
+-----------------------------------------------------------------------+
|  BIÊN BẢN CHẤM CHECKLIST KỸ THUẬT:                                    |
|  [v] 1. Ngoại quan & Khung vỏ: ĐẠT (Xước dăm nhẹ góc dưới, không móp) |
|  [v] 2. Màn hình & Cảm ứng:   ĐẠT (Màn zin chính hãng, TrueTone tốt)  |
|  [v] 3. Tình trạng Pin:       ĐẠT (Dung lượng 89%, chu kỳ sạc 420 lần)|
|  [v] 4. Bo mạch & Ốc vít:     ĐẠT (Chưa qua sửa chữa, keo kháng nước) |
|  [v] 5. Camera & Loa thoại:   ĐẠT (Mic thu rõ, camera không đốm bụi)  |
+-----------------------------------------------------------------------+
|  Ghi chú KTV: "Máy dùng giữ gìn, hoạt động hoàn hảo mọi chức năng."   |
+-----------------------------------------------------------------------+
|  [DONG HO CAT] THỜI GIAN THANH TOÁN TIỀN HÀNG: 05:12:44               |
|  Người mua cần thanh toán tiền hàng và ship cuối trong 6h để nhận hàng.|
|  - Tiền hàng: 15.000.000 đ                                            |
|  - Phí vận chuyển giao hàng: 35.000 đ                                 |
|  [ THANH TOÁN TIỀN HÀNG & GIAO HÀNG ]         [ KHIẾU NẠI / TRANH CHẤP]|
+-----------------------------------------------------------------------+
```

---

### 4.5. UI 5: Trạm Kỹ thuật viên Thực thi (`ProductInspectionStationView.tsx`)
Trang chuyên dụng cho KTV (`Role = INSPECTOR`) tại xưởng hoặc trên máy tính bảng/mobile khi đi thực địa tại nhà:

```
+-----------------------------------------------------------------------+
| TRẠM LÀM VIỆC KỸ THUẬT VIÊN (INSPECTION STATION)                      |
| Đơn: #INS-8921 | iPhone 13 Pro Max 128GB | Gói: TẠI NHÀ (AT_HOME)     |
+-----------------------------------------------------------------------+
| 1. LỊCH HẸN VỚI NGƯỜI BÁN:                                            |
|    Thời gian hẹn: 14/10/2026 - 09:30 AM | SĐT Người bán: 0912.345.678 |
|    Địa chỉ: 123 Nguyễn Thị Minh Khai, P. Bến Thành, Quận 1            |
|    [ ĐẶT LẠI LỊCH HẸN ]                                               |
+-----------------------------------------------------------------------+
| 2. UPLOAD VIDEO BẰNG CHỨNG (BẮT BUỘC):                                |
|    [ + Quay / Tải Video Khui kiện & Kiểm tra Ngoại quan ]             |
|    [ + Quay / Tải Video Tháo máy & Kiểm tra Kỹ thuật    ]             |
|    (Hệ thống tự động upload lên Cloudinary và lưu URL bảo chứng)      |
+-----------------------------------------------------------------------+
| 3. BIỂU MẪU CHẤM CHECKLIST (Ngành hàng: Điện thoại & Tablet):         |
|    Tiêu chí 1: Ngoại quan vỏ & ốc vít đáy                             |
|    (o) ĐẠT   ( ) KHÔNG ĐẠT    Ghi chú: [Xước nhẹ góc phải__________]  |
|                                                                       |
|    Tiêu chí 2: Màn hình cảm ứng & Hiển thị                            |
|    (o) ĐẠT   ( ) KHÔNG ĐẠT    Ghi chú: [Màn zin, cảm ứng mượt______]  |
|                                                                       |
|    Tiêu chí 3: Tình trạng Pin & Sạc                                   |
|    (o) ĐẠT   ( ) KHÔNG ĐẠT    Ghi chú: [Pin 89% zin theo máy_______]  |
|                                                                       |
|    Tiêu chí 4: Bo mạch & Dấu hiệu ngấm nước/ẩm mốc                    |
|    (o) ĐẠT   ( ) KHÔNG ĐẠT    Ghi chú: [Giấy quỳ trắng, chưa sửa___]  |
+-----------------------------------------------------------------------+
| 4. KẾT LUẬN CUỐI CÙNG:                                                |
|    (*) ĐẠT CHUẨN (PASSED)         ( ) KHÔNG ĐẠT (FAILED)              |
|    Ghi chú tổng kết: [Máy zin toàn bộ, đủ điều kiện giao dịch sàn____]|
|                                                                       |
|    [ HỦY BỎ ]                          [ NỘP BIÊN BẢN KIỂM ĐỊNH ]     |
+-----------------------------------------------------------------------+
```

---

### 4.6. UI 6: Portal Quản lý Trung tâm Kiểm định (`InspectorPortalView.tsx`)
Trang quản lý dành cho Quản lý Trung tâm (`Role = INSPECTION_CENTER`) với 4 Tabs:

#### Tab 1: Điều phối Đơn kiểm định (Dispatch Center)
- Bộ lọc: Tất cả, Chờ phân công (`CONFIRMED_PENDING_DISPATCH`), Đang kiểm tra (`INSPECTING`), Đã xong.
- Cột bảng: Mã đơn, Sản phẩm, Gói (`AT_HOME`/`AT_CENTER`), Người bán, Người mua, KTV phụ trách, Trạng thái, Thao tác.
- Nút **[Phân công KTV]**: Mở Modal hiển thị danh sách các KTV đang rảnh và chọn KTV để gán đơn.

#### Tab 2: Checklist Studio (Quản lý Tiêu chí Kiểm định)
- Danh sách bộ Checklist theo Danh mục: Điện thoại, Laptop, Xe máy cũ, Đồ gia dụng...
- Form thêm tiêu chí: Tên tiêu chí, Mô tả đánh giá, Checkbox Bắt buộc (Mandatory), Thứ tự hiển thị.
- Hỗ trợ thêm/sửa/xóa trực tiếp.

#### Tab 3: Bảng giá Bậc thang & Gửi đề xuất (Tier Pricing Tab)
- Hiển thị bảng 6 bậc thang hiện tại:
  * 0–5 km: 2.000 đ/km
  * 5–10 km: 2.500 đ/km
  * 10–15 km: 3.000 đ/km
  * 15–20 km: 3.500 đ/km
  * 20–30 km: 4.000 đ/km
  * >30 km: 4.500 đ/km
- Cho phép Quản lý trung tâm nhập mức giá điều chỉnh đề xuất (VD: xăng tăng giá muốn tăng mỗi bậc 500 đ).
- Nút **[Gửi Đề Xuất Lên Admin Sàn Duyệt]** $\to$ Tạo proposal gửi sang Admin.

#### Tab 4: Quản lý Nhân sự Kỹ thuật viên (Staff Management)
- Bảng danh sách các KTV trực thuộc trung tâm (Role `INSPECTOR`).
- Form tạo tài khoản KTV mới: Họ tên, Email, Mật khẩu, Số điện thoại.

---

### 4.7. UI 7: Tab Quản trị Biểu phí & Bậc thang Admin Sàn (`AdminDashboardView.tsx`)
Tích hợp tab **"Kiểm định Sản phẩm"** trong trang Quản trị Admin:

```
+-----------------------------------------------------------------------+
| QUẢN TRỊ BIỂU PHÍ & KIỂM ĐỊNH SẢN PHẨM (ADMIN PLATFORM)               |
+-----------------------------------------------------------------------+
| [TAB 1: PHÊ DUYỆT ĐỀ XUẤT BIỂU PHÍ (2 Chờ duyệt)]   [TAB 2: CẤU HÌNH] |
+-----------------------------------------------------------------------+
| DANH SÁCH ĐỀ XUẤT TỪ TRUNG TÂM KIỂM ĐỊNH:                             |
| Mã: #PROP-102 | Danh mục: Đồ điện tử | Người gửi: TTKD Sài Gòn Hub    |
| - Phí kiểm định cơ sở: 100.000 đ -> Đề xuất: 120.000 đ                |
| - Bảng cước di chuyển: Tăng bậc 1 lên 2.500 đ, bậc 6 lên 5.000 đ       |
| - Lý do: "Chi phí di chuyển và kiểm tra đồ điện tử cồng kềnh tăng"    |
|                                                                       |
| [ XEM CHI TIẾT SO SÁNH TRƯỚC/SAU ]    [ TỪ CHỐI ]    [ PHÊ DUYỆT ]    |
+-----------------------------------------------------------------------+
| CẤU HÌNH TRỰC TIẾP 6 BẬC THANG CƯỚC DI CHUYỂN TOÀN SÀN:               |
| Bậc 1 (0-5 km):     [ 2000 ] đ/km                                     |
| Bậc 2 (5-10 km):    [ 2500 ] đ/km                                     |
| Bậc 3 (10-15 km):   [ 3000 ] đ/km                                     |
| Bậc 4 (15-20 km):   [ 3500 ] đ/km                                     |
| Bậc 5 (20-30 km):   [ 4000 ] đ/km                                     |
| Bậc 6 (>30 km):     [ 4500 ] đ/km                                     |
|                                                                       |
| [ LƯU CẤU HÌNH BẬC THANG ]                                            |
+-----------------------------------------------------------------------+
```

---

## 5. LỘ TRÌNH TRIỂN KHAI FRONTEND TỪNG BƯỚC

| Bước | Nội dung công việc | File mục tiêu |
| :---: | :--- | :--- |
| **B1** | Tạo file Mock Data Fixtures & Switch Toggle | `src/data/mockInspectionData.ts`, `src/types/inspection.ts` |
| **B2** | Cập nhật Service Layer hỗ trợ Real/Mock | `src/services/inspectorService.ts` |
| **B3** | Xây dựng các Reusable Components | `InspectionQuoteCard.tsx`, `EscrowCountdownClock.tsx`, `ZinConsentCheckbox.tsx` |
| **B4** | Cập nhật Modals Checkout & Chat | `CheckoutModal.tsx`, `ChatModal.tsx` |
| **B5** | Nâng cấp Trạm Kỹ thuật viên | `ProductInspectionStationView.tsx` |
| **B6** | Nâng cấp Portal Quản lý Trung tâm | `InspectorPortalView.tsx` |
| **B7** | Bổ sung Tab Quản trị Biểu phí Admin | `AdminDashboardView.tsx` |
| **B8** | Kiểm thử giao diện & Build Production | Chạy `npm run build` không lỗi type, demo 5 kịch bản mượt mà |

---
*Tài liệu này được lưu trữ tại `secondLife/docs/FRONTEND_PRODUCT_INSPECTION_MOCK_AND_UI_PLAN.md` làm kim chỉ nam thực hiện mã nguồn giao diện Frontend.*
