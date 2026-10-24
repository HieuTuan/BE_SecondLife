# FE — Tự chọn xe, tính phí vận chuyển, mua hàng và tracking Lalamove

Đối chiếu luồng tự chọn xe BE; cập nhật catalogue chuẩn hóa ngày 24/10/2026. Dùng tài liệu này cho phần vận chuyển tự động; nó thay thế các hướng dẫn cũ yêu cầu BUYER truyền serviceType hoặc dùng xe mặc định.

FE triển khai mục ẩn/hiện **“Xem thông tin vận chuyển”**, hiển thị giới hạn từng xe và đánh dấu xe phù hợp tại form đăng bài theo [hướng dẫn catalogue xe chi tiết](FE_SHIPPING_VEHICLE_CATALOGUE_GUIDE.md). API mới là `GET /api/v1/shipping/vehicles`, hỗ trợ lọc theo tọa độ lấy hàng; `/cities` và `/provinces` vẫn giữ cấu trúc catalogue thô. Phần giao diện mới do FE triển khai, chưa nằm trong thay đổi BE này.

## 1. Kết quả FE cần làm

BUYER chỉ chọn địa chỉ nhận, xem phí và thanh toán. BE lấy thông số kiện hàng SELLER đã lưu, lọc phương tiện Lalamove theo thành phố, tải trọng và kích thước, ưu tiên xe có tải trọng nhỏ nhất đủ chở kiện hàng và dùng báo giá đầu tiên thành công.

SELLER nhận đơn đã thanh toán → xác nhận đơn → chuẩn bị hàng → gọi tài xế. BUYER/SELLER theo dõi cùng vận đơn. Người test đổi trạng thái trên Sandbox; Production do tài xế Lalamove thao tác.

```text
SELLER lưu địa chỉ lấy hàng + thông số kiện hàng
→ BUYER xem bài → chọn điểm nhận trên bản đồ
→ BE xác định địa chỉ → BE tự chọn xe và báo phí
→ BUYER kiểm tra tổng tiền → thanh toán
→ SELLER xác nhận đơn → đóng gói → gọi tài xế
→ Lalamove tìm tài xế → nhận chuyến → lấy hàng → giao thành công
→ Webhook cập nhật BE → FE đọc trạng thái/tracking
→ BUYER xác nhận nhận hàng → BE giải ngân theo chính sách
```

Không làm dropdown bắt BUYER chọn xe. Có thể hiển thị loại xe BE đã chọn như thông tin chỉ đọc; BUYER không sửa nó. Phí Lalamove trả về là nguồn tính phí, FE không tự nhân km hoặc tự đặt bảng giá.

## 2. Quy ước request, response và ID

Base URL local: `http://localhost:8080`. FE cấu hình base URL theo môi trường, không hardcode trong component. Authentication giữ `/api/auth`; các endpoint nghiệp vụ dưới đây dùng `/api/v1`.

Request JSON cần `Content-Type: application/json`; API đăng nhập cần `Authorization: Bearer <accessToken>`. Không đưa API Key/Secret Lalamove của BE vào FE.

| ID | Lấy từ đâu | Dùng ở đâu |
|---|---|---|
| postId | Bài đăng trả về từ GET /posts | Báo giá và tạo đơn |
| sellerId | Chi tiết bài đăng | Hiển thị người bán |
| quoteId | data.quoteId của POST /shipping/quotes | Gửi thành shippingQuoteId khi checkout |
| providerQuotationId | Báo giá Lalamove, BE trả để tham khảo | Không thay thế quoteId của SecondLife |
| orderId | id của response POST /orders | Xác nhận đơn, tạo vận đơn, nhận hàng |
| shipmentId | data.shipmentId của POST /orders/{orderId}/shipments | Tracking, lịch sử và đồng bộ |
| orderCode | Vận đơn Lalamove trả về | Hiển thị/tìm trong Partner Portal; giữ kiểu string |
| requestId | FE sinh UUID cho một thao tác tạo mới | Giữ nguyên khi retry chính thao tác đó |

### Khác biệt response cần xử lý đúng

- API shipping, shipment, confirmation: `{success, message, data, timestamp}`; đọc `response.data`.
- POST /orders và PUT /orders/{id}/delivered trả OrderResponseDTO trực tiếp; lấy `response.id`, không phải `response.data.id`.
- GET /orders/buyer và /orders/seller trả Spring Page trực tiếp; danh sách ở `content`.
- GET /posts trả ApiResponse bọc Spring Page; danh sách ở `data.content`.
- Các JSON bên dưới là minh họa contract. UUID, số tiền, URL và thời gian phải thay bằng response thực tế; không dùng lại quote mẫu để checkout.

## 3. Các màn hình và trạng thái UI

