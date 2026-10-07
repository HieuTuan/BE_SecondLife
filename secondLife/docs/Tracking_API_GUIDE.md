# Main Flow 2 — Mua ngay, thanh toán bằng ví và tracking GHN

Hướng dẫn FE thực thi API đặt hàng, thanh toán bằng ví và tracking; có JSON để test trên Swagger: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html). Các đường dẫn dưới đây đã gồm `/api/v1`.

Các bước 1–11 mô tả thứ tự gọi API và JSON. Phần **Hướng dẫn nối API vào React** cuối file mô tả quản lý state, đọc response, retry và polling tracking. Nội dung đối chiếu với controller, DTO và service hiện tại; không có API GPS shipper hoặc API chi tiết đơn hàng `GET /api/v1/orders/{orderId}` trong code hiện tại.

## Chuẩn bị

- Có hai tài khoản khác nhau: **BUYER** mua hàng và **SELLER** sở hữu bài đăng.
- Bài đăng đang `ACTIVE`, có giá bán và thông tin cân nặng/kích thước kiện đã lưu trong Main Flow 1.
- Địa chỉ lấy hàng đã được lưu khi SELLER đăng ký shop và xác nhận email. Không cần bước nhập lại địa chỉ lấy hàng khi mua.
- GHN đã bật và cấu hình đúng `GHN_BASE_URL`, `GHN_TOKEN`, `GHN_SHOP_ID`. Token và Shop ID phải thuộc cùng môi trường GHN.
- Hướng dẫn này áp dụng giao trực tiếp **SELLER → BUYER** (`SELLER_TO_BUYER`). Nếu quote trả `CENTER_TO_BUYER`, đó là nhánh hàng qua trung tâm kiểm định, STAFF tạo vận đơn từ trung tâm.

Trong Swagger: chọn API → **Try it out** → nhập tham số/body → **Execute**. Dùng **Authorize** nhập access token theo định dạng Swagger yêu cầu. Khi đổi BUYER sang SELLER hoặc ngược lại, thay token trong Authorize.

**Các UUID, mã vận đơn, địa chỉ và số tiền dưới đây là ví dụ.** JSON đúng cấu trúc nhưng phải thay ID bằng dữ liệu thật từ response; không thể dùng một UUID mẫu để mua bài không tồn tại. GET và các API ghi rõ “không body” không gửi `{}`.

## Thứ tự API cần gọi

| Bước | Token | API | Mục đích |
| --- | --- | --- | --- |
| 1 | BUYER | `GET /api/v1/listings?page=0&size=10` | Chọn bài đang bán, lấy postId |
| 2 | BUYER | `GET /api/v1/listings/{postId}` | Xem chi tiết và giá |
| 3 | BUYER | `GET /api/v1/wallets/me` | Kiểm tra tiền trong ví |
| 3a, nếu thiếu tiền | BUYER | `POST /api/v1/wallets/deposit-request` | Tạo yêu cầu nạp, chuyển khoản theo response |
| 4 | BUYER | `GET /api/v1/shipping/provinces` | Chọn tỉnh/thành nhận hàng |
| 5 | BUYER | `GET /api/v1/shipping/wards?provinceId=...` | Chọn phường/xã thuộc tỉnh đã chọn |
| 6 | BUYER | `POST /api/v1/shipping/quotes` | Lấy phí giao và tổng tiền |
| 7 | BUYER | `POST /api/v1/orders` | Đặt hàng và trừ tiền ví, giữ ký quỹ |
| 8 | SELLER | `POST /api/v1/orders/{orderId}/shipments` | Tạo vận đơn GHN |
| 9 | BUYER/SELLER | `GET /api/v1/orders/{orderId}/shipments` | Xem vận đơn/trạng thái |
| 10 | BUYER/SELLER | `GET /api/v1/shipments/{shipmentId}/events` | Xem lịch sử giao hàng |
| 11 | BUYER | `PUT /api/v1/orders/{orderId}/delivered` | Sau khi GHN báo giao thành công, xác nhận nhận hàng và giải ngân |

Trong luồng thật, GHN cập nhật bước 9–10 qua webhook. Cách giả lập trên localhost nằm ở bước 10 bên dưới.

## 1. BUYER chọn bài đang bán

```http
GET /api/v1/listings?page=0&size=10
```

Không body. Lấy `data.content[].postId` của bài muốn mua; kiểm tra `sellerId` khác tài khoản BUYER. Không gửi `sort`.

Có thể lọc bằng `categoryId` hoặc `itemId` trong tham số Swagger. Nếu danh sách rỗng, cần có bài `ACTIVE` trước khi test.

Ví dụ dùng xuyên suốt: `postId = fd6923c9-62b7-4e30-a7d6-b67e7129f233`. Thay bằng postId đang bán của bạn.

## 2. BUYER xem chi tiết

Nhập postId vào tham số path:

```http
GET /api/v1/listings/fd6923c9-62b7-4e30-a7d6-b67e7129f233
```

Thông tin ở `data`: `postId`, `sellerId`, `title`, `description`, `imageUrls`, `price`, `status`, category/item. Bài không tồn tại hoặc không còn `ACTIVE` trả 404.

FE dùng API này cho trang sản phẩm. `GET /api/v1/posts` hiện cũng trả danh sách marketplace; không dùng đường dẫn `GET /api/v1/posts/{postId}` vì controller hiện tại không có endpoint đó. `GET /api/v1/orders/buyer` là danh sách đơn đã mua.

