# KẾ HOẠCH TRIỂN KHAI HỆ THỐNG THÔNG BÁO TOÀN DIỆN (NOTIFICATION SYSTEM MASTER PLAN)
## TỐI ƯU HÓA PHÂN KÊNH IN-APP / WEBSOCKET VS GMAIL (CHỐNG SPAM EMAIL)

> **Tài liệu đặc tả kiến trúc & Kế hoạch triển khai chi tiết hệ thống Thông báo (In-App & Real-Time WebSocket) cho nền tảng SecondLife.**  
> **Phiên bản**: 2.0 (Đã tinh chỉnh phân luồng chống spam Email & Bổ sung đầy đủ 3 cơ chế xóa)  
> **Trạng thái**: Kế hoạch chi tiết chờ duyệt trước khi thi công

---

## 1. Triết lý & Tiêu chí phân luồng Kênh thông báo (Notification Channel Strategy)

Để mang lại trải nghiệm chuyên nghiệp, mượt mà và **tránh biến hộp thư Gmail của khách hàng thành bãi rác (Spam)**, SecondLife phân định ranh giới rõ ràng giữa 2 kênh truyền thông:

### 1.1. Kênh In-App + WebSocket (Kênh Thời Gian Thực Mặc Định)
- **Tần suất**: Mọi biến động trạng thái trong hệ thống.
- **Đặc điểm**:
  - Lưu vào cơ sở dữ liệu (bảng `notifications`).
  - Đẩy ngay lập tức qua WebSocket STOMP (`/user/queue/notifications`) khi người dùng đang mở web/app.
  - Hiển thị badge quả chuông (unread count) và dropdown thông báo.
  - Cho phép người dùng click để chuyển hướng ngay tới màn hình xử lý (Deep Link).
  - Có các tính năng quản lý: Đọc, đánh dấu đã đọc tất cả, xóa từng cái, xóa đã đọc, xóa tất cả.

### 1.2. Kênh Gmail (Kênh Chứng Từ & Bảo Mật Khẩn Cấp)
- **Nguyên tắc "Anti-Spam"**: **CHỈ GỬI EMAIL KHI THỰC SỰ CẦN THIẾT**.
- **Bộ 4 tiêu chí vàng để một sự kiện ĐƯỢC PHÉP gửi Gmail**:
  1. **Bảo mật & Định danh tài khoản (Security & Auth)**:
     - OTP đăng ký / OTP quên mật khẩu.
     - Cảnh báo đổi mật khẩu / Tài khoản bị khóa (để user can thiệp nếu bị hack).
     - Cấp tài khoản quản trị viên / kỹ thuật viên kèm mật khẩu ban đầu.
  2. **Giá trị Pháp lý & Định danh người bán (Legal & KYC)**:
     - Kết quả xét duyệt hồ sơ Người bán (Chấp thuận kèm điều khoản hoặc Từ chối kèm lý do khắc phục).
  3. **Chứng từ Tài chính & Hóa đơn Giao dịch (Financial Receipt)**:
     - Đặt hàng & Khóa tiền ký quỹ thành công (Order Receipt lưu làm bằng chứng giao dịch).
     - Giải ngân tiền bán hàng về ví người bán (Payout Confirmation - hóa đơn doanh thu).
     - Hoàn tiền khi đơn hàng bị hủy (Refund Receipt).
  4. **Biên bản Kiểm định Chính thức (Official Inspection Certificate)**:
     - Kết quả kiểm định có tính chất quyết định giao dịch: PASSED (kèm link chứng thư / video bằng chứng để khách quyết định mua) hoặc FAILED / MẤT ZIN / RÁCH SEAL (biên bản sự cố và chính sách đền bù).

> [!IMPORTANT]
> **CÁC SỰ KIỆN TUYỆT ĐỐI KHÔNG GỬI GMAIL (CHỈ IN-APP)**:
> - Từng bước vận chuyển nhỏ lẻ: "Tài xế đang tới lấy", "Tài xế đang đi giao" *(Người dùng chỉ cần theo dõi trên app hoặc tra cứu mã vận đơn)*.
> - Trả giá / Thương lượng: Đề xuất giá, từ chối giá, hủy deal *(Tần suất chat/trả giá cao, gửi mail sẽ gây phiền phức cực lớn)*.
> - Đổi trạng thái tin đăng: Duyệt tin đăng thành công *(Chỉ in-app là đủ)*.
> - KTV đang di chuyển tới nhà kiểm định.
> - Thông báo nội bộ cho Staff/Admin về hàng đợi duyệt.