| Màn hình | Nội dung/nút cần có |
|---|---|
| SELLER: kiện hàng | Cân nặng sau đóng gói, dài/rộng/cao, Lưu |
| BUYER: checkout | Tên, điện thoại, bản đồ chọn điểm nhận, địa chỉ chỉ đọc, Lấy phí vận chuyển |
| BUYER: xác nhận thanh toán | Giá hàng, phí ship, tổng tiền, thời hạn báo giá, nút Thanh toán |
| BUYER: thanh toán ngân hàng | Thông tin chuyển khoản từ BE, số tiền còn thiếu, trạng thái chờ/xác nhận |
| SELLER: đơn bán | Danh sách đơn, trạng thái thanh toán, hạn xác nhận, Xác nhận đơn, Gọi tài xế |
| BUYER/SELLER: vận đơn | Mã Lalamove, trạng thái, timeline, tài xế, bản đồ khi có GPS, link theo dõi |
| BUYER: nhận hàng | Đã nhận hàng khi BE xác nhận giao thành công và ký quỹ còn HELD |

Checkout nên có state `addressDraft`, `resolvedAddress`, `quote`, `quoteLoading`, `quoteError`, `checkoutRequestId`, `order`.
Không cho thanh toán khi chưa có quote hợp lệ. Khi đổi bài, địa chỉ/tọa độ, người liên hệ hoặc negotiationId: xóa quote cũ, lấy báo giá lại. Nếu hủy request cũ hoặc response về sai thứ tự, chỉ nhận kết quả ứng với state địa chỉ mới nhất.

## 4. SELLER lưu kiện hàng trước khi BUYER mua

Quyền: SELLER và LISTING_CREATE_SELF; chỉ chủ bài được sửa.

```http
PUT /api/v1/posts/{postId}/shipping-package
```

```json
{
  "weight": 12000,
  "length": 35,
  "width": 30,
  "height": 35
}
```

| Field | Đơn vị | Kiểm tra đầu vào |
|---|---|---|
| weight | gram, số nguyên | Lớn hơn hoặc bằng 1 |
| length | cm, số nguyên | Lớn hơn hoặc bằng 1 |
| width | cm, số nguyên | Lớn hơn hoặc bằng 1 |
| height | cm, số nguyên | Lớn hơn hoặc bằng 1 |

