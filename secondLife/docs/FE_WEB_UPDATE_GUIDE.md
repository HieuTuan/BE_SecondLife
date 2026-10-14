# HƯỚNG DẪN ĐIỀU CHỈNH & TÍCH HỢP FRONTEND WEB (FE WEB UPDATE GUIDE)

**Dự án**: SecondLife Marketplace – Web Portal (Buyer, Seller, Admin)  
**Ngày cập nhật**: 14/10/2026  
**Trạng thái Backend**: Đã triển khai hoàn tất và vượt qua 100% test (758/758 tests).  
**Mục tiêu**: Hướng dẫn đội ngũ Frontend Web điều chỉnh các endpoint bị thay đổi (tránh lỗi Breaking Change), đồng thời bổ sung các giao diện mới cho tính năng Rút tiền và Vòng đời Video kiểm định.

---

## MỤC LỤC
1. [Breaking Change: Đổi URL Tải & Sửa Bài Đăng Bản Nháp](#1-breaking-change-đổi-url-tải--sửa-bài-đăng-bản-nháp)
2. [Tối ưu Trang Chi tiết Sản phẩm Public (Marketplace)](#2-tối-ưu-trang-chi-tiết-sản-phẩm-public-marketplace)
3. [Giao diện Video Bằng chứng Kiểm định (Xử lý Tự động Dọn dẹp sau 48h)](#3-giao-diện-video-bằng-chứng-kiểm-định-xử-lý-tự-động-dọn-dẹp-sau-48h)
4. [Giao diện Ví & Yêu cầu Rút tiền cho Người dùng (Seller/Buyer)](#4-giao-diện-ví--yêu-cầu-rút-tiền-cho-người-dùng-sellerbuyer)
5. [Giao diện Quản trị viên Phê duyệt Rút tiền (Admin Portal)](#5-giao-diện-quản-trị-viên-phê-duyệt-rút-tiền-admin-portal)
6. [Bảng Tổng hợp Endpoints dành cho Web FE](#6-bảng-tổng-hợp-endpoints-dành-cho-web-fe)

---

## 1. BREAKING CHANGE: ĐỔI URL TẢI & SỬA BÀI ĐĂNG BẢN NHÁP

### 1.1. Vấn đề thay đổi
Trước đây, route `GET /api/v1/posts/{postId}` được dùng chung cho việc lấy bản nháp (draft) của Seller. Hiện tại, Backend đã tách bạch hoàn toàn quyền hạn:
- **`GET /api/v1/posts/{postId}`**: Trở thành API công khai cho Buyer xem chi tiết sản phẩm đã duyệt.
- **`GET /api/v1/posts/{postId}/draft`**: Endpoint chuyên biệt dành riêng cho Seller để tải lại bản nháp bài đăng của mình và tiếp tục chỉnh sửa.

### 1.2. Hành động cần làm trên FE Web
Tại trang **Chỉnh sửa bản nháp bài đăng (Edit Draft / Continue Listing)**:
- **URL cũ**: `GET /api/v1/posts/${postId}` ❌
- **URL mới**: `GET /api/v1/posts/${postId}/draft` ✅
- **Headers**: Bắt buộc có `Authorization: Bearer <seller_token>`

```typescript
// services/postService.ts
export const getDraftPostDetail = async (postId: string): Promise<ListingDraftResponse> => {
  const response = await apiClient.get<ApiResponse<ListingDraftResponse>>(
    `/api/v1/posts/${postId}/draft`
  );
  return response.data.data;
};
```

---

## 2. TỐI ƯU TRANG CHI TIẾT SẢN PHẨM PUBLIC (MARKETPLACE)

### 2.1. Thay đổi cơ chế xác thực
- **Endpoint**: `GET /api/v1/posts/{postId}`
- **Quyền hạn**: **Public (`permitAll()`)**. Khách vãng lai chưa đăng nhập (Guest) hoặc Buyer đều gọi được.
- **Hành động FE**: Loại bỏ việc bắt buộc phải có `accessToken` mới cho phép mở trang chi tiết sản phẩm. Nếu có token thì vẫn gửi kèm trong header (BE tự nhận diện), nếu không có token thì gọi không kèm header `Authorization`.

### 2.2. Dữ liệu trả về (`MarketplaceListingResponse`)
```typescript
export interface MarketplaceListingResponse {
  id: string;
  sellerId: string;
  sellerName: string;
  sellerAvatarUrl?: string;
  sellerRating?: number;
  title: string;
  description: string;
  price: number;
  model: string;
  brand: string;
  categoryName: string;
  images: { id: string; url: string; isCover: boolean }[];
  requiresInspection: boolean;
  status: string; // "APPROVED", "SOLD", etc.
  createdAt: string;
  updatedAt: string;
}
```

---

## 3. GIAO DIỆN VIDEO BẰNG CHỨNG KIỂM ĐỊNH (XỬ LÝ TỰ ĐỘNG DỌN DẸP SAU 48H)

### 3.1. Cơ chế Vòng đời Video mới
Để tránh tràn bộ nhớ Cloudinary Free và hỗ trợ cả video kiểm định mổ máy dài 1–2 tiếng, Backend áp dụng:
1. **Video trực tiếp trên hệ thống (Cloudinary)**: Tự động xóa file sau **48 giờ** kể từ khi giải ngân hoàn tất đơn hàng hoặc đơn bị huỷ.
2. **Video link ngoài (YouTube Không công khai / Google Drive)**: Giữ nguyên link, không bị xóa (`videoCleanupStatus = "EXEMPTED"`).

### 3.2. Cấu trúc trường mới trong `InspectionOrderResponse`
Khi gọi `GET /api/v1/inspections/{orderId}` hoặc `GET /api/v1/inspector/orders/{orderId}`:
```typescript
export interface InspectionOrderResponse {
  id: string;
  inspectionStatus: string; // ASSIGNED_TO_INSPECTOR, INSPECTING, PASSED, FAILED
  videoProofUrl?: string;
  videoProofPublicId?: string;
  videoCleanupStatus?: 'ACTIVE' | 'CLEANED' | 'EXEMPTED';
  cleanupMessage?: string;
  videoRetentionUntil?: string; // ISO 8601 UTC timestamp
  // ... các trường khác
}
```

### 3.3. Component React gợi ý hiển thị Video Bằng chứng
```tsx
import React from 'react';
import { Alert, Typography, Box, Button } from '@mui/material';
import LockClockIcon from '@mui/icons-material/LockClock';
import VideoLibraryIcon from '@mui/icons-material/VideoLibrary';

interface VideoProofViewerProps {
  videoUrl?: string;
  cleanupStatus?: 'ACTIVE' | 'CLEANED' | 'EXEMPTED';
  cleanupMessage?: string;
  retentionUntil?: string;
}

export const InspectionVideoProofViewer: React.FC<VideoProofViewerProps> = ({
  videoUrl,
  cleanupStatus,
  cleanupMessage,
  retentionUntil
}) => {
  if (cleanupStatus === 'CLEANED') {
    return (
      <Alert severity="info" icon={<LockClockIcon />}>
        <Typography variant="subtitle2" fontWeight="bold">
          Video bằng chứng đã được tự động dọn dẹp
        </Typography>
        <Typography variant="body2">
          {cleanupMessage ||
            'Video kiểm định gốc đã được tự động dọn dẹp sau 48h giải ngân để bảo mật dữ liệu & tối ưu tài nguyên.'}
        </Typography>
      </Alert>
    );
  }

  if (!videoUrl) {
    return <Typography variant="body2" color="text.secondary">Không có video bằng chứng</Typography>;
  }

  // Trường hợp 1: Link YouTube Unlisted
  if (videoUrl.includes('youtube.com') || videoUrl.includes('youtu.be')) {
    const videoId = videoUrl.includes('youtu.be')
      ? videoUrl.split('/').pop()?.split('?')[0]
      : new URL(videoUrl).searchParams.get('v');
    return (
      <Box sx={{ position: 'relative', paddingTop: '56.25%', borderRadius: 2, overflow: 'hidden' }}>
        <iframe
          src={`https://www.youtube.com/embed/${videoId}`}
          title="Inspection Video Proof"
          style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', border: 0 }}
          allowFullScreen
        />
      </Box>
    );
  }

  // Trường hợp 2: Link Google Drive hoặc link ngoài
  if (videoUrl.includes('drive.google.com')) {
    return (
      <Button
        variant="outlined"
        startIcon={<VideoLibraryIcon />}
        href={videoUrl}
        target="_blank"
        rel="noopener noreferrer"
      >
        Mở video kiểm định chi tiết trên Google Drive
      </Button>
    );
  }

  // Trường hợp 3: Video Cloudinary đang ACTIVE
  return (
    <Box>
      <video
        src={videoUrl}
        controls
        style={{ width: '100%', maxHeight: 420, borderRadius: 8, backgroundColor: '#000' }}
      />
      {retentionUntil && (
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
          * Video sẽ được hệ thống tự động lưu trữ đến hết: {new Date(retentionUntil).toLocaleString('vi-VN')}
        </Typography>
      )}
    </Box>
  );
};
```

---

## 4. GIAO DIỆN VÍ & YÊU CẦU RÚT TIỀN CHO NGƯỜI DÙNG (SELLER/BUYER)

### 4.1. Thông tin Ví hiện tại
- **Endpoint**: `GET /api/v1/wallets/me`
- **Response**:
```json
{
  "id": "e44d4fa7-0801-4be3-b9ad-80eb9a2ec22d",
  "userId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "balance": 1850000.00,
  "updatedAt": "2026-10-14T09:00:00Z"
}
```

### 4.2. Modal Tạo yêu cầu Rút tiền
- **Endpoint**: `POST /api/v1/wallets/withdrawals`
- **Validation**:
  - `amount`: Số tiền cần rút $\ge$ 50,000 VND và không được vượt quá `balance`.
  - `bankCode`: Mã ngân hàng (ví dụ: `VCB`, `MB`, `TCB`, `ACB`, `BIDV`).
  - `accountNumber`: Số tài khoản ngân hàng.
  - `accountHolderName`: Tên chủ tài khoản (in hoa không dấu).
- **Request Body**:
```json
{
  "amount": 500000,
  "bankCode": "MB",
  "accountNumber": "033123456789",
  "accountHolderName": "NGUYEN VAN A"
}
```
- **Xử lý UX**:
  - Khi submit thành công, hiển thị Toast: *"Yêu cầu rút tiền đã được tiếp nhận và chuyển đến Admin xử lý."*
  - Tiền trong ví sẽ được trừ ngay lập tức để tránh người dùng rút 2 lần.

### 4.3. Bảng Lịch sử Rút tiền
- **Endpoint**: `GET /api/v1/wallets/withdrawals?page=0&size=10`
- **Mapping Trạng thái (Status Badge)**:
  - `PENDING_REVIEW`: Màu Vàng (Chờ duyệt)
  - `APPROVED`: Màu Xanh lá (Đã giải ngân)
  - `REJECTED`: Màu Đỏ (Bị từ chối) $\rightarrow$ hover vào để xem `adminNote` giải thích lý do (tiền đã được hệ thống tự động hoàn lại vào số dư ví).

---

## 5. GIAO DIỆN QUẢN TRỊ VIÊN PHÊ DUYỆT RÚT TIỀN (ADMIN PORTAL)

### 5.1. Danh sách yêu cầu rút tiền cho Admin
- **Endpoint**: `GET /api/v1/wallets/admin/withdrawals`
- **Query Params**:
  - `status`: `PENDING_REVIEW` | `APPROVED` | `REJECTED` (bỏ trống để lấy tất cả)
  - `page`: Số trang (0-indexed)
  - `size`: Kích thước trang
- **Quyền**: Yêu cầu quyền Admin (`hasRole('ADMIN')` hoặc `ADMIN_RBAC_MANAGE`).

### 5.2. Modal Thao tác Duyệt / Từ chối
- **Endpoint**: `POST /api/v1/wallets/admin/withdrawals/{id}/review`
- **Request Body**:
```json
{
  "action": "APPROVE", // hoặc "REJECT"
  "adminNote": "Đã chuyển khoản qua Vietcombank ngày 14/10 mã GD 998811"
}
```
- **Hành vi**:
  - Khi Admin chọn **`APPROVE`**: Đơn chuyển sang `APPROVED`, gửi thông báo đến chuông và email của User.
  - Khi Admin chọn **`REJECT`**: Đơn chuyển sang `REJECTED`, hệ thống **tự động hoàn lại 100% số tiền** về ví người dùng, gửi thông báo ghi rõ lý do.

---

## 6. BẢNG TỔNG HỢP ENDPOINTS DÀNH CHO WEB FE

| Tính năng | Phương thức & URL | Yêu cầu Auth | Ghi chú |
| :--- | :--- | :---: | :--- |
| **Chi tiết sản phẩm** | `GET /api/v1/posts/{postId}` | Không | Public cho Buyer/Guest xem bài đăng |
| **Bản nháp bài đăng** | `GET /api/v1/posts/{postId}/draft` | Có (Seller) | **Breaking change thay cho URL cũ** |
| **Xem số dư ví** | `GET /api/v1/wallets/me` | Có | Xem balance hiện tại |
| **Tạo yêu cầu rút tiền** | `POST /api/v1/wallets/withdrawals` | Có | Trừ tiền ngay, tạo đơn chờ duyệt |
| **Lịch sử rút tiền user** | `GET /api/v1/wallets/withdrawals` | Có | Phân trang danh sách đơn rút |
| **Admin: Danh sách rút** | `GET /api/v1/wallets/admin/withdrawals` | Admin | Lọc theo status |
| **Admin: Duyệt rút tiền** | `POST /api/v1/wallets/admin/withdrawals/{id}/review` | Admin | APPROVE hoặc REJECT kèm note |
| **AI Định giá độc lập** | `POST /api/v1/ai/estimate-price` | Có | Định giá sản phẩm trực tiếp từ BE (bảo mật Gemini key) |
| **Tra cứu tem niêm phong QR**| `GET /api/v1/inspection/seal/{tamperSealId}` | Không | Public cho khách/Buyer quét mã tem niêm phong |
| **Ghi nhận lịch sử quét tem**| `POST /api/v1/inspection/seal/scan` | Có | Dành cho KTV/Shipper/Buyer ghi nhận quét tem |

---

## 7. TÍCH HỢP AI ĐỊNH GIÁ TRỰC TIẾP TRÊN BACKEND (LOẠI BỎ NODE EXPRESS FE)

### 7.1. Bối cảnh
Trước đây, Frontend Web phải tự chạy một Express Node.js server phụ để giấu API Key của Google Gemini khi người dùng đăng bài cần gợi ý giá. Hiện tại Backend Spring Boot đã tiếp nhận và cung cấp endpoint trực tiếp với bảo mật tối đa:
- **Endpoint mới**: `POST /api/v1/ai/estimate-price` (hỗ trợ cả alias `POST /api/ai/estimate-price`).
- **Xác thực**: Yêu cầu Bearer Token `isAuthenticated()`.
- **Request Body**:
```json
{
  "title": "MacBook Pro M1 2020 16GB 512GB",
  "category": "Laptop",
  "brand": "Apple",
  "model": "MacBook Pro M1",
  "itemCondition": "LIKE_NEW",
  "description": "Máy cá nhân dùng ít, pin 95%",
  "imageUrls": [
    "https://res.cloudinary.com/secondlife/image/upload/v123/macbook1.jpg"
  ]
}
```
- **Response**:
```json
{
  "success": true,
  "message": "Ước lượng giá thành công",
  "data": {
    "fairPriceMin": 18000000,
    "fairPriceMax": 21000000,
    "suggestedPrice": 19500000,
    "expectedSellTime": "3-5 ngày",
    "modelVersion": "gemini-2.5-flash"
  }
}
```

---

## 8. XÁC THỰC TEM NIÊM PHONG KIỂM ĐỊNH QUA MÃ QR (TAMPER-EVIDENT QR SEAL)

### 8.1. Quy trình Nghiệp vụ
1. Trung tâm kiểm định SecondLife dán **Tem niêm phong vỡ (Tamper-evident Seal)** lên sản phẩm đã kiểm định, trên tem có in mã QR chứa `tamperSealId` (VD: `SL-SEAL-20261014-9988`).
2. Khách hàng hoặc Shipper dùng Camera / Trình quét mã QR trên Web hoặc Mobile để kiểm tra nguồn gốc và tính nguyên vẹn của tem.

### 8.2. Tra cứu công khai tem niêm phong (`GET /api/v1/inspection/seal/{tamperSealId}`)
- **Quyền hạn**: `permitAll()` (Công khai, không cần đăng nhập).
- **Ví dụ gọi**: `GET /api/v1/inspection/seal/SL-SEAL-20261014-9988`
- **Dữ liệu trả về**:
```json
{
  "success": true,
  "message": "Xác thực tem niêm phong thành công",
  "data": {
    "tamperSealId": "SL-SEAL-20261014-9988",
    "inspectionOrderId": "b78b53f6-6c17-4efc-8b89-a29eb431eb60",
    "postId": "a190539c-2ef4-4f0f-8fa2-379e49a888c3",
    "productTitle": "iPhone 13 Pro Max 128GB",
    "conditionScore": 92,
    "conditionGrade": "A",
    "inspectionStatus": "PASSED",
    "inspectionCenterName": "Trung tâm kiểm định SecondLife",
    "inspectorName": "Nguyễn Văn A",
    "inspectedAt": "2026-10-14T08:30:00Z",
    "currentSealStatus": "INTACT", // hoặc "TAMPERED" nếu đã bị rách/mở
    "officialSecondLifeSeal": true,
    "scanHistory": [
      {
        "id": "123e4567-e89b-12d3-a456-426614174000",
        "tamperSealId": "SL-SEAL-20261014-9988",
        "scannedByName": "KTV Nguyễn Văn A",
        "scanStage": "INITIAL_SEAL",
        "sealCondition": "INTACT",
        "locationText": "Trung tâm kiểm định Hà Nội",
        "note": "Tem dán nguyên vẹn tại cổng kết nối và ốc đáy",
        "scannedAt": "2026-10-14T08:35:00Z"
      }
    ]
  }
}
```

---

## 9. MỞ RỘNG QUYỀN TRUY CẬP CHO NHÂN VIÊN STAFF TRÊN ADMIN PORTAL
Các màn hình sau trên Web Portal hiện đã hỗ trợ tài khoản mang Role `STAFF` song song với `ADMIN`:
1. **Giám sát Số dư & Doanh thu Hệ thống**:
   - `GET /api/v1/admin/system-wallet/balance`
   - `GET /api/v1/admin/system-wallet/commission-revenue`
   - `GET /api/v1/admin/system-wallet/ledger`
2. **Quản lý & Khóa Tài khoản Người dùng Vi phạm**:
   - `GET /api/v1/admin/users`: Xem danh sách người dùng.
   - `PUT /api/v1/admin/users/{id}/status`: Cập nhật trạng thái người dùng (`ACTIVE`, `SUSPENDED`, `BANNED`).