---

## 2. Bảng Phân loại Sự kiện Thông báo Toàn hệ thống (Matrix by Role)

Dưới đây là bảng phân bổ rõ ràng giữa **In-App + WebSocket** và **Gmail** cho từng vai trò người dùng:

### 2.1. Role: BUYER (Người mua)
| Nghiệp vụ | Sự kiện kích hoạt | In-App + WS | Gmail | Lý do phân luồng | Tiêu đề thông báo | Deep Link |
|---|---|:---:|:---:|---|---|---|
| **Đơn hàng** | Đặt hàng thành công & Giữ cọc | **CÓ** | **CÓ** | Hóa đơn xác nhận đơn hàng & giữ tiền ký quỹ (chứng từ giao dịch) | Đặt hàng thành công #{orderCode} | `/buyer/orders/{id}` |
| | Đơn hàng đã bàn giao vận chuyển | **CÓ** | KHÔNG | Cập nhật tiến độ thông thường, tránh spam | Đơn hàng #{orderCode} đang được giao | `/buyer/orders/{id}` |
| | Tài xế Lalamove đang tới giao | **CÓ** | KHÔNG | Trạng thái thời gian thực ngắn hạn | Đơn hàng #{orderCode} sắp tới điểm giao | `/buyer/orders/{id}` |
| | Giao hàng thành công | **CÓ** | KHÔNG | Nhắc nhở kiểm tra và xác nhận nhận hàng | Đơn hàng #{orderCode} đã giao thành công | `/buyer/orders/{id}` |
| | Hủy đơn & Hoàn tiền vào ví | **CÓ** | **CÓ** | Chứng từ hoàn tiền giao dịch tài chính | Đơn hàng #{orderCode} đã hủy - Tiền đã hoàn về ví | `/wallet` |
| **Kiểm định** | Gửi yêu cầu kiểm định thành công | **CÓ** | KHÔNG | Trạng thái chờ phản hồi | Yêu cầu kiểm định #{orderCode} đã gửi | `/buyer/inspections/{id}` |
| | Người bán chấp thuận kiểm định | **CÓ** | KHÔNG | Tiến độ kiểm định | Người bán đồng ý kiểm định cho '{itemTitle}' | `/buyer/inspections/{id}` |
| | Người bán từ chối kiểm định | **CÓ** | KHÔNG | Kèm lý do và cọc tự động hoàn về ví | Người bán từ chối kiểm định '{itemTitle}' | `/buyer/inspections/{id}` |
| | Chốt lịch hẹn KTV tới nhà | **CÓ** | KHÔNG | Nhắc lịch hẹn trên app | Lịch hẹn kiểm định tại nhà: {appointmentTime} | `/buyer/inspections/{id}` |
| | KTV đang di chuyển tới | **CÓ** | KHÔNG | Trạng thái ngắn hạn | KTV đang trên đường đến điểm hẹn | `/buyer/inspections/{id}` |
| | Kết quả kiểm định: PASSED | **CÓ** | **CÓ** | Biên bản kiểm định đạt chuẩn, căn cứ để mua hàng | [Biên bản] Kết quả kiểm định ĐẠT - '{itemTitle}' | `/buyer/inspections/{id}` |
| | Kết quả: FAILED / RÁCH SEAL | **CÓ** | **CÓ** | Biên bản sự cố chính thức, căn cứ xử lý bồi thường cọc | [Biên bản] Kết quả kiểm định KHÔNG ĐẠT - '{itemTitle}' | `/buyer/inspections/{id}` |
| **Trả giá** | Người bán đồng ý mức giá | **CÓ** | KHÔNG | Tần suất thương lượng cao, chỉ push in-app để chốt đơn | Người bán chấp nhận mức giá {price} VND! | `/posts/{postId}` |
| | Người bán từ chối mức giá | **CÓ** | KHÔNG | Không spam hòm thư với các deal bị từ chối | Đề xuất giá cho '{itemTitle}' đã bị từ chối | `/posts/{postId}` |
| **Ví tiền** | Nạp tiền ví qua SePay thành công | **CÓ** | **CÓ** | Biên nhận nạp tiền tài chính | Nạp tiền thành công: +{amount} VND | `/wallet` |
| | Hoàn tiền giao dịch | **CÓ** | **CÓ** | Biên nhận hoàn tiền ví | Hoàn tiền ví: +{amount} VND | `/wallet` |