SecondLife không áp trần cố định 50 kg hoặc 200 cm mỗi chiều. DTO và bước tạo payload chỉ kiểm tra số đo dương; khả năng vận chuyển được kiểm tra khi lấy báo giá dựa trên `load`, `dimensions` của từng dịch vụ trong catalogue `GET /v3/cities` của Lalamove tại điểm lấy hàng. Chỉ xét xe có tải trọng/kích thước và đơn vị hợp lệ. Tham khảo [Get City Info của Lalamove](https://developers.lalamove.com/#get-city-info).

Lưu kiện hàng thành công không đồng nghĩa tuyến giao hàng đã được hỗ trợ. Ví dụ kiện 100 kg, dài 220 cm được lưu, sau đó BE chỉ lấy báo giá từ xe đủ sức chở kiện; nếu không có xe phù hợp hoặc tuyến không khả dụng thì trả lỗi rõ ràng ở bước quote. Không hardcode giới hạn hoặc danh sách xe ở FE; không dùng phân loại xe ước lượng trên giao diện để quyết định cho phép vận chuyển.

Nếu UI nhập kg, quy đổi sang gram trước khi gửi; không gửi 12 để biểu thị 12 kg. Các số đo phải gồm bao bì, không chỉ kích thước sản phẩm trần. Các field vẫn là số nguyên 32 bit trong API/DB; số vượt kiểu dữ liệu hoặc JSON sai kiểu bị trả HTTP 400, đây không phải tải trọng tối đa của Lalamove.

Bài DRAFT/REJECTED/ACTIVE/PENDING_INSPECTION có thể sửa kiện theo điều kiện BE. Bài đã bán không được sửa; API trả 409. Nếu kiện đổi sau khi BUYER lấy quote nhưng chưa đặt đơn, quote cũ bị chặn tại checkout, BUYER phải lấy quote mới.

## 5. Địa chỉ lấy hàng SELLER

Thông thường đã lưu khi đăng ký seller trước eKYC: tái sử dụng, không bắt nhập lại mỗi đơn. Hướng dẫn onboarding: [EKYC_SELLER_VERIFICATION_GUIDE.md](EKYC_SELLER_VERIFICATION_GUIDE.md).

```http
GET /api/v1/shipping/pickup-address
```

GET cho phép BUYER và SELLER đọc địa chỉ của chính tài khoản đăng nhập. Chưa có địa chỉ trả HTTP 200, success=true và không có trường data (quy ước bỏ field null của ApiResponse); FE hiển thị trạng thái chưa cấu hình. PUT vẫn chỉ dành cho SELLER; BUYER lưu địa chỉ qua luồng onboarding.

Nếu seller cần cập nhật địa chỉ cho các báo giá mới:

```http
PUT /api/v1/shipping/pickup-address
```

```json
{
  "name": "Nguyễn Seller",
  "phone": "0912345678",
  "latitude": 21.0333,
  "longitude": 105.7937
}
```

BE tự xác định address từ tọa độ và lưu thông tin người liên hệ. Địa chỉ của đơn đã tạo lấy từ snapshot báo giá, không tự đổi khi seller sửa hồ sơ sau đó.

## 6. BUYER lấy bài và chọn địa chỉ nhận

```http
GET /api/v1/posts?page=0&size=10
GET /api/v1/posts/{postId}
```

Lấy postId/sellerId/price/status. Luồng mẫu dành cho bài ACTIVE, mua ngay, không thương lượng. Bài RESERVED cần negotiationId được chấp nhận của đúng BUYER; gửi cùng negotiationId khi báo giá và checkout.

### 6.1. Tọa độ từ đâu?

FE lấy từ marker người dùng bấm/kéo trên bản đồ hoặc `navigator.geolocation.getCurrentPosition`. Không bắt người mua gõ kinh độ/vĩ độ và không dùng GPS tài xế làm địa chỉ nhận.

Leaflet có thể dùng cho bản đồ đã có trong dự án. Thư viện bản đồ hiển thị điểm; BE mới là bên xác định địa chỉ từ tọa độ. Không phải tích hợp thêm giao diện Partner Portal vào trang checkout.

### 6.2. Xem trước địa chỉ do BE xác định

```http
POST /api/v1/shipping/reverse-geocode
```

```json
{
  "latitude": 21.0306,
  "longitude": 105.8008
}
```

Các field trong data:

```json
{
  "address": "Địa chỉ do BE xác định từ tọa độ",
  "latitude": 21.0306,
  "longitude": 105.8008,
  "city": "HANOI",
  "countryCode": "VN",
  "placeId": "ID do dịch vụ địa chỉ trả về nếu có"
}
```

Hiển thị address chỉ đọc. Khi chọn marker mới, xóa kết quả cũ và quote cũ. Có thể lấy phí sau khi người dùng bấm xác nhận vị trí để tránh gọi báo giá trên mỗi lần kéo marker.

Không gửi các field GHN cũ như districtId/wardCode/provinceName/newAddress; ShippingAddressRequest hiện chỉ có name, phone, latitude, longitude. Trường address do BE xác định, BUYER không tự nhập để thay thế tọa độ.

## 7. BUYER lấy phí — BE tự chọn xe

```http
POST /api/v1/shipping/quotes
```

```json
{
  "postId": "1c2dd5e3-36db-420b-b313-9ed606b017a9",
  "deliveryAddress": {
    "name": "Nguyễn Văn A",
    "phone": "0987654321",
    "latitude": 21.0306,
    "longitude": 105.8008
  }
}
```

Không gửi serviceType, fee, giá hàng, sellerId hoặc thông số kiện hàng từ BUYER. BE lấy chúng từ dữ liệu đã lưu. Field serviceType cũ được bỏ qua để tương thích; nó không điều khiển loại xe.

BE thực hiện:

1. Kiểm tra bài còn mua được, BUYER không phải chủ bài, negotiation hợp lệ nếu có.
2. Lấy địa chỉ seller hoặc trung tâm kiểm định thực sự giữ hàng, cùng thông số kiện hàng.
3. Xác định địa chỉ nhận từ tọa độ; điểm lấy phải thuộc thành phố hỗ trợ.
4. Đọc danh mục xe Lalamove của thành phố lấy hàng. Chỉ chọn xe có đủ thông tin tải trọng và kích thước với đơn vị xác định được.
5. Quy đổi kg → gram và m → cm (có hỗ trợ đơn vị g/t, cm/mm theo dữ liệu catalogue), so sánh cả cân nặng lẫn ba chiều.
6. Giữ chiều cao kiện hàng theo chiều đứng. Chỉ cho phép đổi chiều dài với chiều rộng; không tự đặt nằm tủ lạnh/máy giặt.
7. Sắp xếp xe đủ điều kiện theo tải trọng tăng dần, gọi xe nhỏ nhất trước và dừng ngay khi có báo giá hợp lệ. Chỉ thử xe kế tiếp khi Lalamove trả 422 với ERR_OUT_OF_SERVICE_AREA hoặc ERR_INVALID_SERVICE_TYPE; lỗi xác thực/mạng/hạn mức được trả ngay cho FE. Không bảo đảm đây là giá thấp nhất của mọi loại xe.
8. Lưu loại xe, phí, địa chỉ, kiện hàng và quote Lalamove vào snapshot.

Báo giá tự động trùng tuyến, địa chỉ và kích thước/cân nặng được dùng lại trong tối đa 30 giây trên cùng một BE instance (tối đa 128 mục), luôn giới hạn bởi thời hạn quote Lalamove. Request đồng thời dùng chung kết quả; lỗi không được cache. Đổi tuyến hoặc kiện hàng tạo cache key mới. Khi đặt chuyến, BE vẫn lấy báo giá mới để tránh dùng lịch lấy hàng cũ.

Response minh họa:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "quoteId": "e456898e-7f33-47c9-8ea1-cdb3f4eb4202",
    "leg": "SELLER_TO_BUYER",
    "shippingFee": 27000,
    "productPrice": 1000000,
    "totalPayable": 1027000,
    "insuranceValue": 0,
    "expiresAt": "2026-10-16T10:05:00Z",
    "expectedDeliveryTime": null,
    "provider": "LALAMOVE",
    "serviceType": "MOTORCYCLE",
    "providerQuotationId": "3605105567955862251"
  }
}
```

Thời gian/ID/phí trên chỉ minh họa; dùng response thật. API có thể bỏ field null. Timestamp Z là UTC, hiển thị theo múi giờ người dùng.

### Không có xe hoặc tuyến phù hợp

`POST /api/v1/shipping/quotes` trả HTTP 400 theo `ErrorResponse` hiện có khi không có xe đủ tải trọng/kích thước tại điểm lấy hàng:

```json
{
  "success": false,
  "message": "Không có loại xe Lalamove đủ tải trọng và kích thước cho kiện hàng tại điểm lấy hàng. Giới hạn từng loại xe tại điểm lấy hàng (dài x rộng x cao): MOTORCYCLE: tối đa 30 kg, 50 x 40 x 50 cm; VAN: tối đa 500 kg, 170 x 120 x 120 cm",
  "path": "/api/v1/shipping/quotes",
  "errors": [],
  "timestamp": "2026-10-24T10:00:00"
}
```

Phần giới hạn trong `message` được tạo từ các xe có tải trọng/kích thước xác minh được của đúng thành phố lấy hàng, quy đổi sang kg/cm. Không lấy xe thành phố khác, không lấy dữ liệu thiếu/sai đơn vị, không gộp tải trọng lớn nhất của một xe với kích thước lớn nhất của xe khác. Nếu catalogue không có xe với thông số xác minh được thì chỉ trả thông báo không có xe phù hợp, không tự dựng mức tối đa. Các mức trong ví dụ chỉ minh họa, không dùng để hardcode ở FE; có xe trong catalogue chưa bảo đảm tuyến cụ thể khả dụng.

Nếu có xe đủ sức chở nhưng tất cả báo giá đều trả 422 với `ERR_OUT_OF_SERVICE_AREA` hoặc `ERR_INVALID_SERVICE_TYPE`, BE trả HTTP 400 cùng cấu trúc trên và `message` là `Lalamove chưa hỗ trợ tuyến giao hàng này với các loại xe phù hợp`.

Điểm lấy hàng ngoài khu vực hệ thống hỗ trợ trả HTTP 400 với `message` là `Điểm lấy hàng nằm ngoài khu vực vận chuyển được hỗ trợ`. FE phải hiển thị `message` ngay cả khi `errors` rỗng, xóa quote cũ và chặn checkout cho đến khi có quote hợp lệ; người dùng cần sửa thông tin kiện hàng hoặc địa chỉ phù hợp. Không tự gán xe máy, phí 0 hay coi lưu kiện thành công là đã hỗ trợ giao hàng.

Lỗi xác thực, hạn mức, mạng hoặc catalogue không hợp lệ vẫn theo status lỗi dịch vụ tương ứng (429/502/503/504); không diễn giải các lỗi này thành kiện hàng vượt tải trọng. Các ID, phí và timestamp trong tài liệu chỉ minh họa.

### FE hiển thị kết quả

- Giá hàng: productPrice.
- Phí giao hàng: shippingFee, do BUYER trả.
- Tổng thanh toán: totalPayable.
- Loại xe tự chọn: serviceType, chỉ đọc. Dùng nhãn phù hợp nếu có mapping; luôn có fallback hiển thị mã gốc, không đổi mã trước khi gửi BE.
- Thời hạn báo giá: expiresAt.
- expectedDeliveryTime=null: hiển thị “Chưa có dự kiến giao hàng”, không tự dựng ngày giao.
- insuranceValue=0: không diễn giải thành cam kết bồi thường không giới hạn.

GET /api/v1/shipping/cities dùng tham khảo danh mục dịch vụ theo thành phố; không còn là bước bắt BUYER chọn xe. /shipping/provinces là alias catalogue, không phải cây địa chỉ GHN.
Trong lần kiểm tra Sandbox hiện tại, tài khoản trả danh mục Hà Nội và TP.HCM; Đà Nẵng chỉ dùng được nếu Lalamove thật sự trả dịch vụ khả dụng. Không hardcode xe hoặc tự tạo thành phố để bỏ qua giới hạn provider.

## 8. Checkout và thời hạn quote

FE hiển thị tổng tiền để BUYER xác nhận trước khi gọi tạo đơn. Thời hạn quote chỉ áp dụng cho việc sử dụng quote tại checkout; không đồng nhất với thời hạn thanh toán ngân hàng trên đơn đã tạo.

### 8.1. Thanh toán bằng ví

```http
POST /api/v1/orders
```

```json
{
  "requestId": "9d36bf83-b009-422c-99d4-3045a604686a",
  "postId": "1c2dd5e3-36db-420b-b313-9ed606b017a9",
  "shippingQuoteId": "e456898e-7f33-47c9-8ea1-cdb3f4eb4202",
  "paymentMethod": "WALLET"
}
```

Response trực tiếp, các field chính minh họa:

```json
{
  "id": "a2d11d05-1beb-4a56-a633-3fa86c9e9490",
  "postId": "1c2dd5e3-36db-420b-b313-9ed606b017a9",
  "paymentMethod": "WALLET",
  "shippingQuoteId": "e456898e-7f33-47c9-8ea1-cdb3f4eb4202",
  "finalPrice": 1000000,
  "shippingFee": 27000,
  "totalPaid": 1027000,
  "status": "PROCESSING",
  "escrowStatus": "HELD",
  "confirmationStatus": "PENDING"
}
```

Lưu id làm orderId. BUYER đã thanh toán nhưng hàng chưa được giao; HELD là ký quỹ, không phải tiền seller đã nhận.

### 8.2. Chuyển khoản ngân hàng trực tiếp

Dùng cùng POST /orders nhưng paymentMethod=BANK_TRANSFER. Tạo requestId mới cho thao tác mới; không đổi paymentMethod khi retry cùng requestId.

```json
{
  "requestId": "e5b7163d-1194-4084-b831-3258594c3265",
  "postId": "1c2dd5e3-36db-420b-b313-9ed606b017a9",
  "shippingQuoteId": "e456898e-7f33-47c9-8ea1-cdb3f4eb4202",
  "paymentMethod": "BANK_TRANSFER"
}
```

Đây là phương án thay thế WALLET, không thực hiện cả hai với một bài đã bán. Đơn mới có thể ở PENDING_PAYMENT. Hiển thị chỉ dẫn chuyển tiền từ payment và theo dõi:

```http
GET /api/v1/orders/{orderId}/payment
```

API trả trực tiếp thông tin orderId/status/orderStatus/escrowStatus, totalAmount/receivedAmount/remainingAmount, transferContent, bankAccountName/bankAccountNumber/bankName, expiresAt/paidAt. FE không gọi endpoint webhook để tự xác nhận đã trả tiền. Chỉ chuyển UI sang thanh toán thành công khi BE xác nhận; seller không gọi tài xế khi đơn còn PENDING_PAYMENT.

Chi tiết QR/chuyển khoản: [FE_SEPAY_VIETQR_INTEGRATION_AND_UX_SAFETY_GUIDE.md](FE_SEPAY_VIETQR_INTEGRATION_AND_UX_SAFETY_GUIDE.md).

### 8.3. Quote hết hạn hoặc dữ liệu đổi trước checkout

POST /orders có thể trả 409 khi quote hết hạn/đã dùng, giá hàng hoặc kiện hàng thay đổi. Khi đó:

1. Không đánh dấu đã thanh toán; không giữ spinner vô hạn.
2. Xóa quote cũ, lấy quote mới với địa chỉ hiện tại.
3. Hiển thị lại phí và tổng tiền. Nếu khác trước, yêu cầu BUYER xác nhận lại.
4. Tạo thao tác checkout mới sau khi đã biết lần cũ thất bại; không đổi requestId chỉ vì timeout mạng.

Sau timeout không rõ đơn đã tạo chưa: retry đúng requestId và payload cũ để nhận lại đơn, hoặc kiểm tra danh sách đơn trước. Nếu quote đã hết hạn nhưng đơn thực sự đã tạo, retry cùng requestId có thể trả lại đơn cũ; không tạo thêm thao tác thanh toán.

## 9. SELLER nhận và xác nhận đơn

```http
GET /api/v1/orders/seller?page=0&size=10
GET /api/v1/orders/{orderId}/confirmation
```

Dùng id của đơn mua, không dùng postId hoặc orderCode Lalamove làm orderId.
GET confirmation có data gồm confirmationStatus, paidAt, confirmationDeadline, confirmationCancelAt, sellerConfirmedAt, latePenaltyPoints, timeoutPenaltyPoints. FE dùng deadline BE trả, không tự đặt lại 24h/48h khi reload trang.

Khi đã thanh toán và đơn còn hợp lệ:

```http
PUT /api/v1/orders/{orderId}/confirm
```

Không body. Kết quả `data.confirmationStatus=CONFIRMED`. Reload danh sách đơn và trạng thái; không chỉ thay nhãn cục bộ. SELLER chuẩn bị hàng rồi mới bấm Gọi tài xế.

## 10. SELLER gọi tài xế

```http
POST /api/v1/orders/{orderId}/shipments
```

```json
{
  "requestId": "63440fd7-7abf-4cdc-a23a-677349dc1c39",
  "leg": "SELLER_TO_BUYER"
}
```

Luồng giao thẳng không gửi lại địa chỉ, kích thước, loại xe hoặc phí. BE dùng snapshot đã gắn với đơn, làm mới báo giá cùng loại xe để đặt chuyến và kiểm tra phí.

Response minh họa:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "shipmentId": "2e638e48-29fc-38a5-b14b-2e554f7f416e",
    "orderId": "a2d11d05-1beb-4a56-a633-3fa86c9e9490",
    "leg": "SELLER_TO_BUYER",
    "orderCode": "3605105567955862251",
    "status": "READY_TO_PICK",
    "providerStatus": "ASSIGNING_DRIVER",
    "quotedFee": 27000,
    "actualFee": 27000,
    "expectedDeliveryTime": null,
    "deliveredAt": null,
    "lastError": null,
    "provider": "LALAMOVE",
    "driverId": null,
    "shareLink": "https://share.sandbox.lalamove.com/duong-dan-minh-hoa"
  }
}
```

