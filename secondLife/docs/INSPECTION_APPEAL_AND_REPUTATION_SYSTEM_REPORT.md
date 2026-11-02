# BÁO CÁO TỔNG HỢP TOÀN DIỆN: HỆ THỐNG KIỂM ĐỊNH, KHIẾU NẠI 24H VÀ TRỪ ĐIỂM UY TÍN (BE & FE)

**Dự án**: Sàn Giao Dịch Đồ Cũ SecondLife (SEP490 Capstone)  
**Thời gian hoàn thành**: 30/10/2026  
**Trạng thái kiểm thử**: 
- **Backend (BE)**: 58/58 Tests Passed (100% Green).
- **Frontend (FE)**: TypeScript `tsc --noEmit` 0 errors, Production Build `npm run build` thành công.

---

## 1. TỔNG QUAN YÊU CẦU NGHIỆP VỤ & QUY TRÌNH HỆ THỐNG

### 1.1. Luồng Thương Lượng Hình Thức & Chi Phí Kiểm Định
- **Hai hình thức kiểm định**:
  - **Tại nhà người bán (`AT_HOME`)**: Kỹ thuật viên (Inspector) đến tận nhà người bán mở máy kiểm tra. Phí kiểm định = Phí cơ bản theo danh mục + Phụ phí di chuyển 2 chiều tính theo 6 bậc thang cước km.
  - **Tại trung tâm (`AT_CENTER`)**: Sản phẩm được vận chuyển qua bưu tá đến Trung tâm Kiểm định Hub. Phí kiểm định = Phí cơ bản + Phí vận chuyển chiều đi + Phí ký quỹ vận chuyển chiều về dự phòng (sẽ được hoàn trả nếu người mua chốt mua thành công).
- **Ba phương án chi trả phí**:
  - `BUYER_PAYS`: Người mua chi trả 100%.
  - `SELLER_PAYS`: Người bán chi trả 100% (cần người bán chấp thuận).
  - `SPLIT_50_50`: Chia đôi chi phí 50% - 50%.
- **Thương lượng đề xuất lại (Counter-Offer)**:
  - Người bán có quyền đề xuất đổi hình thức (Tại nhà <-> Tại trung tâm) hoặc đổi phương án phí. Người mua có 24h để đồng ý hoặc hủy bỏ.

### 1.2. Thống Nhất Ngày Giờ Kiểm Định Tại Nhà
- Kỹ thuật viên (hoặc người bán) sử dụng giao diện lịch hẹn đề xuất ngày giờ đến nhà (`scheduledAt` tương lai) kèm ghi chú dặn dò.
- Người bán nhận thông báo và xác nhận khung giờ hẹn.

### 1.3. Cơ Chế Rẽ Nhánh Kết Quả Kiểm Định
- **Khi kết quả `PASSED` (Đạt chuẩn)**:
  - Người mua có **6 giờ** để xác nhận thanh toán tiền hàng cuối cùng (được trừ tiền cọc cam kết giữ chỗ).
  - Hệ thống tự động hoàn phí ký quỹ chiều về (nếu gói `AT_CENTER`) và điều phối vận chuyển sản phẩm đến tay người mua.
  - Nếu người mua quá hạn 6 giờ không thanh toán hoặc từ chối mua: Đơn bị hủy, người mua bị trừ chi phí kiểm định/vận chuyển từ tiền cọc.
- **Khi kết quả `FAILED` (Không đạt chuẩn)**:
  - Hệ thống tự động thiết lập **Thời hạn khiếu nại 24 giờ (`appeal_deadline = NOW() + 24H`)**.
  - Người bán có quyền gửi khiếu nại đối soát (`PENDING_REVIEW`) hoặc chủ động chấp nhận kết quả.
  - **Nếu sau 24 giờ người bán không khiếu nại**: Hệ thống tự động kích hoạt tiến trình nền (Scheduler) chốt đơn và áp dụng khấu trừ điểm uy tín người bán.