---

### 2.2. Role: SELLER (Người bán)
| Nghiệp vụ | Sự kiện kích hoạt | In-App + WS | Gmail | Lý do phân luồng | Tiêu đề thông báo | Deep Link |
|---|---|:---:|:---:|---|---|---|
| **Đơn hàng** | Có người mua đặt hàng mới | **CÓ** | **CÓ** | Hóa đơn bán hàng cần chuẩn bị ngay, tránh trễ đơn | Đơn hàng mới #{orderCode} cần đóng gói | `/seller/orders/{id}` |
| | Tài xế Lalamove tới lấy hàng | **CÓ** | KHÔNG | Trạng thái vận chuyển ngắn hạn | Tài xế Lalamove đang tới lấy hàng | `/seller/orders/{id}` |
| | Giao hàng thành công tới khách | **CÓ** | KHÔNG | Cập nhật tiến độ | Đơn hàng #{orderCode} đã giao thành công | `/seller/orders/{id}` |
| | Tiền bán hàng giải ngân vào ví | **CÓ** | **CÓ** | Chứng từ nhận tiền doanh thu bán hàng (đã trừ hoa hồng) | Giải ngân tiền bán hàng #{orderCode}: +{amount} VND | `/wallet` |
| | Đơn hàng bị hủy | **CÓ** | **CÓ** | Cảnh báo đơn bán bị hủy để thu hồi hàng | Đơn hàng #{orderCode} đã bị hủy | `/seller/orders/{id}` |
| **Tin đăng** | Tin đăng được phê duyệt | **CÓ** | KHÔNG | Tin đã lên sàn, xem trực tiếp trên app | Tin đăng '{title}' đã được duyệt | `/posts/{id}` |
| | Tin đăng bị từ chối phê duyệt | **CÓ** | KHÔNG | Báo lý do để sửa bài trên app | Tin đăng '{title}' bị từ chối duyệt | `/seller/posts/{id}/edit` |
| **Kiểm định** | Có khách yêu cầu kiểm định | **CÓ** | KHÔNG | Yêu cầu vào app đồng ý hoặc từ chối | Yêu cầu kiểm định mới cho '{itemTitle}' | `/seller/inspections/{id}` |
| | Chốt lịch hẹn KTV tới nhà | **CÓ** | KHÔNG | Nhắc lịch đón KTV | Lịch hẹn KTV kiểm định: {appointmentTime} | `/seller/inspections/{id}` |
| | Kết quả kiểm định hoàn tất | **CÓ** | **CÓ** | Biên bản kỹ thuật chính thức bảo vệ tài sản người bán | [Biên bản] Kết quả kiểm định sản phẩm '{itemTitle}' | `/seller/inspections/{id}` |
| | Khách chốt mua sau kiểm định | **CÓ** | **CÓ** | Khách đã thanh toán tiền hàng, chuẩn bị giao máy | Khách hàng đã thanh toán mua '{itemTitle}' | `/seller/orders/{id}` |
| **Trả giá** | Có khách hàng đề xuất giá mới | **CÓ** | KHÔNG | Không spam mail deal dạo | Đề xuất trả giá mới: {price} VND cho '{itemTitle}' | `/seller/negotiations` |
| | Khách hàng hủy đề xuất giá | **CÓ** | KHÔNG | Không gửi mail rác | Khách hàng đã hủy đề xuất trả giá | `/seller/negotiations` |
| **Xác minh** | Hồ sơ Người bán được duyệt | **CÓ** | **CÓ** | Chúc mừng & hướng dẫn chính sách bán hàng của sàn | Chúc mừng! Hồ sơ Người bán của bạn đã được duyệt | `/seller/dashboard` |
| | Hồ sơ Người bán bị từ chối | **CÓ** | **CÓ** | Văn bản từ chối kèm lý do cụ thể và cách nộp lại | Kết quả xét duyệt hồ sơ Người bán SecondLife | `/seller/onboarding` |