## 3. BUYER kiểm tra/nạp ví

```http
GET /api/v1/wallets/me
```

Response ví trả trực tiếp: đọc `balance`, không phải `data.balance`.

Nếu thiếu tiền, gọi:

```http
POST /api/v1/wallets/deposit-request
```

```json
{
  "amount": 2000000
}
```

`amount` là số tiền VND cần nạp, tối thiểu 10.000. Response trả trực tiếp các trường `id`, `amount`, `code`, `status`, `bankAccountName`, `bankAccountNumber`, `bankName`.

Chuyển khoản vào tài khoản ngân hàng được trả về, đúng số tiền và nội dung `code`. Tạo yêu cầu nạp **chưa cộng tiền vào ví**. Chờ backend nhận xác nhận thanh toán qua SePay rồi gọi lại `GET /api/v1/wallets/me`. Chỉ đặt hàng khi đủ tổng tiền ở bước 6.

## 4–5. BUYER chọn địa chỉ nhận hàng từ GHN

```http
GET /api/v1/shipping/provinces
```

Lấy `_id` và `name` của tỉnh/thành trong `data[]`. Sau đó nhập `_id` vào `provinceId`:

```http
GET /api/v1/shipping/wards?provinceId=1000001
```

`1000001` chỉ là ID ví dụ; dùng giá trị thực nhận ở API trước. Lấy `name` của phường/xã thuộc tỉnh đã chọn, đang hoạt động (`status = 1`).

**Ở API quote hiện tại**, gửi tên tỉnh/phường trong `deliveryAddress`, không gửi `provinceId`/`wardId`. Hai ID đó dùng chọn danh mục; request đăng ký SELLER là request khác, có dùng ID. Dùng chính xác tên GHN trả về.

## 6. BUYER lấy báo giá giao hàng

```http
POST /api/v1/shipping/quotes
```