shareLink phải dùng nguyên URL BE trả, không dựng từ orderCode. Số đơn 19 chữ số phải giữ string, không ép thành JavaScript Number.

### Quy tắc lỗi và retry khi gọi tài xế

- Chặn nút khi đang gửi, giữ requestId đến khi biết kết quả.
- HTTP 200/success=true chưa đủ để kết luận đã gọi tài xế thành công. Kiểm tra status, orderCode và lastError.
- orderCode đã có: hiển thị vận đơn hiện tại, không tạo request mới để gọi thêm xe.
- CREATING/FAILED hoặc timeout chưa rõ có vận đơn thật: giữ requestId, tải lại danh sách, cho STAFF đối soát; không tự sinh UUID mới và tạo lại liên tục.
- CANCELLED, orderCode=null, lastError xác nhận lỗi từ chối trước khi tạo: sau khi khắc phục mới cho thử thao tác mới theo kết quả BE.
- Phí báo lại lớn hơn số tiền ship đã trả: BE trả 409 trước khi tạo chuyến; không tự thu thêm ví BUYER và không âm thầm đổi xe. Luồng hiện tại yêu cầu hủy đơn chưa gửi theo điều kiện BE, xử lý tiền cũ rồi checkout báo giá mới; chưa có nút/API chuyên biệt “thu chênh lệch ship”.
- SELLER đổi thông số kiện sau khi BUYER đã trả tiền bị chặn; nếu thực tế đóng gói khác, không bấm gọi xe với thông số sai. Cần xử lý đơn cũ và báo giá mới theo luồng được phép.