### 1.4. Công Thức Tính Điểm Uy Tín Bị Trừ Của Người Bán (`calculateChecklistPenalty`)
Hệ thống đối soát tỷ lệ vi phạm của sản phẩm thực tế so với cam kết bài đăng:
- **Trừ 40.00 điểm (Vi phạm nghiêm trọng)**: Sản phẩm bị KTV xếp loại `POOR`, điểm đánh giá `< 40`, hoặc tỷ lệ mục checklist thất bại `> 60%`.
- **Trừ 20.00 điểm (Vi phạm trung bình)**: Tỷ lệ mục checklist thất bại từ `30%` đến `60%`.
- **Trừ 10.00 điểm (Vi phạm nhẹ)**: Tỷ lệ mục checklist thất bại `<= 30%` (lỗi nhỏ do hao mòn tự nhiên).
- **Trừ 15.00 điểm (Mặc định)**: Áp dụng khi không có dữ liệu checklist chi tiết.

### 1.5. Phán Quyết Khiếu Nại Của Quản Lý Trung Tâm
- **`UPHELD` (Bác bỏ khiếu nại)**: Giữ nguyên kết quả `FAILED`, hệ thống áp dụng trừ điểm uy tín người bán.
- **`OVERTURNED` (Chấp thuận khiếu nại)**: Xác nhận kỹ thuật viên nhầm lẫn hoặc người bán cung cấp đủ hóa đơn chứng cứ chính hãng. Hủy phạt điểm uy tín và hoàn tiền/cọc theo chính sách bảo vệ người dùng.

---

## 2. CHI TIẾT CÁC THAY ĐỔI BACKEND (BE)

### 2.1. Cơ Sở Dữ Liệu & Migration
* **File migration mới**: `src/main/resources/db/migration/V67__inspection_appeal_and_reputation_penalty.sql`
  * Bổ sung các cột vào bảng `inspection_orders`:
    * `appeal_deadline` (TIMESTAMP WITH TIME ZONE): Hạn chót 24h khiếu nại.
    * `appeal_status` (VARCHAR(30)): Trạng thái (`NONE`, `PENDING_REVIEW`, `UPHELD`, `OVERTURNED`).
    * `appeal_reason` (TEXT): Lý do khiếu nại của người bán.
    * `appeal_evidence_json` (TEXT): Danh sách URL ảnh/video/bằng chứng.
    * `appeal_resolved_at` (TIMESTAMP WITH TIME ZONE): Thời điểm xử lý khiếu nại.
    * `appeal_resolved_by` (UUID REFERENCES users(id)): Quản lý/Admin xử lý.
    * `reputation_penalty_applied` (BOOLEAN DEFAULT FALSE): Đánh dấu đã trừ điểm uy tín hay chưa.
    * `reputation_penalty_points` (NUMERIC(5,2)): Số điểm uy tín đã bị khấu trừ.

### 2.2. Entity & Enums
* **`com.secondlife.secondlife.common.InspectionAppealStatus`**:
  * Định nghĩa 4 trạng thái: `NONE`, `PENDING_REVIEW`, `UPHELD`, `OVERTURNED`.
* **`com.secondlife.secondlife.entity.InspectionOrder`**:
  * Ánh xạ các trường DB mới cùng quan hệ `@ManyToOne` với `appealResolvedBy`.

### 2.3. Data Transfer Objects (DTOs) & Mappers
* **`com.secondlife.secondlife.dto.request.InspectionAppealRequest`**:
  * Chứa dữ liệu khiếu nại: `reason`, `evidenceUrls`, `contestedItemIds`, `note`.
* **`com.secondlife.secondlife.dto.request.ReviewInspectionAppealRequest`**:
  * Chứa phán quyết của trung tâm: `decision` (`UPHELD` / `OVERTURNED`), `resolutionNote`.
* **`com.secondlife.secondlife.dto.response.InspectionOrderResponse`**:
  * Mở rộng đầy đủ các trường thông tin khiếu nại và điểm uy tín đã trừ.
* **`com.secondlife.secondlife.mapper.InspectionOrderMapper`**:
  * Bổ sung mapping tên người duyệt khiếu nại `appealResolvedByName`.