```json
{
  "postId": "fd6923c9-62b7-4e30-a7d6-b67e7129f233",
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

| Field | Ý nghĩa |
| --- | --- |
| postId | Bài ACTIVE cần mua |
| deliveryAddress.name | Tên người nhận |
| deliveryAddress.phone | Số điện thoại người nhận, 9–15 chữ số, có thể có dấu + đầu chuỗi |
| deliveryAddress.address | Số nhà, đường, thông tin địa chỉ cụ thể |
| deliveryAddress.provinceName | Tên tỉnh/thành đúng theo GHN |
| deliveryAddress.wardName | Tên phường/xã thuộc tỉnh/thành đã chọn |
| deliveryAddress.newAddress | true: địa chỉ mới hai cấp; không cần nhập quận/huyện |

Mua ngay **không gửi `negotiationId`**. Backend lấy địa chỉ SELLER và thông tin kiện từ dữ liệu đã lưu, gọi GHN tính phí.

Đọc response:

| Trường response | Dùng để |
| --- | --- |
| data.quoteId | Lưu làm shippingQuoteId cho bước 7 |
| data.leg | Kiểm tra SELLER_TO_BUYER cho hướng dẫn này |
| data.productPrice | Tiền hàng |
| data.shippingFee | Phí vận chuyển |
| data.totalPayable | Tổng cần thanh toán bằng ví |
| data.expiresAt | Hạn sử dụng báo giá |
| data.expectedDeliveryTime | Thời gian giao dự kiến, nếu có |

Ví dụ tiền hàng 1.000.000, phí 30.000 thì ví cần ít nhất 1.030.000. Đây là số minh họa; dùng `data.totalPayable` thực. Nếu quote hết hạn hoặc bài/thông tin kiện thay đổi, lấy quote mới trước khi đặt hàng.

## 7. BUYER đặt hàng và thanh toán

```http
POST /api/v1/orders
```

```json
{
  "postId": "fd6923c9-62b7-4e30-a7d6-b67e7129f233",
  "shippingQuoteId": "cab1b4e3-40e5-40a6-a83e-a6437f980b8a",
  "requestId": "fe64a08d-96ab-4c1b-8357-9292745daa97"
}
```

- `postId`: đúng bài ở bước 6.
- `shippingQuoteId`: thay bằng `data.quoteId` vừa nhận; phải thuộc BUYER hiện tại và còn hiệu lực.
- `requestId`: UUID do FE tạo cho một lần đặt hàng. Retry cùng thao tác giữ nguyên UUID và payload; đơn mới dùng UUID mới.

**API này thực hiện thanh toán bằng ví luôn**, không gọi thêm API trừ tiền hoặc API thanh toán GHN. Thành công: trừ tiền hàng + phí giao, đơn `PROCESSING`, ký quỹ `HELD`, bài `SOLD`. Tiền hàng chưa được chuyển cho SELLER. Thiếu tiền thì giao dịch rollback.

Response là **OrderResponseDTO trực tiếp**, không bọc `data`. Lưu `id` làm `orderId`; đọc `status`, `escrowStatus`, `finalPrice`, `shippingFee`, `totalPaid` để kiểm tra kết quả.

Gọi lại `GET /api/v1/wallets/me` bằng BUYER để đối chiếu tiền bị trừ. FE có thể hiển thị “Thanh toán thành công, chờ người bán gửi hàng”.

## 8. SELLER tạo vận đơn GHN

Đổi Authorize sang token **SELLER sở hữu bài đã bán**. Lấy đơn bán nếu cần:

```http
GET /api/v1/orders/seller?page=0&size=10
```

Danh sách đơn trả trực tiếp trong `content[]`; `content[].id` là orderId. Không lấy `data.content` ở API đơn hàng.

Nhập orderId từ bước 7 vào path:

```http
POST /api/v1/orders/{orderId}/shipments
```

```json
{
  "requestId": "ae45bd6b-0bcb-4bac-b1d1-dc33f650db20",
  "leg": "SELLER_TO_BUYER"
}
```

Không cần gửi lại `fromAddress`/`toAddress` cho nhánh này. Backend dùng địa chỉ và kiện đã gắn với quote được thanh toán.

Lưu `data.shipmentId` để tracking và `data.orderCode` là mã vận đơn GHN. Tạo vận đơn thành công **chưa có nghĩa GHN đã lấy hàng**. Retry cùng thao tác giữ nguyên requestId. Nếu `CREATION_UNCERTAIN`, cần đồng bộ/đối soát; không đổi requestId để tạo nhiều vận đơn.

SELLER có thể lấy nhãn để in, không body:

```http
GET /api/v1/shipments/{shipmentId}/label
```

## 9. BUYER/SELLER theo dõi đơn và vận đơn

```http
GET /api/v1/orders/buyer?page=0&size=10
GET /api/v1/orders/seller?page=0&size=10
GET /api/v1/orders/{orderId}/shipments
GET /api/v1/shipments/{shipmentId}/events
```

Chọn BUYER cho danh sách đơn mua, SELLER cho danh sách đơn bán. API shipments/events chỉ cho người liên quan đơn hoặc STAFF/ADMIN.

- Đơn mua/bán: `content[]`; tìm đơn bằng `id`.
- Vận đơn: `data[]`; xem `shipmentId`, `orderCode`, `status`, `providerStatus`, `expectedDeliveryTime`, `deliveredAt`, `lastError`.
- Timeline: `data[]`; đọc `status`, `occurredAt`, `type`, `reason`.

Muốn lấy trạng thái mới trực tiếp từ GHN, không body:

```http
POST /api/v1/shipments/{shipmentId}/sync
```

API sync đọc trạng thái thật ở GHN, không phải nút tự đổi trạng thái. Trong luồng thật, backend còn tự nhận webhook GHN tại `POST /api/v1/shipping/callback`.

| Trạng thái đơn | Ý nghĩa trên FE |
| --- | --- |
| PROCESSING / HELD | Đã thanh toán, chờ lấy hàng |
| SHIPPED / HELD | Đang vận chuyển; xem providerStatus để biết chi tiết |
| DELIVERED / HELD | GHN báo đã giao, chờ BUYER xác nhận |
| COMPLETED / RELEASED | BUYER xác nhận, đã chuyển tiền hàng cho SELLER |

Các sự cố có thể đưa ký quỹ về `FROZEN` để xử lý. Không dùng `PUT /api/v1/orders/{id}/shipped` để đổi tay: code hiện tại chặn thao tác đó.

## 10. Tùy chọn: giả lập trạng thái GHN để test localhost trên Swagger

Dùng khi muốn test luồng SecondLife đến bước xác nhận nhận hàng mà chưa có giao hàng thật. **Giả lập chỉ thay đổi dữ liệu SecondLife, không thay đổi đơn bên GHN.** Cần vận đơn đã được tạo ở bước 8, có orderCode thật trong database.

Trong Swagger chọn:

```http
POST /api/v1/shipping/callback
```

Nhập header `X-GHN-Secret` bằng đúng giá trị `GHN_WEBHOOK_SECRET` trong `.env`. API callback không yêu cầu Bearer nhưng bắt buộc secret đúng, GHN đang bật và ShopID khớp cấu hình.

### 10.1. Giả lập GHN đã lấy hàng

```json
{
  "ShopID": 123456,
  "OrderCode": "MA_VAN_DON_TU_BUOC_8",
  "Type": "switch_status",
  "Status": "picked",
  "Time": "2026-10-05T08:00:00Z"
}
```

Thay `ShopID` bằng `GHN_SHOP_ID`; thay `OrderCode` bằng `data.orderCode` bước 8. **Thay `Time` bằng thời điểm UTC hiện tại khi test**, không dùng nguyên thời gian mẫu. Thời điểm phải mới hơn sự kiện trước và không ở tương lai quá mức cấu hình cho phép.

Gọi lại API đơn mua và vận đơn: đơn chuyển `SHIPPED`, vận đơn có `providerStatus = picked`.

### 10.2. Giả lập GHN đã giao hàng

Gọi cùng API, header giữ nguyên:

```json
{
  "ShopID": 123456,
  "OrderCode": "MA_VAN_DON_TU_BUOC_8",
  "Type": "switch_status",
  "Status": "delivered",
  "Time": "2026-10-05T08:05:00Z"
}
```

Thay ID/mã như trên và `Time` bằng thời điểm hiện tại **mới hơn bước 10.1**. Không dùng lại cùng Time và Type cho hai trạng thái: backend chống xử lý trùng sự kiện.

Kiểm tra: đơn `DELIVERED`, `shippingDeliveredAt` có giá trị; vận đơn `providerStatus = delivered`. Ký quỹ vẫn `HELD`, SELLER chưa được giải ngân.

Nếu đang test bằng callback giả lập, không dùng sync để mong GHN cũng có trạng thái giả lập. Sync vẫn đọc dữ liệu thật của GHN. Với webhook thật, URL trên trang GHN phải có đầy đủ đường dẫn, ví dụ `https://<domain-ngrok>/api/v1/shipping/callback`, header `X-GHN-Secret` giống cấu hình backend.