Hủy đơn mua và hủy vận đơn là hai thao tác khác nhau:

```http
PUT /api/v1/orders/{orderId}/cancel
POST /api/v1/shipments/{shipmentId}/cancel
```

Hủy vận đơn không đồng nghĩa tự hoàn tiền/hủy đơn mua. Đơn mua chỉ hủy được khi BE cho phép, có kiểm tra vận đơn. SELLER hủy đơn cần lý do thuộc bộ SELLER, có thể ảnh hưởng uy tín:

```json
{
  "reasonCode": "SELLER_CANNOT_FULFILL",
  "reason": "Không thể thực hiện đơn với thông số kiện hàng và phí đã xác nhận; cần xử lý lại đơn."
}
```

Không gắn sẵn hành động hủy/hoàn tiền tự động vào lỗi 409 của FE.

## 11. Tracking và timeline cho BUYER/SELLER

```http
GET /api/v1/orders/{orderId}/shipments
GET /api/v1/shipments/{shipmentId}/events
GET /api/v1/shipments/{shipmentId}/tracking
```

Danh sách vận đơn ở data (mảng). Có thể có nhiều lần tạo thất bại và chặng trả hàng; không mặc định phần tử đầu luôn là chuyến đang hoạt động. Chọn đúng leg và trạng thái, giữ các lần cũ để hiển thị lịch sử khi cần.
Timeline events trả data là mảng với id, shipmentId, eventKey, type, status, occurredAt, reason, payload. Hiển thị nhãn sự kiện/thời gian; không render nguyên payload kỹ thuật hoặc dữ liệu nhạy cảm thành nội dung UI. Không tự sắp trạng thái “lùi” chỉ vì response cũ về sau.