### 2.4. Service Layer Logic
* **`SellerReputationService` & `SellerReputationServiceImpl`**:
  * Bổ sung method:
    ```java
    void recordInspectionFailurePenalty(UUID sellerId, UUID inspectionOrderId, BigDecimal penaltyPoints, String reason, boolean isSeriousBreach);
    ```
  * Tự động trừ điểm trong `SellerReputation`, ghi log lịch sử `ReputationHistory`, và gửi cảnh báo vi phạm.
* **`InspectionService` & `InspectionServiceImpl`**:
  * Khi KTV nộp kết quả kiểm định `FAILED`:
    ```java
    order.setAppealDeadline(Instant.now().plus(Duration.ofHours(24)));
    order.setAppealStatus(InspectionAppealStatus.NONE);
    order.setReputationPenaltyApplied(false);
    ```
  * `submitAppeal(UUID sellerId, UUID orderId, InspectionAppealRequest request)`:
    * Kiểm tra quyền người bán chính chủ.
    * Xác thực đơn đang ở trạng thái `FAILED`, chưa quá hạn 24h (`isBefore(appealDeadline)`), và chưa từng nộp khiếu nại.
    * Chuyển trạng thái sang `InspectionAppealStatus.PENDING_REVIEW`.
  * `acceptFailedResult(UUID sellerId, UUID orderId)`:
    * Cho phép người bán tự nguyện chấp nhận kết quả lỗi mà không chờ hết 24h.
    * Tính toán mức phạt qua `calculateChecklistPenalty(order, isSerious)` và gọi `sellerReputationService.recordInspectionFailurePenalty(...)`.
  * `reviewAppeal(UUID reviewerId, UUID orderId, ReviewInspectionAppealRequest request)`:
    * Quản lý phê duyệt `UPHELD` (trừ điểm uy tín người bán) hoặc `OVERTURNED` (miễn phạt uy tín, hoàn tiền ký quỹ).
  * `processExpiredAppeals()`:
    * Quét các đơn `FAILED` có `appeal_deadline <= NOW()`, `appeal_status = 'NONE'`, và `reputation_penalty_applied = false`.
    * Tự động áp dụng khấu trừ điểm uy tín theo mức độ matching checklist.
  * Sửa lỗi ép kiểu đồng bộ giữa `ShippingAddress` và `ShippingAddressRequest`.
  * Bổ sung overload `confirmFinalPurchase(UUID buyerId, UUID orderId)`.

### 2.5. Schedulers & Controllers
* **`InspectionEscrowExpiryScheduler`**:
  * Định kỳ mỗi 15 phút gọi `inspectionService.processExpiredAppeals()` để quét và xử lý các đơn 24h quá hạn.
* **`InspectionController`**:
  * `POST /api/v1/inspection/orders/{orderId}/appeal`: Người bán nộp khiếu nại 24h.
  * `POST /api/v1/inspection/orders/{orderId}/accept-failed`: Người bán chấp nhận kết quả kiểm định lỗi.
* **`InspectionCenterController`**:
  * `POST /api/v1/inspector-center/orders/{orderId}/review-appeal`: Quản lý trung tâm duyệt khiếu nại.

### 2.6. Kiểm Thử Backend
* Cập nhật `InspectionServiceImplWorkflowTest` với các test case:
  * `sellerSubmitAppeal_Within24Hours_Success`
  * `sellerSubmitAppeal_After24Hours_ThrowsBadRequest`
  * `sellerAcceptFailedResult_Voluntary_PenaltyApplied`
  * `centerReviewAppeal_Upheld_PenaltyApplied`
  * `centerReviewAppeal_Overturned_NoPenalty`
  * `schedulerProcessExpiredAppeals_AutoDeductsReputation`
* **Kết quả Maven**: Chạy lệnh `mvnw test "-Dtest=ModuleBoundariesTest,PackageArchitectureTest,*Inspection*Test"` -> **58/58 bài test Passed 100%**.

---

## 3. CHI TIẾT CÁC THAY ĐỔI FRONTEND (FE)

