# TÀI LIỆU TỔNG HỢP THAY ĐỔI BACKEND DÀNH CHO FRONTEND
## HOÀN THIỆN LUỒNG KIỂM ĐỊNH (INSPECTION FLOW ENHANCEMENTS)

- **Dự án**: Sàn Thương Mại Điện Tử Đồ Cũ SecondLife (SEP490)
- **Nhánh tích hợp**: `dev` (Commit: `bd9ca54`)
- **Ngày cập nhật**: 16/10/2026
- **Đối tượng áp dụng**: Đội ngũ Frontend Web & Mobile App (React / Flutter)

---

## 1. TỔNG QUAN CÁC THAY ĐỔI MỚI

Trong đợt cập nhật này, Backend đã bổ sung và hoàn thiện 6 nội dung trọng tâm cho luồng Kiểm định:
1. **API Tra cứu Tem Niêm Phong Công Khai**: Bổ sung `productImageUrl` (ảnh sản phẩm) và tọa độ địa lý `latitude`/`longitude` trong lịch sử quét tem.
2. **Rate Limiting Chống Spam Tra Cứu Tem**: Giới hạn 30 requests/phút/IP cho các endpoint công khai `/api/v1/inspection/seal/*`.
3. **Mã Tem Niêm Phong Chống Làm Giả (Tamper Seal)**: Sinh mã theo định dạng chuẩn có HMAC SHA-256 Checksum (`SL-SEAL-{YYYYMMDD}-{6HEX}-{2HEX_CHECKSUM}`) và API cho KTV sinh tem trước khi dán.
4. **Ghi Nhận Quét Tem Kèm Tọa Độ GPS**: API `POST /api/v1/inspection/seal/scan` hỗ trợ nhận `latitude` và `longitude` để vẽ bản đồ hành trình.
5. **Cơ Chế Bồi Thường Khi Người Mua Quá Hạn Thanh Toán**: Quá 6 giờ sau khi kiểm định Đạt (`PASSED`), tiền cọc phí kiểm định của Người mua sẽ được giải ngân đền bù trực tiếp vào ví Người bán.
6. **Thời Hạn Lưu Trữ Video Bằng Chứng**: Nâng thời hạn lưu trữ video trên Cloudinary từ 48 giờ lên **14 ngày**.
7. **Tự Động Đồng Bộ Chặng Vận Chuyển Từ Lalamove**: Webhook Lalamove tự động ghi nhận nhật ký quét tem khi tài xế lấy hàng và giao hàng.

---

## 2. CHI TIẾT CÁC CONTRACTS VÀ API ENDPOINTS

### 2.1. Tra cứu & Xác thực Tem Niêm Phong Số (Public Seal Verification)

- **Endpoint**: `GET /api/v1/inspection/seal/{tamperSealId}`
- **Quyền truy cập**: Public (Không yêu cầu đăng nhập token)
- **Rate Limit**: **30 requests / phút / IP** (Vượt ngưỡng trả về HTTP status `429 Too Many Requests`).
- **Response Model**: `ApiResponse<InspectionSealVerificationResponse>`