---

### 2.3. Role: INSPECTION_CENTER (Quản lý Trung tâm kiểm định)
| Nghiệp vụ | Sự kiện kích hoạt | In-App + WS | Gmail | Lý do phân luồng | Tiêu đề thông báo | Deep Link |
|---|---|:---:|:---:|---|---|---|
| **Điều phối** | Đơn kiểm định mới đã cọc xong | **CÓ** | KHÔNG | Thao tác hàng ngày trên Dashboard trung tâm | Đơn kiểm định mới #{orderCode} cần gán KTV | `/center/inspections/{id}` |
| | KTV nộp xong biên bản kiểm định | **CÓ** | KHÔNG | Theo dõi tiến độ KTV nội bộ | KTV {inspectorName} đã hoàn thành đơn #{orderCode} | `/center/inspections/{id}` |
| **Phụ phí** | Admin duyệt đề xuất phụ phí đi lại | **CÓ** | KHÔNG | Cập nhật trực tiếp trên hệ thống | Đề xuất phụ phí đơn #{orderCode} đã được DUYỆT | `/center/inspections/{id}` |
| | Admin từ chối đề xuất phụ phí | **CÓ** | KHÔNG | Cập nhật trực tiếp trên hệ thống kèm lý do | Đề xuất phụ phí đơn #{orderCode} BỊ TỪ CHỐI | `/center/inspections/{id}` |

---

### 2.4. Role: INSPECTOR (Kỹ thuật viên kiểm định)
| Nghiệp vụ | Sự kiện kích hoạt | In-App + WS | Gmail | Lý do phân luồng | Tiêu đề thông báo | Deep Link |
|---|---|:---:|:---:|---|---|---|
| **Nhiệm vụ** | Được phân công đơn kiểm định | **CÓ** | KHÔNG | Thông báo nhiệm vụ qua app di động/web | Phân công kiểm định mới: #{orderCode} | `/inspector/inspections/{id}` |
| | Lịch hẹn kiểm định tại nhà ấn định | **CÓ** | KHÔNG | Nhắc lịch làm việc trong ngày | Lịch hẹn kiểm định: {time} tại {address} | `/inspector/inspections/{id}` |
| | Đơn kiểm định bị hủy | **CÓ** | KHÔNG | Hủy lịch công tác | Đơn kiểm định #{orderCode} đã bị hủy | `/inspector/inspections` |

---

### 2.5. Role: STAFF & ADMIN (Nhân viên & Quản trị viên sàn)
| Nghiệp vụ | Sự kiện kích hoạt | In-App + WS | Gmail | Lý do phân luồng | Tiêu đề thông báo | Deep Link |
|---|---|:---:|:---:|---|---|---|
| **Vận hành** | Hàng đợi tin đăng mới chờ duyệt | **CÓ** | KHÔNG | Nhận in-app notification trên giao diện quản trị | Có tin đăng mới chờ duyệt: '{title}' | `/staff/listings/pending` |
| | Hồ sơ eKYC cảnh báo nghi vấn | **CÓ** | KHÔNG | Đẩy vào hàng đợi xét duyệt thủ công | Hồ sơ eKYC {userEmail} cần xét duyệt thủ công | `/staff/verifications/pending` |
| | Trung tâm đề xuất duyệt phụ phí | **CÓ** | KHÔNG | Task quản trị tài chính trên admin portal | Đề xuất duyệt phụ phí cho đơn #{orderCode} | `/admin/fee-proposals/{id}` |
| | Cảnh báo hệ thống nghiêm trọng | **CÓ** | **CÓ** | Sự cố hệ thống/Lỗi cổng thanh toán/Lalamove | [CẢNH BÁO BẢO MẬT] Lỗi kết nối dịch vụ đối tác | `/admin/logs` |