## 11. BUYER xác nhận nhận hàng và giải ngân

Đổi Authorize về token **BUYER của đơn**, nhập orderId bước 7:

```http
PUT /api/v1/orders/{orderId}/delivered
```

**Không body.** Chỉ thực hiện sau khi backend đã nhận trạng thái giao thành công từ GHN (hoặc callback giả lập ở bước 10).

Kết quả trả trực tiếp: `status = COMPLETED`, `escrowStatus = RELEASED`. SELLER được cộng **tiền hàng**, không nhận phần phí vận chuyển. Có thể đổi token SELLER rồi gọi `GET /api/v1/wallets/me` để đối chiếu. GHN báo delivered không tự giải ngân ngay; BUYER cần bước xác nhận này.

## Test hủy đơn riêng

Dùng một đơn còn `PROCESSING`, ký quỹ `HELD`. Nếu đã tạo vận đơn, SELLER hủy vận đơn trước khi GHN lấy hàng:

```http
POST /api/v1/shipments/{shipmentId}/cancel
```

Sau đó người có quyền trên đơn gọi, không body:

```http
PUT /api/v1/orders/{orderId}/cancel
```

Hủy vận đơn không tự hủy đơn mua. Nếu chưa tạo vận đơn thì có thể gọi hủy đơn trực tiếp. Trường hợp chưa tạo vận đơn hoàn tiền hàng và phí giao; khi đã tạo vận đơn, quy tắc hiện tại chỉ hoàn tiền hàng. Đơn đã lấy hàng/giao hàng cần xử lý sự cố hoặc hoàn hàng, không dùng quy trình hủy này.

## Kiểm tra nhanh khi gặp lỗi

Nếu Swagger hiển thị `legacyCodesValid`, `districtNameValid` hoặc body gồm `array`, `empty`, `nodeType`..., đó là schema tự sinh của hàm validation/JsonNode. Xóa body mẫu và dùng JSON trong hướng dẫn. Callback cần các field `ShopID`, `OrderCode`, `Type`, `Status`, `Time`; ShopID phải khớp cấu hình đang chạy, không dùng mã Client trên trang Developer.

| Hiện tượng | Kiểm tra |
| --- | --- |
| 401 | Access token hết hạn hoặc chưa Authorize |
| 403 | Sai tài khoản/role/chủ đơn; callback sai secret hoặc ShopID |
| listings rỗng / chi tiết 404 | Chưa có bài ACTIVE hoặc bài đã được mua |
| Insufficient balance | Ví BUYER chưa đủ totalPayable; yêu cầu nạp chưa được xác nhận |
| Quote hết hạn/không khớp | Lấy quote mới và dùng đúng postId, token BUYER đã lấy quote |
| GHN request failed / 502 | Kiểm tra log backend, token, Shop ID và GHN_BASE_URL cùng môi trường |
| Shipment leg không khớp | Xem data.leg của quote; CENTER_TO_BUYER phải đi nhánh STAFF |
| Xác nhận nhận hàng bị chặn | Đơn chưa DELIVERED, thiếu shippingDeliveredAt, ký quỹ bị đóng băng hoặc đã giải ngân |
| Callback thành công nhưng trạng thái không đổi | Kiểm tra sự kiện trùng Time/Type hoặc cũ hơn sự kiện đã lưu |

Chuỗi test hoàn chỉnh: **BUYER chọn bài → chọn địa chỉ → lấy quote → đảm bảo ví đủ tiền → đặt hàng/thanh toán → SELLER tạo vận đơn → GHN báo picked/delivered → BUYER xác nhận nhận hàng → SELLER nhận tiền hàng**.

## Nối các API này thì FE hoàn thành được những phần nào?

Các API trên đủ để xây dựng luồng **mua ngay, thanh toán bằng ví, giao trực tiếp SELLER → BUYER và giải ngân sau xác nhận nhận hàng**, khi các dịch vụ GHN/SePay được cấu hình và hoạt động đúng.

