# Seller: xem chi tiết bài đăng của chính mình

## API

```http
GET /api/v1/posts/my-posts/{postId}
Authorization: Bearer <SELLER_ACCESS_TOKEN>
Accept: application/json
```

- `postId` là `id` của bài đăng trong `GET /api/v1/posts/my-posts`, không phải ID đơn hàng.
- Không có request body. Không truyền `sellerId` hoặc `userId`; BE lấy người dùng từ token.
- Yêu cầu quyền `PROFILE_READ_SELF`, giống API danh sách bài đăng cá nhân hiện có.
- BE kiểm tra chủ sở hữu: token phải thuộc người tạo bài đăng.
- API chỉ đọc, không đăng lại bài, không trừ lượt đăng và không gọi AI/hãng vận chuyển.
- Không giới hạn `ACTIVE`: chủ bài vẫn xem được `RESERVED`, `PAYMENT_PENDING`, `SOLD`, cũng như nháp/chờ duyệt/bị từ chối theo luồng danh sách cá nhân hiện có.
- API công khai `GET /api/v1/posts/{postId}` giữ nguyên hành vi; FE seller nên dùng API cá nhân cho nút "Xem chi tiết".

## Response 200

Trả trực tiếp `PostDto`, cùng quy ước với các API cá nhân trong `PostController`. **Không có lớp `data` hoặc `success` ở response thành công.** Ví dụ minh họa:

```json
{
  "id": "59c59571-425b-4af6-96a6-a33645ff56c6",
  "categoryId": "19af3b80-0a47-4488-a904-9fb8c5147ee5",
  "itemId": null,
  "title": "Giá phơi đồ",
  "description": "Mô tả tình trạng và thông tin sản phẩm.",
  "imageUrls": ["https://example.com/front.jpg", "https://example.com/back.jpg"],
  "status": "ACTIVE",
  "createdAt": "2026-11-02T10:00:00Z",
  "updatedAt": "2026-11-02T12:00:00Z",
  "price": 150000,
  "itemCondition": "GOOD",
  "aiDescription": null,
  "aiSuggestedPrice": null,
  "rejectionReason": null,
  "listingCreditCharged": true,
  "shippingWeight": 2000,
  "shippingLength": 160,
  "shippingWidth": 43,
  "shippingHeight": 18,
  "user": {
    "id": "22d069c0-ed50-4ec9-8b49-1ca8ca681f28",
    "email": "seller@example.com",
    "fullName": "Người bán",
    "avatarUrl": null,
    "address": "Địa chỉ hồ sơ của người bán"
  }
}
```

- `shippingWeight`: gram; chia 1000 khi hiển thị kg.
- `shippingLength`, `shippingWidth`, `shippingHeight`: centimet, là số đo kiện đã đóng gói.
- Dùng đúng dữ liệu BE; nếu thiếu số đo, hiển thị chưa cập nhật, không suy đoán theo danh mục hoặc gán kích thước mặc định.
- `imageUrls`: danh sách ảnh theo thứ tự đã lưu.
- `price`: giá bài đăng, không phải giá đã thương lượng trên đơn hàng.
- `rejectionReason`: lý do duyệt nếu có. Chỉ hiển thị khi phù hợp trạng thái.
- `user.address`: địa chỉ hồ sơ, không mặc định là địa chỉ kho lấy hàng. Kho lấy hàng thuộc API shipping riêng.

## Lỗi

| HTTP | Trường hợp | FE xử lý |
| --- | --- | --- |
| 400 | `postId` không phải UUID hợp lệ | Kiểm tra ID gửi lên |
| 401 | Chưa đăng nhập/token không hợp lệ | Yêu cầu đăng nhập lại |
| 403 | Thiếu `PROFILE_READ_SELF`, hoặc bài thuộc người khác | Báo không có quyền xem |
| 404 | Không tồn tại bài đăng | Báo bài đăng không tồn tại và tải lại danh sách |

Lỗi nghiệp vụ dùng cấu trúc lỗi hiện có của BE (`success`, `message`, `path`, `errors`, `timestamp`). Không yêu cầu FE suy luận quyền chỉ từ role hoặc tên seller.

## Test trên Swagger

1. Khởi động lại BE sau khi cập nhật code.
2. Đăng nhập seller, copy access token vào **Authorize**.
3. Gọi `GET /api/v1/posts/my-posts`; copy `items[].id` từ bài của tài khoản đó.
4. Gọi `GET /api/v1/posts/my-posts/{postId}`, không nhập request body: mong đợi 200 và đúng ảnh, giá, số đo đã lưu.
5. Thử ID một bài `SOLD` của cùng seller: mong đợi 200.
6. Đăng nhập seller khác và dùng lại ID trên: mong đợi 403, không trả dữ liệu bài đăng.
7. Dùng UUID không tồn tại: mong đợi 404. Dùng chuỗi không phải UUID: mong đợi 400.
8. Xóa token và gọi lại: mong đợi 401.

Đây là API chi tiết **bài đăng**, không phải chi tiết đơn mua hay lịch sử giao dịch.