---

### 2.6. Mọi Tài khoản (Bảo mật & Cấp quyền)
| Nghiệp vụ | Sự kiện kích hoạt | In-App + WS | Gmail | Lý do phân luồng | Tiêu đề thông báo | Deep Link |
|---|---|:---:|:---:|---|---|---|
| **Bảo mật** | Mã OTP đăng ký / OTP Quên mật khẩu | KHÔNG | **CÓ** | Chưa đăng nhập được app, bắt buộc gửi qua mail | Mã xác thực tài khoản SecondLife | `/verify-otp` |
| | Đổi mật khẩu thành công | **CÓ** | **CÓ** | Cảnh báo khẩn cấp phát hiện truy cập trái phép | [Cảnh báo bảo mật] Mật khẩu tài khoản đã thay đổi | `/profile/security` |
| | Khóa / Kích hoạt tài khoản | **CÓ** | **CÓ** | Quyết định kỷ luật tài khoản có hiệu lực pháp lý | Thông báo trạng thái tài khoản SecondLife | `/` |
| | Cấp tài khoản KTV / Trung tâm mới | KHÔNG | **CÓ** | Cung cấp email & mật khẩu khởi tạo cho nhân viên | Thông tin tài khoản nhân viên SecondLife | `/login` |

---

## 3. Thiết kế Database Schema (Flyway V42)

File: `src/main/resources/db/migration/V42__create_notifications_table.sql`:

```sql
-- V42__create_notifications_table.sql
-- Bảng lưu trữ thông báo in-app cho toàn bộ người dùng

CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    type VARCHAR(50) NOT NULL,                  -- ORDER, INSPECTION, LISTING, NEGOTIATION, WALLET, EKYC, SECURITY, SYSTEM
    reference_id UUID,                         -- ID đối tượng: order_id, inspection_id, post_id, v.v.
    reference_type VARCHAR(50),                -- "ORDER", "INSPECTION_ORDER", "POST", "NEGOTIATION", "WALLET_TRANSACTION"
    action_url VARCHAR(255),                   -- Deep link FE: "/orders/123", "/wallet", v.v.
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE, -- Xóa mềm (Soft delete)
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Index danh sách thông báo chưa xóa theo thời gian mới nhất (Tối ưu Pagination GET /notifications)
CREATE INDEX idx_notifications_user_list 
    ON notifications (user_id, created_at DESC) 
    WHERE is_deleted = FALSE;

-- Index đếm số thông báo chưa đọc siêu nhanh cho Badge quả chuông (GET /unread-count)
CREATE INDEX idx_notifications_user_unread 
    ON notifications (user_id) 
    WHERE is_deleted = FALSE AND is_read = FALSE;

-- Index tìm kiếm theo đối tượng liên kết
CREATE INDEX idx_notifications_reference 
    ON notifications (reference_type, reference_id);
```

---

## 4. Thiết kế RESTful API Contract

Tất cả các endpoints nằm tại `/api/v1/notifications` và bảo vệ bằng `@PreAuthorize("isAuthenticated()")`.

### 4.1. `GET /api/v1/notifications` (Xem tất cả / Lọc chưa đọc)
- **Tham số (Query Params)**:
  - `page`: int (mặc định `0`)
  - `size`: int (mặc định `20`)
  - `unreadOnly`: boolean (nếu `true` chỉ lấy tin chưa đọc)
  - `type`: String (tùy chọn: `ORDER`, `INSPECTION`, `LISTING`, `WALLET`, v.v.)
- **Response**: `200 OK`
```json
{
  "content": [
    {
      "id": "e4b2d15a-93a0-4541-86a2-6eb62817bf41",
      "title": "Bạn có đơn hàng mới!",
      "message": "Khách hàng vừa đặt đơn hàng #ORD-8932 cho 'MacBook Pro M1'.",
      "type": "ORDER",
      "referenceId": "73d42c38-16e5-4f10-91ae-b295054ea746",
      "referenceType": "ORDER",
      "actionUrl": "/seller/orders/73d42c38-16e5-4f10-91ae-b295054ea746",
      "isRead": false,
      "readAt": null,
      "createdAt": "2026-10-14T13:15:30Z"
    }
  ],
  "pageNumber": 0,
  "pageSize": 20,
  "totalElements": 15,
  "totalPages": 1,
  "last": true
}
```