| Màn hình/chức năng FE | API cần nối | Kết quả |
| --- | --- | --- |
| Danh sách và chi tiết sản phẩm | GET /api/v1/listings; GET /api/v1/listings/{postId} | BUYER chọn bài ACTIVE, xem ảnh/mô tả/giá để mua |
| Ví và nạp tiền | GET /api/v1/wallets/me; POST /api/v1/wallets/deposit-request | Hiển thị số dư, thông tin chuyển khoản; cập nhật số dư sau khi SePay xác nhận |
| Chọn địa chỉ nhận | GET /api/v1/shipping/provinces; GET /api/v1/shipping/wards | Chọn tỉnh/phường theo dữ liệu GHN |
| Checkout | POST /api/v1/shipping/quotes | Hiển thị tiền hàng, phí giao, tổng thanh toán và hạn quote |
| Mua ngay/thanh toán | POST /api/v1/orders | Tạo đơn, trừ ví, giữ ký quỹ; hiển thị thanh toán thành công |
| Đơn mua và đơn bán | GET /api/v1/orders/buyer; GET /api/v1/orders/seller | Danh sách đơn theo tài khoản, trạng thái mua bán và ký quỹ |
| SELLER gửi hàng | POST /api/v1/orders/{orderId}/shipments; GET /api/v1/shipments/{shipmentId}/label | Tạo vận đơn GHN, lấy mã/nhãn đóng gói |
| Tracking vận chuyển | GET /api/v1/orders/{orderId}/shipments; GET /api/v1/shipments/{shipmentId}/events | Hiển thị trạng thái GHN, timeline, ngày giao dự kiến và thời điểm đã giao |
| Làm mới tracking | POST /api/v1/shipments/{shipmentId}/sync | Lấy trạng thái thực từ GHN; webhook cập nhật backend khi có sự kiện |
| Xác nhận nhận hàng | PUT /api/v1/orders/{orderId}/delivered | BUYER xác nhận, hoàn tất đơn, giải ngân tiền hàng cho SELLER |
| Hủy trước khi gửi nếu đủ điều kiện | POST /api/v1/shipments/{shipmentId}/cancel; PUT /api/v1/orders/{orderId}/cancel | Hủy vận đơn rồi hủy đơn, hoàn tiền theo điều kiện đã nêu |

### Cách FE hiển thị trạng thái vận chuyển

Tracking dùng `providerStatus` của vận đơn để hiển thị chi tiết giống trạng thái GHN. `status` của đơn mua vẫn dùng riêng cho vòng đời thanh toán/ký quỹ.

| Chặng thực tế | providerStatus | Trạng thái đơn mua |
| --- | --- | --- |
| Đã tạo vận đơn, chờ nhận kiện | ready_to_pick | PROCESSING |
| Nhân viên GHN đang lấy hàng | picking | PROCESSING |
| GHN xác nhận đã nhận kiện SELLER | picked | SHIPPED |
| Kiện lưu kho/phân loại | storing / sorting | SHIPPED |
| Đang chuyển giữa các kho/bưu cục | transporting | SHIPPED |
| Nhân viên đang giao cho BUYER | delivering | SHIPPED |
| GHN xác nhận giao thành công | delivered | DELIVERED, ký quỹ HELD |
| BUYER xác nhận trên SecondLife | Vẫn delivered | COMPLETED, ký quỹ RELEASED |

FE không gọi callback để đổi trạng thái trong vận hành thật. Nhân viên GHN xử lý nhận/giao kiện trên hệ thống GHN; GHN gửi webhook về SecondLife. STAFF SecondLife không dùng callback như một API cập nhật trạng thái thủ công. Khi test staging, callback giả lập chỉ chứng minh luồng xử lý của SecondLife; trạng thái ở trang GHN có thể khác.

### Những phần chưa hoàn thành chỉ bằng chuỗi API này

- **GPS shipper trên bản đồ:** các API hiện tại không cung cấp tọa độ shipper trực tiếp. Tracking trạng thái/timeline không phải theo dõi vị trí GPS.
- **Giao nhận thực tế trên staging:** tạo vận đơn kiểm tra tích hợp, không tạo chuyến giao thật. Callback giả lập không cập nhật hệ thống GHN.
- **Chọn mang kiện ra bưu cục hay GHN đến lấy:** request hiện tại chưa có lựa chọn riêng cho hai hình thức. READY_TO_PICK không chứng minh hình thức gửi hàng.
- **Nhánh thương lượng, trung tâm kiểm định, tranh chấp/hoàn hàng:** cần quy trình và các API riêng; hướng dẫn này tập trung mua ngay và giao trực tiếp.
- **Nạp tiền tự thành công khi bấm tạo yêu cầu:** phải có giao dịch ngân hàng được SePay xác nhận; FE tạo yêu cầu nạp không tự cộng số dư.

Nếu nền tảng dùng chung GHN_SHOP_ID, các vận đơn tập trung trong shop GHN của nền tảng. Trang quản lý shop GHN là giao diện người gửi/chủ shop, không phải giao diện nghiệp vụ nhận/giao kiện của nhân viên GHN.

## Hướng dẫn nối API vào React

### A. Quy ước request và response

- Base URL lấy từ cấu hình môi trường FE. Ví dụ môi trường local dùng `http://localhost:8080`. Không đưa token GHN hoặc `GHN_WEBHOOK_SECRET` vào FE.
- Các API nghiệp vụ dùng `Authorization: Bearer <accessToken>` của tài khoản đang đăng nhập. Request có body JSON thêm `Content-Type: application/json`.
- GET, sync, cancel, label, xác nhận nhận hàng không có body, không gửi JSON mẫu Swagger.
- Kiểm tra HTTP status trước khi đọc kết quả thành công. Lỗi nghiệp vụ thường có `message`, `errors`, `path`; hiển thị `message` từ backend.
- Không dùng một quy tắc `response.data` cho mọi API: response đơn hàng và ví khác response shipping/listings.