### 3.1. API Service & Types
* **`FE_SecondLife/SEP490_FE_SecondLife/src/services/inspectorService.ts`**:
  * Bổ sung types: `InspectionAppealStatus`, `InspectionAppealRequest`, `ReviewInspectionAppealRequest`.
  * Mở rộng `InspectionOrderDto` với 9 trường dữ liệu khiếu nại và điểm uy tín.
  * Cung cấp các methods API: `submitAppeal()`, `acceptFailedResult()`, `reviewAppeal()`.
* **`FE_SecondLife/SEP490_FE_SecondLife/src/types/index.ts`**:
  * Bổ sung các trường khiếu nại vào interface `EscrowOrder`.

### 3.2. Bộ Component Mới (`src/components/inspection/`)
1. **`InspectionCountdownTimer.tsx`**:
   * Đồng hồ đếm ngược trực quan hiển thị Giờ:Phút:Giây.
   * Chế độ cảnh báo khẩn cấp: nhấp nháy màu hổ phách/đỏ khi thời gian còn `< 4 giờ`.
   * Hiển thị trạng thái "ĐÃ HẾT HẠN" khi đồng hồ về 0.
2. **`InspectionPenaltyPreviewCard.tsx`**:
   * Tự động phân tích JSON checklist và điểm số để hiển thị mức trừ uy tín dự tính (-10, -20, -40 điểm).
   * Hiển thị điểm số uy tín hiện tại và điểm số ước tính sau khi trừ.
   * Cảnh báo nguy cơ nếu điểm rơi xuống dưới 50 điểm (bị giới hạn quyền đăng tin).
3. **`InspectionAppealModal.tsx`**:
   * Hiển thị danh sách các tiêu chí checklist mà KTV đánh không đạt kèm ghi chú kỹ thuật.
   * Người bán tick chọn những tiêu chí muốn khiếu nại (`contestedItemIds`).
   * Tải ảnh/chứng từ hóa đơn trực tiếp lên Cloudinary thông qua `mediaService.uploadImage` hoặc dán link tài liệu.
   * Form nhập lý do khiếu nại bắt buộc và nút gửi đơn với hiệu ứng loading.
4. **`CenterReviewAppealModal.tsx`**:
   * Giao diện đối soát 2 cột song song dành cho Quản lý Trung tâm:
     * Cột trái: Kết luận KTV (điểm số, grade, link video unboxing/bóc seal).
     * Cột phải: Đơn khiếu nại người bán (lý do, danh sách mục tranh chấp, link ảnh bằng chứng).
   * Hai nút chọn phán quyết: `UPHELD` (Bác bỏ khiếu nại) vs `OVERTURNED` (Chấp thuận khiếu nại).
   * Ô nhập lý do/căn cứ phán quyết chính thức bắt buộc.
5. **`InspectionHomeSchedulerModal.tsx`**:
   * Modal đặt lịch hẹn ngày giờ đến nhà kiểm định giữa KTV và Người bán.
   * Xác thực thời gian hẹn phải ở tương lai và gợi ý khung giờ hành chính.

### 3.3. Tích Hợp Giao Diện Người Dùng (Views)
* **`InspectionCenterDashboardView.tsx`**:
  * Mở rộng `CenterTab` thêm tab `'appeals'`.
  * Thêm mục **"KHIẾU NẠI KIỂM ĐỊNH"** vào thanh menu điều hướng với huy hiệu số đơn chờ duyệt.
  * Bổ sung màn hình quản lý khiếu nại:
    * 4 Card thống kê: Tổng khiếu nại, Chờ xét duyệt, Đã bác bỏ, Đã chấp thuận.
    * Thanh lọc trạng thái và ô tìm kiếm theo tên sản phẩm, mã đơn, tên người bán.
    * Bảng danh sách khiếu nại chi tiết kèm nút "Xét Duyệt Phán Quyết".
  * Nhúng `CenterReviewAppealModal` xử lý phê duyệt trực tiếp.