Tracking data khi có vị trí hợp lệ:

```json
{
  "shipmentId": "2e638e48-29fc-38a5-b14b-2e554f7f416e",
  "provider": "LALAMOVE",
  "orderCode": "3605105567955862251",
  "status": "PICKED",
  "providerStatus": "PICKED_UP",
  "shareLink": "https://share.sandbox.lalamove.com/duong-dan-minh-hoa",
  "driverId": "80042",
  "locationStatus": "AVAILABLE",
  "driver": {
    "driverId": "80042",
    "name": "Tài xế minh họa",
    "phone": "+84901234567",
    "plateNumber": "Biển số do Lalamove trả về",
    "photo": "",
    "coordinates": {
      "lat": "21.0320",
      "lng": "105.7980",
      "updatedAt": "2026-10-16T10:00:00Z"
    }
  }
}
```

GPS ví dụ không phải vị trí thật đã đo. FE chỉ vẽ marker tài xế khi locationStatus=AVAILABLE và coordinates hợp lệ; lat/lng có thể là string, chuyển bằng Number khi đưa vào thư viện bản đồ. Không ép mã đơn thành Number.

Nếu UNAVAILABLE: driver có thể null hoặc có thông tin người lái nhưng coordinates=null. Hiển thị “Chưa có vị trí tài xế”, vẫn hiển thị trạng thái/timeline/shareLink. Không đưa marker về 0,0, không tự tạo đường di chuyển giả. Sandbox có thể trả GPS 0,0 hoặc thời gian 1970; BE đã lọc chúng.