| API | Nếu dùng fetch: giá trị từ `await response.json()` | Nếu dùng Axios: giá trị trong HTTP `response.data` |
| --- | --- | --- |
| GET listings | `body.data.content` | `response.data.data.content` |
| GET listing detail | `body.data` | `response.data.data` |
| GET wallet | `body.balance` | `response.data.balance` |
| POST deposit-request | `body.id`, `body.code` | `response.data.id`, `response.data.code` |
| POST orders / PUT delivered / PUT order cancel | `body.id`, `body.status` | `response.data.id`, `response.data.status` |
| GET orders/buyer hoặc orders/seller | `body.content` | `response.data.content` |
| POST quotes | `body.data.quoteId` | `response.data.data.quoteId` |
| POST create shipment / sync / shipment cancel | `body.data` là một vận đơn | `response.data.data` |
| GET order shipments | `body.data` là mảng vận đơn | `response.data.data` |
| GET shipment events | `body.data` là mảng sự kiện | `response.data.data` |
| GET label | `body.data` là kết quả GHN | `response.data.data` |

Ví dụ helper fetch dùng chung, không tự bóc `data` để tránh sai kiểu response:

```ts
export async function requestJson<T>(
  baseUrl: string,
  token: string,
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const headers = new Headers(options.headers);
  headers.set('Authorization', `Bearer ${token}`);
  if (options.body != null) headers.set('Content-Type', 'application/json');

  const response = await fetch(`${baseUrl.replace(/\/$/, '')}${path}`, {
    ...options,
    headers,
  });
  const body = await response.json();
  if (!response.ok || body?.success === false) {
    throw new Error(body?.message ?? `HTTP ${response.status}`);
  }
  return body as T;
}
```

`baseUrl` và `token` do ứng dụng truyền vào. Với lỗi mạng, fetch sẽ throw; FE hiển thị thông báo và cho retry đúng thao tác, không tự coi đó là đặt hàng thất bại chắc chắn.

### B. Màn hình BUYER: chọn sản phẩm → checkout → thanh toán

1. Khi mở danh sách, gọi GET listings. Dùng `postId` làm key React và ID sản phẩm; không dùng `id` hoặc `Date.now()` thay cho `postId` trong response marketplace.
2. Khi chọn sản phẩm, gọi GET listing detail. Giữ `postId` và `sellerId`. Không hiển thị nút mua bài của chính tài khoản hiện tại.
3. Mở checkout: đọc ví, tải danh mục tỉnh/phường GHN và nhận địa chỉ người mua. Khi đổi tỉnh phải xóa lựa chọn phường cũ.
4. Khi địa chỉ hợp lệ, gọi POST quotes với JSON bước 6. Giữ nguyên `quoteId`, `leg`, `productPrice`, `shippingFee`, `totalPayable`, `expiresAt` trong state checkout.
5. Hiển thị tiền hàng, phí giao, tổng tiền và hạn báo giá. Khi sửa địa chỉ, đổi sản phẩm hoặc đổi thông tin thương lượng, xóa quote đang giữ và lấy lại quote.
6. Nếu ví thiếu tiền, chuyển sang nạp ví. Chờ ví thực sự tăng sau xác nhận thanh toán rồi mới cho đặt hàng.
7. Khi bấm thanh toán, tạo `requestId` một lần bằng `crypto.randomUUID()`, giữ cùng payload cho các lần retry. Disable nút trong lúc request đang chạy.
8. Gọi POST orders với JSON bước 7. Thành công lấy `body.id` làm `orderId`, hiển thị thanh toán thành công; tải lại số dư ví và danh sách đơn mua.

Không gửi `finalPrice`, `shippingFee`, `totalPaid`, `buyerId` hay `sellerId` trong body đặt hàng. Backend tính giá và xác định chủ thể từ dữ liệu/token. Phí vận chuyển được trả cùng tiền hàng qua ví; không thu thêm COD tiền hàng từ BUYER.

Ví dụ tạo payload sau khi đã lấy quote:

```ts
// Tạo một lần cho thao tác đặt hàng. Giữ payload khi retry sau lỗi mạng.
const pendingOrder = {
  postId: selectedPostId,
  shippingQuoteId: quote.quoteId,
  requestId: crypto.randomUUID(),
};

const order = await requestJson<{ id: string; status: string; escrowStatus: string }>(
  apiBaseUrl,
  buyerToken,
  '/api/v1/orders',
  { method: 'POST', body: JSON.stringify(pendingOrder) },
);

// order.id là orderId; response không có lớp data.
// Nếu mất response, retry pendingOrder với cùng requestId và cùng payload.
```

Nếu cần giữ thao tác qua reload, FE có thể lưu payload chờ xử lý theo tài khoản trong sessionStorage và xóa sau khi đã xác định kết quả. Không tạo UUID mới chỉ vì request timeout; backend có thể đã thanh toán thành công. Khi backend xác nhận quote hết hạn/chưa tạo đơn, lấy quote mới và tạo thao tác mới.

Nếu dùng giá thương lượng, quote và order đều phải có cùng `negotiationId` của thương lượng đã `ACCEPTED`, chưa hết hạn và thuộc đúng BUYER/post. Mua ngay bỏ field này ở cả hai request.

### C. Màn hình SELLER: đơn bán → tạo vận đơn → gửi kiện