* **`EscrowOrdersView.tsx`**:
  * Khi đơn hàng chuyển sang `INSPECTION_FAILED`:
    * Tự động hiển thị Khối cảnh báo quy trình khiếu nại 24h.
    * Hiển thị đồng hồ đếm ngược `InspectionCountdownTimer`.
    * Hiển thị bảng tính trước điểm trừ `InspectionPenaltyPreviewCard`.
    * Nút **"Nộp Khiếu Nại Kết Quả (Hạn 24h)"** mở `InspectionAppealModal`.
    * Nút **"Chấp Nhận Kết Quả"** kích hoạt hộp thoại xác nhận an toàn `dialogService.confirm` và gọi API trừ điểm uy tín ngay.
  * Khi đã nộp khiếu nại: Hiển thị badge trạng thái `Đang Chờ Trung Tâm Xét Duyệt Khiếu Nại`.
* **`BuyerInspectionDecisionView.tsx`**:
  * Đảm bảo đồng hồ đếm ngược 6h chốt mua hoạt động chuẩn xác khi kết quả `PASSED`.
  * Tích hợp xem chứng chỉ kiểm định điện tử QR và video bóc seal.

### 3.4. Kiểm Thử Đóng Gói Frontend
- **Type Checking**: Chạy `npx tsc --noEmit` -> **0 lỗi TypeScript**.
- **Production Bundle**: Chạy `npm run build` -> **Vite và Esbuild hoàn tất thành công trong 25.62 giây**.

---

## 4. BẢNG TỔNG HỢP DANH MỤC FILE THAY ĐỔI

| STT | Đường Dẫn File | Phân Hệ | Trạng Thái | Vai Trò / Chức Năng |
| :---: | :--- | :---: | :---: | :--- |
| 1 | `BE_SecondLife/.../V67__inspection_appeal_and_reputation_penalty.sql` | BE | Tạo mới | Migration DB thêm cột 24h appeal và điểm phạt uy tín |
| 2 | `BE_SecondLife/.../InspectionAppealStatus.java` | BE | Tạo mới | Enum trạng thái khiếu nại (`NONE`, `PENDING_REVIEW`, ...) |
| 3 | `BE_SecondLife/.../InspectionAppealRequest.java` | BE | Tạo mới | Request DTO nộp khiếu nại của người bán |
| 4 | `BE_SecondLife/.../ReviewInspectionAppealRequest.java` | BE | Tạo mới | Request DTO duyệt khiếu nại của trung tâm |
| 5 | `BE_SecondLife/.../InspectionOrder.java` | BE | Chỉnh sửa | Entity bổ sung các trường appeal và mapping JPA |
| 6 | `BE_SecondLife/.../InspectionOrderResponse.java` | BE | Chỉnh sửa | Response DTO trả về thông tin appeal và điểm uy tín |
| 7 | `BE_SecondLife/.../InspectionOrderMapper.java` | BE | Chỉnh sửa | Mapping dữ liệu appeal và tên người duyệt |
| 8 | `BE_SecondLife/.../SellerReputationService.java` | BE | Chỉnh sửa | Khai báo method trừ điểm uy tín do kiểm định failed |
| 9 | `BE_SecondLife/.../SellerReputationServiceImpl.java` | BE | Chỉnh sửa | Logic trừ điểm, ghi log lịch sử và gửi cảnh báo |
| 10 | `BE_SecondLife/.../InspectionService.java` | BE | Chỉnh sửa | Khai báo các method nộp, duyệt và quét hết hạn appeal |
| 11 | `BE_SecondLife/.../InspectionServiceImpl.java` | BE | Chỉnh sửa | Triển khai logic nghiệp vụ 24h appeal, checklist penalty |
| 12 | `BE_SecondLife/.../InspectionController.java` | BE | Chỉnh sửa | Endpoints nộp khiếu nại và tự nguyện chấp nhận |
| 13 | `BE_SecondLife/.../InspectionCenterController.java` | BE | Chỉnh sửa | Endpoint xét duyệt khiếu nại cho trung tâm |
| 14 | `BE_SecondLife/.../InspectionEscrowExpiryScheduler.java` | BE | Chỉnh sửa | Cronjob tự động quét các đơn 24h quá hạn |
| 15 | `BE_SecondLife/.../InspectionServiceImplWorkflowTest.java` | BE | Chỉnh sửa | Bộ unit tests kiểm thử luồng khiếu nại và trừ uy tín |
| 16 | `FE_SecondLife/.../inspectorService.ts` | FE | Chỉnh sửa | Khai báo types và các methods API gọi backend |
| 17 | `FE_SecondLife/.../src/types/index.ts` | FE | Chỉnh sửa | Bổ sung các trường appeal vào interface `EscrowOrder` |
| 18 | `FE_SecondLife/.../InspectionCountdownTimer.tsx` | FE | Tạo mới | Component đồng hồ đếm ngược 24h/6h kèm cảnh báo gấp |
| 19 | `FE_SecondLife/.../InspectionPenaltyPreviewCard.tsx` | FE | Tạo mới | Component xem trước mức trừ điểm uy tín dự kiến |
| 20 | `FE_SecondLife/.../InspectionAppealModal.tsx` | FE | Tạo mới | Modal người bán nộp khiếu nại và upload bằng chứng |
| 21 | `FE_SecondLife/.../CenterReviewAppealModal.tsx` | FE | Tạo mới | Modal trung tâm đối soát và ra phán quyết khiếu nại |
| 22 | `FE_SecondLife/.../InspectionHomeSchedulerModal.tsx` | FE | Tạo mới | Modal thống nhất ngày giờ hẹn kiểm định tại nhà |
| 23 | `FE_SecondLife/.../InspectionCenterDashboardView.tsx` | FE | Chỉnh sửa | Tích hợp Tab "Khiếu Nại Kiểm Định" và modal duyệt |
| 24 | `FE_SecondLife/.../EscrowOrdersView.tsx` | FE | Chỉnh sửa | Tích hợp khối cảnh báo khiếu nại 24h và các nút xử lý |

