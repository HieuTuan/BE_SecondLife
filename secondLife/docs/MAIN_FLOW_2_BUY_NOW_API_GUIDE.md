# Main Flow 2 — Mua ngay, thanh toán bằng ví và tracking GHN

Hướng dẫn test trên Swagger: [http://localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html). Các đường dẫn dưới đây đã gồm `/api/v1`.

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

FE dùng API này cho trang sản phẩm. `GET /api/v1/posts/{postId}` là API của chủ bài để đọc draft; `GET /api/v1/orders/buyer` là danh sách đơn đã mua.

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