1. Gọi `GET /api/v1/orders/seller?page=0&size=10`, đọc `body.content`. `id` của đơn bán là orderId đã tạo ở phía BUYER; SELLER không phải tự nhập mã này.
2. Chọn đơn `PROCESSING`, ký quỹ `HELD`; gọi GET order shipments để xem đã có vận đơn chưa.
3. Nếu chưa có vận đơn, gọi POST create shipment với JSON bước 8. `requestId` của vận đơn là UUID riêng, giữ nguyên khi retry; không tái dùng UUID của thao tác đặt hàng.
4. Lưu `shipmentId`, `orderCode`, `providerStatus`. Khi tạo vận đơn chưa chắc chắn (`CREATION_UNCERTAIN`), hiển thị chờ xác minh và retry cùng requestId/đối soát; không cho tạo lại bằng UUID khác.
5. Gọi GET label để lấy kết quả nhãn GHN. Hiển thị/in theo nội dung provider trả về; API không cam kết trả file PDF trực tiếp.
6. SELLER đóng gói và giao kiện cho GHN. FE không tự đổi đơn thành SHIPPED. GHN nhận kiện và cập nhật trạng thái trên hệ thống của họ, backend nhận webhook hoặc sync trạng thái.

Địa chỉ lấy hàng đã có từ onboarding; không bắt SELLER nhập lại trong checkout. Nếu quote có `leg = CENTER_TO_BUYER`, chuyển sang nhánh STAFF/ADMIN tạo vận đơn từ trung tâm bằng leg đó; không ép gửi `SELLER_TO_BUYER`. Các bước vận chuyển tới trung tâm kiểm định là quy trình riêng, không bỏ qua kiểm định bằng cách sửa leg trên FE.

### D. Màn hình tracking của BUYER và SELLER

Màn hình nhận `orderId` từ kết quả đặt hàng hoặc từ đơn đã chọn trong danh sách đơn mua/đơn bán.

1. Gọi `GET /api/v1/orders/{orderId}/shipments` để lấy `data[]`.
2. Mảng rỗng: hiển thị “Chờ tạo vận đơn”, chưa có shipmentId để gọi timeline.
3. Với từng vận đơn, gọi `GET /api/v1/shipments/{shipmentId}/events`. Có thể hiển thị riêng các chặng/đơn hoàn; không giả định luôn chỉ có một shipment và không chọn một vận đơn đã hủy làm vận đơn đang giao.
4. Hiển thị `orderCode`, `providerStatus`, `expectedDeliveryTime`, `deliveredAt`, `podUrl` nếu có. Giá trị null hiển thị “Chưa có thông tin”. Ngày giao dự kiến có thể thay đổi, không dùng nó để suy ra đã giao.
5. Timeline dùng `occurredAt` và `status`/`type`/`reason`. Backend trả thứ tự thời gian tăng dần. Payload sự kiện dùng cho dữ liệu kỹ thuật, không cần hiển thị nguyên JSON cho người mua.
6. Nút “Đồng bộ GHN” gọi POST sync không body, sau đó tải lại vận đơn, timeline và đơn mua/bán. Không gọi sync liên tục trong polling: nút này gọi provider GHN thật.

Webhook đi **GHN → backend**, không đi GHN → React. FE đọc dữ liệu mới từ backend. Code hiện tại chưa có endpoint SSE/WebSocket tracking để FE đăng ký realtime, nên có thể polling trong lúc màn hình tracking đang mở.

**Gợi ý polling:** chọn chu kỳ trong cấu hình FE, ví dụ 15–30 giây. Mỗi lần chỉ gọi API đọc backend, không chồng request; dừng khi rời màn hình. Nếu cần cập nhật trạng thái đơn mua, tải lại danh sách đơn tương ứng. Hiện chưa có GET order detail theo ID; tìm đúng `id` trong danh sách có phân trang, không mặc định đơn luôn nằm ở trang đầu.

Ví dụ polling TypeScript dùng helper phía trên. Caller truyền interval từ cấu hình FE và dùng hàm trả về để cleanup effect:

```ts
type Shipment = {
  shipmentId: string;
  orderCode: string | null;
  leg: string;
  status: string;
  providerStatus: string | null;
  expectedDeliveryTime: string | null;
  deliveredAt: string | null;
  podUrl: string | null;
};
type ShippingEvent = {
  id: string;
  shipmentId: string;
  type: string;
  status: string | null;
  occurredAt: string;
  reason: string | null;
};
type Envelope<T> = { success: boolean; data: T };

function startTracking(
  baseUrl: string,
  token: string,
  orderId: string,
  intervalMs: number,
  onUpdate: (shipments: Shipment[], events: Record<string, ShippingEvent[]>) => void,
  onError: (error: unknown) => void,
): () => void {
  if (!Number.isFinite(intervalMs) || intervalMs <= 0) {
    throw new Error('Tracking interval must be configured and positive');
  }
  const abort = new AbortController();
  let timer: ReturnType<typeof setTimeout> | undefined;

  async function poll() {
    try {
      const result = await requestJson<Envelope<Shipment[]>>(
        baseUrl, token, `/api/v1/orders/${orderId}/shipments`,
        { signal: abort.signal },
      );
      const shipments = result.data;
      const pairs = await Promise.all(shipments.map(async (shipment) => {
        const timeline = await requestJson<Envelope<ShippingEvent[]>>(
          baseUrl, token, `/api/v1/shipments/${shipment.shipmentId}/events`,
          { signal: abort.signal },
        );
        return [shipment.shipmentId, timeline.data] as const;
      }));
      if (!abort.signal.aborted) onUpdate(shipments, Object.fromEntries(pairs));
    } catch (error) {
      if (!abort.signal.aborted) onError(error);
    } finally {
      if (!abort.signal.aborted) timer = setTimeout(poll, intervalMs);
    }
  }

  void poll();
  return () => {
    abort.abort();
    if (timer !== undefined) clearTimeout(timer);
  };
}
```