---

## 5. HƯỚNG DẪN KIỂM THỬ ĐẦU-CUỐI (E2E TESTING GUIDE)

1. **Khởi tạo đơn kiểm định**: Người mua tạo đơn kiểm định Tại nhà hoặc Tại trung tâm.
2. **Kỹ thuật viên thực hiện**: KTV đăng nhập cổng Inspector Portal, chấm điểm checklist để kết quả là `FAILED` (ví dụ: điểm = 35 hoặc đánh rớt > 60% checklist).
3. **Người bán nhận cảnh báo 24h**:
   - Người bán vào mục Đơn Hàng -> Mở chi tiết đơn hàng kiểm định.
   - Kiểm tra hiển thị đồng hồ đếm ngược 24h và Card thông báo dự kiến bị trừ `-40 điểm uy tín`.
   - **Kịch bản A (Chủ động chấp nhận)**: Bấm "Chấp nhận kết quả", xác nhận dialog -> Kiểm tra điểm uy tín bị trừ ngay lập tức.
   - **Kịch bản B (Nộp khiếu nại)**: Bấm "Nộp Khiếu Nại Kết Quả", chọn các mục checklist sai, đính kèm ảnh bằng chứng, nhập lý do và gửi -> Đơn chuyển sang `PENDING_REVIEW`.
4. **Trung tâm thẩm định phán quyết**:
   - Quản lý Trung tâm vào `InspectionCenterDashboardView` -> Chọn tab **"KHIẾU NẠI KIỂM ĐỊNH"**.
   - Bấm "Xét Duyệt Phán Quyết" -> Xem đối soát chứng cứ.
   - Thử nghiệm duyệt `OVERTURNED`: Kiểm tra hệ thống không trừ điểm người bán.
   - Thử nghiệm duyệt `UPHELD`: Kiểm tra hệ thống trừ điểm uy tín người bán theo đúng công thức.
5. **Tiến trình tự động (Scheduler)**:
   - Đơn `FAILED` không được khiếu nại sau 24h -> `InspectionEscrowExpiryScheduler` tự động trừ điểm uy tín và cập nhật `reputation_penalty_applied = true`.