### Phân biệt ba loại trạng thái

| Lalamove providerStatus | Shipment status SecondLife | Nhãn gợi ý |
|---|---|---|
| ASSIGNING_DRIVER | READY_TO_PICK | Đang tìm tài xế |
| ON_GOING | ASSIGNED | Tài xế đã nhận chuyến |
| PICKED_UP | PICKED | Đã lấy hàng / Đang vận chuyển |
| COMPLETED | DELIVERED | Giao thành công |
| CANCELED | CANCELLED | Vận đơn đã hủy |
| REJECTED / EXPIRED | FAILED | Chuyến không thực hiện được |

Shipment DELIVERED khác Order COMPLETED. Khi carrier xác nhận lấy hàng, đơn mua có thể chuyển SHIPPED; carrier giao xong chuyển DELIVERED. Việc hoàn tất đơn/giải ngân là bước tiếp theo, không suy ra từ vận đơn.

### Đồng bộ và polling

```http
POST /api/v1/shipments/{shipmentId}/sync
```

Không body. API đọc trạng thái từ Lalamove về BE; không đổi trạng thái bên Lalamove. Dùng cho nút Làm mới khi cần.
BE nhận webhook để cập nhật chính thức. FE không gọi callback/webhook và không tự POST “đã lấy hàng/đã giao”. GET tracking không thay thế chức năng sync.
Polling tracking không quá một lần mỗi 10 giây; tạm dừng khi tab ẩn/unmount, backoff khi lỗi hoặc hạn mức và dừng khi chuyến đã kết thúc. Không tự gọi sync liên tục trên mỗi render.

## 12. BUYER xác nhận nhận hàng và giải ngân

Chỉ cho bấm khi BE đã xác nhận carrier giao thành công, đơn mua DELIVERED và escrow HELD:

```http
PUT /api/v1/orders/{orderId}/delivered
```

Không body. Response OrderResponseDTO trực tiếp. Khi hoàn tất: status=COMPLETED, escrowStatus=RELEASED. Reload đơn và số dư theo API ví đang dùng.
BE tính tiền seller thực nhận sau hoa hồng và lưu settlement; FE không tự cộng toàn bộ giá hàng vào ví seller. Cơ chế giải ngân tự động sau khoảng chờ do BE cấu hình/job xử lý; FE không chạy timer để gọi giải ngân thay người dùng.
Nếu escrow FROZEN hoặc có quy trình trả hàng/tranh chấp, xử lý theo BE; không bật nút xác nhận nhận hàng để vượt qua trạng thái này.

## 13. Chặng trung tâm kiểm định

Luồng chính trong tài liệu là SELLER_TO_BUYER. Nếu quote trả leg=CENTER_TO_BUYER, STAFF/ADMIN tạo chặng giao từ trung tâm theo đúng leg của quote đã thanh toán; SELLER không tự đổi nó về SELLER_TO_BUYER.

```json
{
  "requestId": "ce80905d-4190-4fd6-9736-9d876304e1a8",
  "leg": "CENTER_TO_BUYER"
}
```

Vẫn gọi POST /api/v1/orders/{orderId}/shipments bằng tài khoản STAFF/ADMIN đủ điều kiện. Địa chỉ trung tâm lấy từ snapshot, không bắt nhập lại nếu đã có.
Các chặng SELLER_TO_CENTER và BUYER_TO_SELLER có điều kiện kiểm định/trả hàng riêng; không tự tạo chúng trong nút mua ngay. Tham khảo các tài liệu kiểm định và tracking hiện có cho phần nghiệp vụ này.

## 14. Test Sandbox cho FE và QA

1. Dùng tài khoản SELLER lưu pickup/kiện hàng hợp lệ và có bài ACTIVE.
2. Đổi BUYER, chọn điểm nhận cùng khu vực thực tế được Lalamove hỗ trợ.
3. Reverse-geocode → quote không có serviceType → xem phí và xe do BE trả.
4. Checkout WALLET khi đủ tiền, hoặc BANK_TRANSFER rồi đợi BE xác nhận thanh toán.
5. Đổi SELLER, xác nhận đơn → gọi tài xế, lưu shipmentId/orderCode.
6. Người có quyền tài khoản tích hợp vào Partner Portal → SANDBOX → Records, chọn đúng thành phố và tìm orderCode. Dùng chức năng giả lập trạng thái khả dụng của đơn còn hoạt động.
7. Lần lượt test ON_GOING → PICKED_UP → COMPLETED theo thao tác Portal cho phép; đơn đã Completed/Cancelled không dùng để test lại các bước trước.
8. Kiểm tra webhook BE nhận và API shipments/events/tracking. Nếu cần, bấm sync để đối chiếu trạng thái thật ở Sandbox.
9. Đổi BUYER, xác nhận nhận hàng sau carrier delivery; kiểm tra order COMPLETED/RELEASED.

