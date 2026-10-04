# Main Flow 2 — Mua ngay, thanh toán bằng ví và tracking GHN

Base URL: `http://localhost:8080/api/v1`. Swagger: `http://localhost:8080/swagger-ui/index.html`.
Authorize bằng Bearer token của BUYER hoặc SELLER tương ứng. JSON gửi với `Content-Type: application/json`. Thay các giá trị `<...>` bằng ID thực; GET và các thao tác không ghi JSON bên dưới không cần body.

## 1. Điều kiện và chọn sản phẩm

Bài đăng phải ACTIVE, người mua không phải chủ bài. GHN phải được cấu hình đúng token, Shop ID và môi trường.

BUYER dùng token đăng nhập để xem sản phẩm:

```http
GET /listings?page=0&size=10
GET /listings?categoryId=<UUID category>&itemId=<UUID item>&page=0&size=10
GET /listings/{postId}
```

Danh sách trả trong `data.content`, thông tin phân trang nằm trong `data`. Chi tiết trả trong `data`. Dùng `postId` của sản phẩm để lấy báo giá và đặt hàng; response có sellerId, categoryId, itemId, title, description, itemCondition, imageUrl, imageUrls, price, status, publishedAt. Chỉ trả bài ACTIVE; chi tiết bài không tồn tại hoặc không ACTIVE trả 404. Bài có thể đã được người khác mua sau khi FE đọc, nên vẫn phải xử lý lỗi khi checkout.

`GET /posts/{postId}` vẫn là API đọc draft của chủ bài, có quyền LISTING_CREATE_SELF; không dùng làm API chi tiết dành cho BUYER.

`GET /orders/buyer` là danh sách đơn hàng người dùng đã mua, không phải danh sách sản phẩm đang bán.

## 2. SELLER chuẩn bị vận chuyển

Lấy danh mục địa chỉ:

```http
GET /shipping/provinces
GET /shipping/wards?provinceId=<ID tỉnh từ response>
```

Lưu địa chỉ lấy hàng, có thể dùng lại cho nhiều bài:

```http
PUT /shipping/pickup-address
```

```json
{
  "name": "Nguyễn Văn A",
  "phone": "0901234567",
  "address": "123 Nguyễn Huệ",
  "provinceName": "Hồ Chí Minh",
  "wardName": "Phường Sài Gòn",
  "newAddress": true
}
```

Tên tỉnh/phường phải đúng danh mục GHN trả về. newAddress=true dùng địa chỉ hai cấp, không cần districtId, districtName, wardCode.

```http
PUT /posts/{postId}/shipping-package
```

```json
{
  "weight": 5000,
  "length": 50,
  "width": 40,
  "height": 35
}
```

weight là gram; length/width/height là cm, đo sau đóng gói. Hiện tích hợp dùng GHN đến địa chỉ người bán lấy hàng, chưa có lựa chọn gửi tại bưu cục trên API SecondLife.

## 3. BUYER kiểm tra và nạp ví nếu cần

```http
GET /wallets/me
```

Nếu thiếu tiền, tạo yêu cầu nạp:

```http
POST /wallets/deposit-request
```

```json
{
  "amount": 2000000
}
```

Làm theo thông tin thanh toán trả về. Tạo yêu cầu nạp chưa tăng số dư; chờ giao dịch nạp được xác nhận rồi kiểm tra lại GET /wallets/me.

## 4. BUYER lấy báo giá vận chuyển

```http
POST /shipping/quotes
```

```json
{
  "postId": "<UUID bài ACTIVE>",
  "deliveryAddress": {
    "name": "Trần Văn B",
    "phone": "0912345678",
    "address": "456 Nguyễn Huệ",
    "provinceName": "Hồ Chí Minh",
    "wardName": "Phường Sài Gòn",
    "newAddress": true
  }
}
```

Mua ngay không gửi negotiationId. Lấy data.quoteId; hiển thị data.productPrice, data.shippingFee, data.totalPayable, data.expiresAt. Hết hạn báo giá phải gọi lại. BUYER cần đủ totalPayable trong ví.

## 5. BUYER đặt hàng và thanh toán thành công

```http
POST /orders
```