#### Cấu trúc JSON Response:
```json
{
  "code": 200,
  "message": "Xác thực tem niêm phong thành công",
  "data": {
    "tamperSealId": "SL-SEAL-20261016-D4E5F6-3A",
    "orderId": "651a9985-714e-4f30-863a-2df28362629b",
    "productTitle": "iPhone 13 Pro Max 128GB Xanh Sierra",
    "productImageUrl": "https://res.cloudinary.com/secondlife/image/upload/v1/products/iphone13.jpg", // [MỚI] Ảnh đại diện sản phẩm
    "conditionGrade": "A",
    "conditionScore": 95,
    "sealedAt": "2026-10-16T07:15:00Z",
    "sealedByName": "Nguyễn Văn Kỹ Thuật",
    "centerName": "Trung tâm Kiểm định SecondLife Tân Bình",
    "status": "PASSED",
    "reportSummary": "Máy nguyên zin, pin 98%, màn hình không trầy xước.",
    "scanLogs": [
      {
        "id": "18f9cf38-8c01-447a-a63e-32d2073919b1",
        "stage": "CENTER_RECEIVE",
        "scannedAt": "2026-10-16T07:15:00Z",
        "scannedByName": "Nguyễn Văn Kỹ Thuật",
        "notes": "Niêm phong tem QR sau khi thẩm định đạt",
        "latitude": 10.7991230,   // [MỚI] Vĩ độ
        "longitude": 106.6534560   // [MỚI] Kinh độ
      },
      {
        "id": "22e8db92-410a-4298-8be4-f584e03f9011",
        "stage": "CARRIER_HANDOVER",
        "scannedAt": "2026-10-16T08:00:00Z",
        "scannedByName": "Lalamove Delivery",
        "notes": "Tài xế nhận hàng vận chuyển tới người mua",
        "latitude": 10.8010000,
        "longitude": 106.6540000
      }
    ]
  }
}
```

#### Xử lý lỗi Rate Limiting phía Frontend:
```javascript
// Ví dụ xử lý Axios Interceptor / Catch block
if (error.response && error.response.status === 429) {
  toast.error("Bạn đang tra cứu quá thường xuyên. Vui lòng thử lại sau 1 phút!");
}
```

---

### 2.2. Kỹ Thuật Viên Sinh Tem Niêm Phong (Inspector Seal Generation)

- **Endpoint**: `POST /api/v1/inspector/orders/{orderId}/generate-seal`
- **Quyền truy cập**: Bearer Token (Role `INSPECTOR`)
- **Mục đích**: Sinh mã tem QR chuẩn trước khi in ra tem vật lý dán niêm phong lên hộp sản phẩm.
- **Request Body**: Trống
- **Response Model**:
  ```json
  {
    "code": 200,
    "message": "Sinh mã tem niêm phong thành công",
    "data": "SL-SEAL-20261016-D4E5F6-3A"
  }
  ```
- **Quy chuẩn mã tem**:
  - Tiền tố: `SL-SEAL-`
  - Ngày sinh mã: `YYYYMMDD` (8 ký tự)
  - Chuỗi ngẫu nhiên: `XXXXXX` (6 ký tự Hex)
  - Mã kiểm tra: `YY` (2 ký tự Hex Checksum được mã hóa bảo mật từ Secret Salt)
  - Khi dán hoặc nộp kết quả (`POST /api/v1/inspector/orders/{orderId}/submit-result`), nếu KTV truyền `tamperSealId`, BE sẽ tự động xác thực Checksum. Nếu cố tình sửa số mã tem, BE sẽ từ chối với lỗi `400 Bad Request: Mã tem niêm phong không hợp lệ hoặc đã bị can thiệp`.

---

### 2.3. Ghi Nhận Quét Tem Kèm Tọa Độ GPS (Scan Log)

- **Endpoint**: `POST /api/v1/inspection/seal/scan`
- **Quyền truy cập**: Public hoặc Authenticated User/Driver/Staff
- **Request Body**:
  ```json
  {
    "tamperSealId": "SL-SEAL-20261016-D4E5F6-3A",
    "stage": "SELLER_TO_CENTER", // Xem danh sách stage bên dưới
    "notes": "Đã nhận hàng từ người bán tại nhà",
    "latitude": 10.776889,   // [MỚI] Tùy chọn (float/double)
    "longitude": 106.700806  // [MỚI] Tùy chọn (float/double)
  }
  ```

#### Danh sách các Stage hợp lệ:
| Stage Enum | Ý Nghĩa | Ghi Chú |
| :--- | :--- | :--- |
| `SELLER_TO_CENTER` | Người bán bàn giao gửi tới Hub | Khi gửi chuyển phát |
| `CENTER_RECEIVE` | Hub kiểm định tiếp nhận hàng | KTV quét lúc nhập kho |
| `CARRIER_HANDOVER` | Bàn giao cho tài xế giao tới người mua | Lalamove tự động hoặc thủ công |
| `DELIVERED_BUYER` | Đã giao tận tay người mua | Kết thúc hành trình |
| `RETURN_TO_SELLER` | Trả hàng về cho người bán | Khi thẩm định không đạt (FAILED) |