Trong React, effect phụ thuộc `orderId`, token, base URL và interval; callbacks phải ổn định (useCallback hoặc setter state). Không đưa state `shipments`, `events`, `orders` hoặc `listings` vừa cập nhật vào dependencies của effect tải chính chúng. Nếu dùng callback phụ thuộc listings để map orders, tách effect tải listings khỏi effect tải orders, tránh vòng lặp liên tục gọi API như lỗi đã sửa trong `App.tsx`.

Nút đồng bộ gọi:

```ts
await requestJson<Envelope<Shipment>>(
  apiBaseUrl,
  accessToken,
  `/api/v1/shipments/${shipmentId}/sync`,
  { method: 'POST' },
);
// Sau đó tải lại shipments, events và danh sách đơn của tài khoản.
```

Tracking hiển thị chi tiết bằng `providerStatus`. Không suy luận trạng thái đơn mua bằng cách viết hoa providerStatus: đó là hai vòng đời khác nhau. Sự cố giao hàng/hoàn hàng có thể đóng băng ký quỹ và cần STAFF xử lý.

### E. Nút BUYER xác nhận nhận hàng

Chỉ bật nút khi đơn thuộc BUYER hiện tại, `status = DELIVERED`, `escrowStatus = HELD` và `shippingDeliveredAt` có giá trị. Vận đơn delivered là tín hiệu để tải lại đơn; backend vẫn là nơi kiểm tra điều kiện giải ngân.

Gọi `PUT /api/v1/orders/{orderId}/delivered`, không body. Thành công đọc trực tiếp `status = COMPLETED`, `escrowStatus = RELEASED`. Tải lại danh sách đơn và ví; SELLER được cộng `finalPrice`. API này không phải API đánh dấu GHN đã giao và không thể dùng để bỏ qua bước vận chuyển.

### F. Các tình huống FE phải xử lý

| Tình huống | Hành vi FE |
| --- | --- |
| Bài đã được người khác mua | Dừng checkout, tải lại danh sách; không tiếp tục dùng quote cũ |
| Đổi địa chỉ hoặc quote hết hạn | Xóa báo giá cũ, lấy quote mới trước khi tạo thao tác mua mới |
| Timeout khi đặt đơn/tạo vận đơn | Giữ UUID/payload, retry cùng thao tác; không tạo đơn/vận đơn mới tùy tiện |
| Chưa có vận đơn | Hiển thị chờ người gửi tạo vận đơn; không gọi events với ID rỗng |
| Webhook cập nhật rồi nhưng UI chưa đổi | Poll API đọc hoặc dùng nút tải lại; không gọi callback từ FE |
| GHN staging vẫn chờ lấy sau callback test | Bình thường: callback giả lập chỉ đổi SecondLife; sync đọc trạng thái thực GHN |
| Đồng bộ GHN lỗi 502 | Giữ dữ liệu tracking gần nhất, hiển thị lỗi; không chuyển sang trạng thái giao thành công |
| Ký quỹ FROZEN | Không bật nút nhận hàng/giải ngân; hiển thị cần xử lý sự cố |
| Shipment CANCELLED nhưng đơn vẫn PROCESSING | Hủy vận đơn và hủy đơn mua là hai thao tác riêng |
| 401/403 | Xử lý phiên đăng nhập hoặc quyền tài khoản; không retry vô hạn |

API import vận đơn cũ, return-to-sender, refund và shipment của inspection là các API xử lý riêng cho STAFF/ADMIN/kiểm định theo điều kiện backend; không đưa chúng vào chuỗi BUYER mua ngay thông thường. Callback giả lập ở mục 10 chỉ dùng test tích hợp có kiểm soát; không đưa secret/nút đổi trạng thái đó vào UI người dùng.

### G. Checklist nghiệm thu FE

- Hiển thị sản phẩm từ response thật, key và đường dẫn mua dùng `postId`.
- Chọn địa chỉ đúng danh mục GHN; checkout có phí giao, tổng tiền và hạn quote.
- BUYER đặt hàng thành công: ví giảm `totalPaid`, đơn PROCESSING/HELD, bài không còn mua được.
- Retry cùng requestId không tạo thêm đơn và không trừ thêm tiền.
- SELLER nhìn thấy đơn trong danh sách bán và tạo được vận đơn/nhãn.
- Tracking cập nhật trạng thái và timeline từ backend; rời màn hình dừng polling, không gọi API vô hạn theo render.
- GHN delivered → đơn DELIVERED/HELD; BUYER xác nhận → COMPLETED/RELEASED.
- Hủy trước khi nhận kiện tuân thủ mục test hủy; không tự giải ngân hoặc đổi trạng thái trên FE.
- FE hoàn thành được đặt hàng, thanh toán ví, vận đơn và tracking trạng thái/timeline. Bản đồ vị trí GPS shipper và thao tác nghiệp vụ nhận kiện của nhân viên GHN không được cung cấp bởi chuỗi API hiện tại.
