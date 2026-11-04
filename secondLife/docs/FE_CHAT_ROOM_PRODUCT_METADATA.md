# Thông tin sản phẩm trong phòng chat

## Nguyên nhân và cách dùng

`GET /api/v1/posts/{postId}` là API marketplace công khai, chỉ cho xem chi tiết `ACTIVE`/`RESERVED`. Bài `SOLD` hoặc `PAYMENT_PENDING` vẫn có lịch sử chat nhưng không còn truy cập qua API này.

FE phải dùng thông tin sản phẩm có sẵn trong response chat để dựng header, sidebar và lịch sử; bỏ các request lấy chi tiết bài đăng công khai chỉ để bổ sung những trường này. Backend không tự sửa được luồng gọi API của FE.

## Các route thực tế

Tất cả yêu cầu Bearer access token. User ID lấy từ token, không truyền ID của người khác để truy cập phòng.

| Method và route | Response thành công |
| --- | --- |
| `GET /api/v1/chats` | `ApiResponse<List<ChatRoomDto>>`, danh sách ở `data` |
| `GET /api/v1/chats/rooms?postId={postId}` | `ApiResponse<ChatRoomDto>`, phòng hiện có của buyer đăng nhập; không tìm thấy giữ response rỗng hiện tại |
| `POST /api/v1/chats/rooms?postId={postId}` | `ApiResponse<ChatRoomDto>`, mở lại phòng của buyer hoặc tạo mới với bài công khai |

GET/POST `/rooms?postId` giữ ngữ nghĩa buyer hiện tại; không suy đoán phòng của seller từ postId vì mỗi bài có thể có nhiều buyer. Seller dùng danh sách `GET /chats` để chọn phòng theo `id`. Đọc/gửi tin nhắn tiếp tục dùng `/{roomId}/messages`.

## Trường dữ liệu

| Field | Ý nghĩa |
| --- | --- |
| `postId` | ID bài đăng liên kết với phòng |
| `postTitle` | Tiêu đề bài đăng hiện tại |
| `postImageUrl` | Ảnh đại diện từ `Post.imageUrl`; có thể null |
| `postPrice` | Giá niêm yết hiện tại từ `Post.price`, BigDecimal/VND; có thể null đối với dữ liệu thiếu |
| `postStatus` | Trạng thái thực tế hiện tại từ `Post.status` |

Các trường `id`, buyer/seller ID, tên/avatar, `lastMessage`, `updatedAt` được giữ nguyên. Hai trường mới là `postPrice` và `postStatus`. Đây là dữ liệu hiện tại của bài liên kết, không phải snapshot lịch sử bất biến tại thời điểm bắt đầu chat.

`postPrice` không phải giá thương lượng hoặc số tiền phải thanh toán. Phòng vẫn đọc được khi bài đã bán không có nghĩa FE được mở lại nút mua: cần dựa vào trạng thái và luồng mua hàng hiện có, BE tiếp tục kiểm tra điều kiện đặt mua.

Nếu ảnh null, dùng placeholder của FE. Nếu giá null, hiển thị chưa có thông tin; không tự gán 0 và không gọi API công khai để bù dữ liệu. Không dùng giá của offer hoặc total đơn hàng thay giá niêm yết.

## Ví dụ response phòng hiện có

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "id": "59ae2a17-bb0c-4883-9982-ecc885f35031",
    "postId": "708ed68e-c1fd-4a25-bba9-1fa68e74c8ce",
    "postTitle": "Sản phẩm đã bán",
    "postImageUrl": "https://example.com/product.jpg",
    "buyerId": "4be9f4d7-c006-4eea-85fd-909d7e614bf9",
    "buyerName": "Người mua",
    "buyerAvatar": null,
    "sellerId": "16eccaba-47a7-4696-948a-23992d05094f",
    "sellerName": "Người bán",
    "sellerAvatar": null,
    "lastMessage": "Cảm ơn bạn",
    "updatedAt": "2026-11-04T03:00:00Z",
    "postPrice": 150000,
    "postStatus": "SOLD"
  },
  "timestamp": "2026-11-04T10:00:00"
}
```

Ví dụ minh họa cấu trúc, không phải dữ liệu đọc từ hai bài đang báo lỗi.

## Phân quyền và thao tác mới

- Danh sách chỉ chứa phòng của buyer/seller đang đăng nhập; không có quyền xem toàn bộ chat dành cho admin trong luồng hiện có và không bổ sung quyền đó.
- Thành viên được mở lại phòng hiện có kể cả bài không công khai.
- Người ngoài không đọc/gửi tin nhắn phòng khác (403).
- GET `/rooms` của người không phải buyer phòng đó không trả thông tin phòng.
- POST tạo phòng mới cho bài không công khai bị chặn 403; tránh lộ thông tin qua việc tự tạo phòng mới. Tạo phòng mới với ACTIVE/RESERVED giữ được hỗ trợ.
- API marketplace công khai và điều kiện đặt mua giữ nguyên.

## Checklist FE

1. Gọi `GET /api/v1/chats`, giữ nguyên DTO nhận được khi chọn phòng.
2. Dùng `room.postTitle`, `room.postImageUrl`, `room.postPrice`, `room.postStatus` trực tiếp.
3. Bỏ vòng lặp gọi `/posts/{postId}` để enrich danh sách/header phòng chat.
4. Cập nhật type FE: thêm `postPrice?: number | null`, `postStatus?: string | null` để tương thích triển khai từng bước.
5. Kiểm tra trạng thái SOLD/PAYMENT_PENDING để hiển thị nhãn và xử lý nút mua theo nghiệp vụ hiện tại. Quyết định mua/giá cuối cùng vẫn do BE xác thực.
6. Giữ API chi tiết công khai cho màn marketplace; trường hợp 404 ở đó không phải lý do xóa lịch sử chat.
7. Test cùng tài khoản tham gia với bài ACTIVE, RESERVED, SOLD, PAYMENT_PENDING; test tài khoản ngoài không thấy phòng/lấy được tin nhắn.