#### Khuyến nghị triển khai FE:
Khi người dùng bấm Quét tem trên Web/App:
```javascript
navigator.geolocation.getCurrentPosition(
  (pos) => {
    callScanApi({
      tamperSealId,
      stage,
      notes,
      latitude: pos.coords.latitude,
      longitude: pos.coords.longitude
    });
  },
  (err) => {
    // Nếu user từ chối quyền định vị vẫn gửi request bình thường
    callScanApi({ tamperSealId, stage, notes });
  }
);
```

---

### 2.4. Luồng Bồi Thường Escrow Khi Người Mua Bỏ Đơn (6 Hours Expiry)

- **Thời hạn**: Người mua có **6 tiếng** để thanh toán đơn hàng sau khi đơn kiểm định chuyển sang trạng thái `PASSED`.
- **Cơ chế bồi thường**:
  - Nếu hết 6 tiếng Người mua không thanh toán:
    - Trạng thái đơn kiểm định chuyển thành `EXPIRED` / `CANCELLED`.
    - Phần tiền cọc phí kiểm định của người mua (`buyerInspectionFeeShare`) sẽ được bồi hoàn vào số dư ví SecondLife của **Người bán**.
    - Hệ thống phát sinh thông báo qua Webhook / Notification tới cả 2 bên.
- **Giao diện FE hiển thị**:
  - Đối với người bán: Hiển thị thông báo *"Đơn hàng kiểm định #... đã bị hủy do Người mua không thanh toán tiền hàng trong 6 giờ. Bạn đã được bồi thường [Số tiền] vào ví."*
  - Đối với người mua: Hiển thị thông báo *"Đơn hàng kiểm định #... đã hết hạn thanh toán. Tiền cọc phí kiểm định của bạn đã được chuyển cho Người bán để bù đắp chi phí."*

---

### 2.5. Thời Hạn Video Bằng Chứng Kiểm Định (Cloudinary)
- Video ghi lại quá trình kiểm định được lưu trữ và có hiệu lực truy cập trong **14 ngày** (thay vì 48 giờ như trước).
- FE có thể tự tin hiển thị nút "Xem video kiểm định" trong suốt 14 ngày trên trang chi tiết đơn hàng hoặc trang khiếu nại.

---

## 3. CHECKLIST KIỂM THỬ TÍCH HỢP CHO FRONTEND

- [ ] **Màn hình Tra cứu Tem QR (`/seal/:sealId`)**:
  - [ ] Hiển thị hình ảnh sản phẩm đại diện (`productImageUrl`).
  - [ ] Hiển thị danh sách các trạm quét (`scanLogs`) kèm vị trí địa lý nếu có.
  - [ ] Thử spam reload trang liên tục để xác nhận giao diện hiển thị thông báo lỗi `429` thân thiện.
- [ ] **Ứng dụng Kỹ Thuật Viên (`/inspector/orders/:id`)**:
  - [ ] Nút "Sinh mã tem QR niêm phong" gọi `POST /api/v1/inspector/orders/{id}/generate-seal` và hiển thị mã tem để in.
  - [ ] Form nộp kết quả (`submit-report`) chấp nhận mã tem đã sinh.
- [ ] **Màn hình Quét Tem Chặng Vận Chuyển**:
  - [ ] Tích hợp Geolocation để đính kèm `latitude`, `longitude` khi gửi `POST /api/v1/inspection/seal/scan`.
- [ ] **Màn hình Chi tiết Đơn hàng (Người Mua & Người Bán)**:
  - [ ] Đếm ngược 6 tiếng thanh toán sau khi kiểm định `PASSED`.
  - [ ] Hiển thị thông báo bồi thường ví nếu đơn bị hủy do hết hạn 6 tiếng.
  - [ ] Video kiểm định xem được trong vòng 14 ngày.