### 4.2. `GET /api/v1/notifications/unread-count` (Lấy số badge quả chuông)
- **Response**: `200 OK`
```json
{
  "unreadCount": 3
}
```

### 4.3. `PATCH /api/v1/notifications/{id}/read` (Đánh dấu 1 tin đã đọc)
- **Response**: `200 OK`
```json
{
  "id": "e4b2d15a-93a0-4541-86a2-6eb62817bf41",
  "isRead": true,
  "readAt": "2026-10-14T13:20:00Z"
}
```

### 4.4. `PATCH /api/v1/notifications/read-all` (Đánh dấu tất cả đã đọc)
- **Response**: `200 OK`
```json
{
  "updatedCount": 3,
  "message": "Tất cả thông báo đã được đánh dấu là đã đọc."
}
```

### 4.5. `DELETE /api/v1/notifications/{id}` (Xóa 1 thông báo cụ thể)
- **Mô tả**: Xóa mềm 1 thông báo theo ID (chỉ được xóa thông báo thuộc về chính mình).
- **Response**: `200 OK`
```json
{
  "success": true,
  "message": "Đã xóa thông báo thành công."
}
```

### 4.6. `DELETE /api/v1/notifications/clear-read` (Xóa tất cả thông báo đã đọc)
- **Mô tả**: Dọn dẹp các thông báo đã đọc, giữ lại các thông báo chưa đọc quan trọng.
- **Response**: `200 OK`
```json
{
  "deletedCount": 12,
  "message": "Đã xóa tất cả thông báo đã đọc."
}
```

### 4.7. `DELETE /api/v1/notifications/clear-all` (Xóa toàn bộ mọi thông báo)
- **Mô tả**: Dọn sạch toàn bộ hòm thư thông báo của người dùng.
- **Response**: `200 OK`
```json
{
  "deletedCount": 15,
  "message": "Đã dọn sạch toàn bộ thông báo."
}
```

---

## 5. Thiết kế Real-time WebSocket Push

### 5.1. Kênh STOMP
- **WebSocket Endpoint**: `ws://localhost:8080/ws`
- **Headers khi kết nối**: `Authorization: Bearer <JWT_ACCESS_TOKEN>`
- **STOMP Destination đăng ký**: `/user/queue/notifications`

### 5.2. Luồng đẩy đa kênh an toàn
```
[Nghiệp vụ (Ví dụ: OrderService / InspectionService)]
                      │
                      ▼
        [NotificationService.notify(...)]
                      │
        ┌─────────────┴────────────────┐
        ▼                              ▼
  [Kênh In-App + WS]              [Kênh Gmail]
 (Mọi sự kiện nghiệp vụ)    (Chỉ các sự kiện quan trọng)
        │                              │
        ├─► Lưu DB (notifications)     └─► Gửi HTML email chuyên nghiệp
        └─► SimpMessagingTemplate          (JavaMailSender / Async Thread)
            gửi tới /user/queue/notifications
```

---

## 6. Kế hoạch Chi tiết các Điểm Cắm Trigger vào Backend

Triển khai cắm trigger gọi `notificationService.notify(...)` tại đúng các vị trí:

1. **`OrderServiceImpl`**:
   - `createOrder`:
     - In-App + Mail cho Buyer (Xác nhận đặt hàng & hóa đơn ký quỹ).
     - In-App + Mail cho Seller (Thông báo có đơn mới cần giao).
   - `markAsShipped`: In-App cho Buyer (Đơn đang được giao).
   - `confirmDelivery`:
     - In-App cho Buyer (Cảm ơn đã hoàn tất đơn hàng).
     - In-App + Mail cho Seller (Thông báo nhận tiền giải ngân ký quỹ).
   - `cancelOrder`:
     - In-App + Mail cho Buyer (Thông báo hủy đơn & hoàn tiền vào ví).
     - In-App + Mail cho Seller (Thông báo đơn hàng bị hủy).