Partner Portal là công cụ của bên tích hợp, không nhúng vào UI BUYER/SELLER. Production tài xế sử dụng ứng dụng tài xế Lalamove. FE dev không cần tài khoản Portal nếu có người khác thao tác giả lập giúp.
Webhook cần URL BE công khai truy cập được; localhost phải dùng tunnel đã cấu hình đúng endpoint/signature. FE chỉ gọi BE, không giữ secret Lalamove. Việc chia sẻ môi trường test nên thống nhất một BE nhận webhook.

### Checklist kiểm thử bắt buộc

| Ca test | Kết quả mong đợi |
|---|---|
| Thiếu cân nặng hoặc một chiều | Báo lỗi yêu cầu seller bổ sung, không chọn xe mặc định |
| Kiện vượt khả năng xe máy | Không chọn xe máy; dùng xe lớn phù hợp nếu có báo giá |
| Kiện chỉ vừa nếu đặt nằm | Không tự xoay chiều cao để chọn xe nhỏ |
| Không có xe đủ tải/kích thước | Hiển thị chưa hỗ trợ kiện hàng |
| Địa chỉ ngoài vùng phục vụ | Hiển thị lỗi khu vực, không cho thanh toán |
| Hai xe cùng phù hợp | Xe có báo giá thấp hơn được chọn |
| BUYER gửi serviceType cũ | Bị bỏ qua; BE vẫn tự chọn |
| Quote hết hạn trước tạo đơn | 409, không trừ tiền, lấy giá mới và xác nhận lại |
| SELLER đổi kiện trước checkout | Quote cũ bị từ chối |
| SELLER đổi kiện sau thanh toán | Bị chặn |
| Bấm đặt đơn hai lần/timeout | Retry cùng requestId; không nhân đôi thanh toán |
| Bấm gọi tài xế hai lần/timeout | Dùng cùng requestId, kiểm tra vận đơn hiện có |
| Giá tăng khi gọi tài xế | Chặn booking, không tự thu thêm tiền BUYER |
| Quote làm mới trả sai loại xe | BE từ chối, không hạ cấp xe |
| HTTP 200 nhưng shipment CANCELLED/lastError | UI hiển thị lỗi, không báo đã tìm được tài xế |
| GPS Sandbox 0,0/1970 | Hiển thị không có GPS; không vẽ vị trí giả |
| Giao thành công nhưng chưa giải ngân | Phân biệt shipment DELIVERED với order COMPLETED |

## 15. Quy tắc lỗi và triển khai FE

| HTTP | Hướng xử lý |
|---|---|
| 400 | Hiển thị `message` khi kiện/tuyến chưa được hỗ trợ, kể cả `errors=[]`; nếu có lỗi trường trong `errors[]` thì hiển thị cạnh trường; chưa cho checkout khi thiếu quote hợp lệ |
| 401 | Refresh/đăng nhập lại theo luồng Auth |
| 403 | Sai chủ đơn/quyền hoặc seller bị hạn chế; không retry vô hạn |
| 404 | ID không tồn tại; tải lại dữ liệu liên quan |
| 409 | Đọc message: quote hết hạn, dữ liệu đổi, chưa xác nhận, đã có vận đơn, phí tăng...; không gộp mọi 409 thành hết hạn quote |
| 429 | Lalamove vượt hạn mức; giữ input và chờ trước khi thử lại, không retry ngay hoặc gọi dồn |
| 502/503/504 | Provider/cấu hình/tạm lỗi mạng; giữ input, hiển thị retry có kiểm soát |

Khi BE trả success=false, hiển thị message và errors nếu có; không coi payload lỗi là quote. Nút thanh toán/gọi tài xế có loading và khóa gửi lặp. Không log Bearer token, key, contact đầy đủ hoặc payload webhook vào console công khai.
BE cần restart sau cập nhật; Swagger reload để bỏ serviceType khỏi request. Cấu hình LALAMOVE_SERVICE_TYPE đã bỏ, không dùng nó để quyết định xe. Không cần migration DB mới riêng cho tính năng tự chọn xe vì snapshot dùng payload hiện có.

Các test kiện hàng bao phủ lưu kiện trên 50 kg/200 cm, chọn xe theo tải trọng và kích thước thực tế, quy đổi đơn vị, giữ kiện đứng, vượt từng giới hạn và response HTTP 400 kèm mức tối đa từng xe của đúng thành phố. Catalogue trong test được giả lập để kiểm tra các biên; mức tải trọng/kích thước khi chạy thật luôn lấy từ Lalamove. Số tiền/đơn trong tài liệu chỉ minh họa, không phải kết quả giao hàng Production.