```json
{
  "postId": "<UUID bài ở bước 4>",
  "shippingQuoteId": "<data.quoteId ở bước 4>",
  "requestId": "fe64a08d-96ab-4c1b-8357-9292745daa97"
}
```

requestId do FE tạo cho mỗi thao tác đặt hàng mới. Retry cùng thao tác giữ nguyên UUID và payload.

API thành công là thanh toán bằng ví thành công: trừ giá sản phẩm + phí vận chuyển, đơn PROCESSING, ký quỹ HELD, bài SOLD. Không cần gọi API trừ tiền riêng. Tiền hàng chưa chuyển cho SELLER. Nếu thiếu tiền trả Insufficient balance và rollback.

Response tạo đơn là OrderResponseDTO trực tiếp: lấy `id` làm orderId, không lấy data.id. Có thể đối chiếu số dư bằng GET /wallets/me.

## 6. SELLER tạo vận đơn

```http
POST /orders/{orderId}/shipments
```

```json
{
  "requestId": "ae45bd6b-0bcb-4bac-b1d1-dc33f650db20",
  "leg": "SELLER_TO_BUYER"
}
```

Lấy data.shipmentId và data.orderCode. orderCode là mã vận đơn GHN. Retry giữ nguyên requestId; nếu trạng thái CREATION_UNCERTAIN cần đồng bộ/đối soát, không tạo UUID mới để thử liên tục. Tạo vận đơn chưa có nghĩa GHN đã lấy hàng.

## 7. FE tracking

| API | Mục đích |
| --- | --- |
| GET /orders/buyer | Đơn mua của người đang đăng nhập; dùng page, size nếu cần phân trang |
| GET /orders/seller | Đơn bán của người đang đăng nhập |
| GET /orders/{orderId}/shipments | Vận đơn, mã GHN, trạng thái và thời gian giao dự kiến |
| GET /shipments/{shipmentId}/events | Lịch sử sự kiện cho timeline |
| POST /shipments/{shipmentId}/sync | Lấy trạng thái mới trực tiếp từ GHN; không cần body |
| GET /shipments/{shipmentId}/label | SELLER lấy nhãn vận đơn để in |

GHN gọi POST /shipping/callback vào URL public đã cấu hình, kèm X-GHN-Secret khớp GHN_WEBHOOK_SECRET. FE không gọi callback trong luồng thật. Giả lập callback chỉ thay đổi dữ liệu SecondLife, không đổi trạng thái bên GHN.

| Trạng thái đơn SecondLife | UI |
| --- | --- |
| PROCESSING | Đã thanh toán, chờ lấy hàng |
| SHIPPED | Đã lấy hàng / đang vận chuyển / đang giao |
| DELIVERED | GHN giao thành công, chờ BUYER xác nhận |
| COMPLETED | BUYER xác nhận, đã giải ngân |

Dùng providerStatus của vận đơn và events để hiển thị chi tiết. Các sự cố vận chuyển có thể đóng băng ký quỹ để xử lý, không tự coi là giao thành công. API PUT /orders/{id}/shipped hiện chặn cập nhật thủ công.

## 8. BUYER xác nhận nhận hàng

Chỉ thực hiện sau khi GHN đã xác nhận giao thành công:

```http
PUT /orders/{orderId}/delivered
```

Không body. Thành công: đơn COMPLETED, ký quỹ RELEASED; SELLER nhận tiền hàng, không nhận phần phí vận chuyển. GHN báo delivered chưa tự giải ngân ngay.

## 9. Hủy nếu còn đủ điều kiện

Nếu đã có vận đơn, hủy vận đơn trước khi GHN lấy hàng bằng POST /shipments/{shipmentId}/cancel, sau đó PUT /orders/{orderId}/cancel. Không body. Hủy vận đơn không đồng nghĩa tự hủy đơn mua. Các trường hợp đã lấy hàng cần luồng xử lý sự cố/hoàn hàng riêng.

## Thứ tự test tối thiểu

SELLER lưu địa chỉ và kiện hàng → BUYER kiểm tra ví → BUYER lấy quote → BUYER POST /orders (thanh toán thành công) → SELLER tạo shipment → GHN cập nhật qua webhook hoặc sync → BUYER xem tracking → BUYER xác nhận nhận hàng.