2. **`OrderAutoReleaseScheduler`**:
   - Khi giải ngân sau 48h: In-App + Mail cho Seller (Tiền bán hàng đã giải ngân vào ví).
3. **`ShipmentService` (Lalamove Webhook)**:
   - `DRIVER_ASSIGNED`: In-App cho Seller (Tài xế đang tới lấy).
   - `PICKED_UP`: In-App cho Buyer (Tài xế đã lấy hàng và đang giao).
   - `COMPLETED`: In-App cho Buyer (Đã giao hàng, nhắc xác nhận trong 48h).
4. **`InspectionServiceImpl`**:
   - `requestInspection`: In-App cho Seller (Có khách yêu cầu kiểm định).
   - `respondInspection`: In-App cho Buyer (Seller đồng ý/từ chối). Nếu ACCEPT -> In-App cho Inspection Center (Đơn mới cần phân công).
   - `assignInspector`: In-App cho Inspector (Nhiệm vụ kiểm định mới).
   - `scheduleAppointment`: In-App cho Buyer, Seller, Inspector (Lịch hẹn tại nhà).
   - `submitResult`:
     - Nếu PASSED: In-App + Mail cho Buyer (Biên bản kiểm định ĐẠT, nhắc chốt mua trong 24h); In-App cho Seller.
     - Nếu FAILED/RÁCH SEAL: In-App + Mail cho Buyer & Seller (Biên bản kiểm định KHÔNG ĐẠT và thủ tục hoàn cọc/bồi thường).
   - `confirmFinalPurchase`: In-App + Mail cho Seller (Khách đã thanh toán chốt mua).
5. **`ListingReviewService`**:
   - `approve`: In-App cho Seller (Tin đăng đã được duyệt).
   - `reject`: In-App cho Seller (Tin đăng bị từ chối kèm lý do).
6. **`NegotiationServiceImpl`**:
   - `createNegotiation`: In-App cho Seller (Khách đề xuất giá mới).
   - `acceptNegotiation`: In-App cho Buyer (Người bán chấp nhận giá).
   - `rejectNegotiation`: In-App cho Buyer (Người bán từ chối giá).
7. **`WalletServiceImpl` / `PaymentCallbackInboxService`**:
   - `deposit`: In-App + Mail cho User (Biên lai nạp tiền ví thành công).
   - `processRefund`: In-App + Mail cho User (Biên lai hoàn tiền).
8. **`SellerVerificationServiceImpl`**:
   - Duyệt Seller: In-App + Mail cho User (Chào mừng trở thành Người bán).
   - Từ chối Seller: In-App + Mail cho User (Thông báo từ chối kèm lý do).

---

## 7. Lộ trình Thực thi Từng Bước (Implementation Steps)

1. **Bước 1**: Tạo migration `V42__create_notifications_table.sql`.
2. **Bước 2**: Tạo Entity `Notification`, Repository `NotificationRepository` với các derived & custom queries tối ưu.
3. **Bước 3**: Tạo các DTOs (`NotificationResponseDTO`, `UnreadCountResponseDTO`).
4. **Bước 4**: Nâng cấp `NotificationService` và `NotificationServiceImpl` hỗ trợ:
   - Lưu trữ In-App vào DB.
   - Bắn WebSocket STOMP real-time qua `SimpMessagingTemplate`.
   - Giữ các method gửi Email có chọn lọc chống spam.
   - Các logic đọc, đếm chưa đọc, xóa 1 cái, xóa đã đọc, xóa tất cả.
5. **Bước 5**: Tạo `NotificationController` tại `/api/v1/notifications` với đầy đủ 7 endpoints chuẩn REST.
6. **Bước 6**: Cắm trigger gọi thông báo vào các Service: `OrderServiceImpl`, `ShipmentService`, `InspectionServiceImpl`, `ListingReviewService`, `NegotiationServiceImpl`, `WalletServiceImpl`.
7. **Bước 7**: Viết Unit Test và Integration Test kiểm chứng toàn bộ chức năng.
