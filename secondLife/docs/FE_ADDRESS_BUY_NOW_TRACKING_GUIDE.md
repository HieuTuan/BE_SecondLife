# Hướng dẫn FE: địa chỉ seller trước eKYC, mua ngay và tracking Lalamove

## 1. Phạm vi và trạng thái hiện tại

Cập nhật 24/10/2026: contract `GET /api/v1/shipping/vehicles` dành cho mục **“Xem thông tin vận chuyển”** và validation ngay khi nhập kiện hàng được mô tả đầy đủ trong [FE_SHIPPING_VEHICLE_CATALOGUE_GUIDE.md](FE_SHIPPING_VEHICLE_CATALOGUE_GUIDE.md). Hướng dẫn này vẫn mô tả luồng địa chỉ, quote và checkout hiện có; việc bổ sung giao diện danh mục xe là công việc FE cần thực hiện.

Tài liệu mô tả contract BE hiện tại cho luồng:

```text
Chọn vị trí lấy hàng → lưu shop → xác nhận email → eKYC
→ đăng bán → BUYER chọn địa chỉ nhận → tính phí → thanh toán bằng ví
→ SELLER tạo vận đơn → tracking → BUYER xác nhận nhận hàng → quyết toán
```

Luồng mua ngay, tính phí, tạo vận đơn, cập nhật trạng thái và quyết toán đã được kiểm tra. Không đồng nghĩa toàn bộ SRS đã hoàn thiện:

- BE đã tự lọc/chọn xe theo tải trọng, kích thước và báo giá. Xem [hướng dẫn FE tự chọn xe mới](FE_AUTO_VEHICLE_SHIPPING_GUIDE.md) cho contract hiện tại.
- GPS tài xế thực tế chưa được xác minh với chuyến giao hàng Production.
- Sandbox hiện trả tài xế giả lập với GPS `0,0`/năm `1970`; BE lọc các điểm này thành không khả dụng.
- Hướng dẫn mua hàng dưới đây dành cho mua ngay, không thương lượng, giao thẳng SELLER → BUYER. Chặng từ trung tâm kiểm định có vai trò xử lý riêng.

Base URL local: `http://localhost:8080`. FE dùng base URL cấu hình theo môi trường.

API Authentication giữ `/api/auth`; các API nghiệp vụ dưới đây dùng `/api/v1`.

Các UUID, email, số điện thoại và tọa độ trong JSON là ví dụ. Thay bằng dữ liệu thực tế từ response hoặc người dùng.

## 2. Nguồn tọa độ và công cụ bản đồ

| Dữ liệu | Nguồn | Cách sử dụng |
|---|---|---|
| Tọa độ địa chỉ lấy/nhận hàng | Người dùng chọn marker trên bản đồ hoặc lấy vị trí thiết bị | FE gửi `latitude`, `longitude` cho BE |
| Địa chỉ dạng chữ | Geoapify, do BE gọi reverse geocoding | FE hiển thị địa chỉ BE trả về |
| Tọa độ tài xế | Lalamove qua API tracking của BE | FE cập nhật marker tài xế khi có GPS hợp lệ |

Đề xuất dùng Leaflet để hiển thị bản đồ, chọn điểm và kéo marker. Bản đồ nền có thể sử dụng Geoapify Map Tiles. Địa chỉ dạng chữ được xác định qua BE, không lấy từ chuỗi người dùng tự nhập.

Tài liệu tham khảo:

- [Leaflet](https://leafletjs.com/reference.html)
- [Geoapify Map Tiles](https://apidocs.geoapify.com/docs/maps/map-tiles/)
- [Browser Geolocation](https://developer.mozilla.org/en-US/docs/Web/API/Geolocation/getCurrentPosition)
- [Lalamove Driver Details](https://developers.lalamove.com/#driver-details)

Leaflet là thư viện hiển thị bản đồ; không tự cấp tọa độ GPS. Tọa độ được lấy từ sự kiện chọn điểm/marker hoặc Geolocation API của thiết bị.

Không đưa key/secret Lalamove hoặc key reverse geocoding của BE vào frontend. Nếu gọi map tiles trực tiếp từ FE, dùng key riêng dành cho trình duyệt và cấu hình giới hạn truy cập phù hợp.

## 3. Component chọn địa chỉ dùng chung

Component dùng cho địa chỉ lấy hàng SELLER và địa chỉ nhận hàng BUYER, gồm:

1. Tên người liên hệ.
2. Số điện thoại.
3. Bản đồ có marker kéo được.
4. Nút **Dùng vị trí hiện tại**.
5. Địa chỉ dạng chữ do BE xác định, chỉ đọc.
6. Nút **Xác nhận vị trí này**.

Người dùng không phải tự nhập kinh độ/vĩ độ. FE giữ chúng trong state từ điểm đã chọn. Không gửi các trường GHN cũ: `provinceName`, `districtName`, `wardName`, `districtId`, `wardCode`, `newAddress`.

### 3.1. Lấy vị trí thiết bị

```javascript
navigator.geolocation.getCurrentPosition(
  ({ coords }) => {
    selectLocation({
      latitude: coords.latitude,
      longitude: coords.longitude
    });
  },
  () => {
    showError("Không lấy được vị trí. Bạn hãy chọn điểm trên bản đồ.");
  },
  { enableHighAccuracy: true, timeout: 10000 }
);
```

`selectLocation` và `showError` là hàm FE cần triển khai. Trình duyệt yêu cầu quyền vị trí và secure context; triển khai website bằng HTTPS. Luôn có phương án chọn điểm thủ công khi quyền bị từ chối hoặc vị trí không chính xác.

### 3.2. Xem trước địa chỉ từ tọa độ

```http
POST /api/v1/shipping/reverse-geocode
Authorization: Bearer <accessToken>
Content-Type: application/json
```

```json
{
  "latitude": 21.0333,
  "longitude": 105.7937
}
```

FE sử dụng các trường trong `data`: `address`, `latitude`, `longitude`, `city`, `countryCode`, `placeId`.

- `address`: địa chỉ để hiển thị.
- `city`: thành phố BE đã xác định.
- `placeId`: mã địa điểm Geoapify; không cần gửi mã này trong request lưu địa chỉ.

API chỉ xem trước, chưa lưu địa chỉ. API lưu sẽ lookup riêng và trả kết quả chính thức.

### 3.3. Xử lý chọn điểm

- Khi click bản đồ hoặc kết thúc kéo marker, cập nhật tọa độ và gọi reverse-geocode.
- Không lookup liên tục theo từng pixel khi marker đang kéo.
- Trong lúc lookup, hiển thị loading và khóa xác nhận.
- Nếu nhiều request đang chạy, bỏ response của lần chọn cũ; chỉ sử dụng kết quả của tọa độ đang chọn.
- Khi lookup lỗi, xóa trạng thái xác nhận của địa chỉ cũ.
- Không dùng vị trí hiện tại của thiết bị làm địa chỉ cuối cùng khi người dùng chưa xác nhận.
- Gửi tọa độ dạng số, không đảo `latitude` và `longitude`. Marker Leaflet thường dùng thứ tự `[latitude, longitude]`.

BE giới hạn địa chỉ trong Hà Nội, Đà Nẵng và TP.HCM. FE hiển thị lỗi BE khi chọn ngoài khu vực hoặc điểm không xác định được địa chỉ đường/tòa nhà.

## 4. Đăng ký người bán trước eKYC

### 4.1. Màn hình và thứ tự

```text
Thông tin shop và địa chỉ lấy hàng
→ xác nhận email shop
→ nộp hồ sơ eKYC
→ xem kết quả xác thực
```

Khi mở màn hình:

```http
GET /api/v1/seller-onboarding/me
```

FE điền dữ liệu đã lưu và đặt marker theo `data.pickupAddress.latitude/longitude`. Dùng `emailVerified`, `canStartEkyc`, `nextStep` để xác định bước tiếp theo.

### 4.2. Lưu thông tin shop

```http
PUT /api/v1/seller-onboarding/me
```

```json
{
  "shopName": "Shop SecondLife",
  "email": "shop@example.com",
  "phone": "0912345678",
  "pickupAddress": {
    "name": "Nguyễn Văn A",
    "phone": "0912345678",
    "latitude": 21.0333,
    "longitude": 105.7937
  }
}
```

| Field | Ý nghĩa và kiểm tra |
|---|---|
| `shopName` | Bắt buộc, tối đa 30 ký tự |
| `email` | Email shop để nhận mã xác nhận |
| `phone` | Số điện thoại shop |
| `pickupAddress.name` | Người liên hệ lấy hàng, tối đa 100 ký tự |
| `pickupAddress.phone` | Số điện thoại Việt Nam; BE chấp nhận dạng 0, 84 hoặc +84 với độ dài phù hợp |
| `latitude` | Bắt buộc, trong khoảng -90 đến 90 |
| `longitude` | Bắt buộc, trong khoảng -180 đến 180 |

Ví dụ điện thoại di động hợp lệ: `0912345678`, `84912345678`, `+84912345678`. FE nên hướng dẫn người dùng nhập số thực tế, không dùng số mẫu Swagger.

BE lookup tọa độ bằng Geoapify rồi lưu địa chỉ dạng chữ. FE lấy địa chỉ từ response lưu thành công để hiển thị kết quả cuối cùng. Không gửi `address` do người dùng tự nhập để ghi đè địa chỉ BE xác định.

Hồ sơ có thể bị hạn chế sửa khi đã bắt đầu/hoàn tất xác thực. FE hiển thị lỗi nghiệp vụ từ BE; không tự giả định mọi trường luôn chỉnh sửa được. Sau khi sửa thông tin, đọc lại trạng thái xác nhận email từ response.

### 4.3. Xác nhận email shop

Gửi mã:

```http
POST /api/v1/seller-onboarding/email/send-code
```

Không có body. Xác nhận:

```http
POST /api/v1/seller-onboarding/email/verify
```

```json
{
  "otp": "123456"
}
```

Thay bằng OTP thực tế gồm 6 số. Chỉ cho sang màn hình eKYC khi `data.canStartEkyc === true`. Nếu `emailVerified` đã là true, bỏ qua gửi/xác nhận OTP.

### 4.4. eKYC và xem trạng thái

```http
POST /api/v1/seller-verifications
GET /api/v1/seller-verifications/me
```

POST gửi thông tin giấy tờ, URL ảnh và thông tin phiên theo hướng dẫn eKYC hiện có. Địa chỉ lấy hàng không nằm trong request eKYC, vì đã lưu ở onboarding.

Xem [hướng dẫn eKYC hiện có](EKYC_SELLER_VERIFICATION_GUIDE.md) để thực hiện upload giấy tờ, phiên VNPT, xử lý trạng thái và nộp lại hồ sơ.

FE cần phân biệt HTTP thành công với hồ sơ `APPROVED`. Không gửi POST tạo mới liên tục khi đang có hồ sơ xử lý. Hiển thị trạng thái, lý do từ chối/lỗi nhà cung cấp và hành động phù hợp với trạng thái hiện tại.

## 5. SELLER chuẩn bị dữ liệu giao hàng

Địa chỉ đã lưu từ onboarding. SELLER xem lại bằng:

```http
GET /api/v1/shipping/pickup-address
```

BUYER đang onboarding cũng được đọc địa chỉ của chính mình qua GET này. Nếu chưa có địa chỉ, BE trả HTTP 200 với success=true và không có data. PUT vẫn yêu cầu SELLER.

Nếu cần cập nhật, dùng component chọn vị trí và API:

```http
PUT /api/v1/shipping/pickup-address
```

Body gồm `name`, `phone`, `latitude`, `longitude`. Việc đổi địa chỉ hiện tại không tự đổi snapshot địa chỉ trong các đơn đã thanh toán.

Khai báo kiện hàng sau đóng gói:

```http
PUT /api/v1/posts/{postId}/shipping-package
```

```json
{
  "weight": 600,
  "length": 20,
  "width": 15,
  "height": 10
}
```

- `weight`: số nguyên dương, đơn vị gram.
- `length`, `width`, `height`: số nguyên dương, đơn vị cm.
- Không áp trần cố định 50 kg hoặc 200 cm mỗi chiều tại BE/FE. BE kiểm tra tải trọng và kích thước từ catalogue từng loại xe Lalamove theo điểm lấy hàng khi lấy báo giá; chỉ đổi chiều dài/rộng, giữ kiện đứng theo chiều cao.
- Lưu thông số thành công chỉ xác nhận dữ liệu hợp lệ, chưa xác nhận có dịch vụ vận chuyển. Nếu thiếu thông tin kiện hàng, BE chưa tính phí được.
- Nếu không có xe đủ sức chở hoặc tuyến không được hỗ trợ, API quote trả HTTP 400, `success=false`, `message` nêu rõ lý do và `errors=[]`. FE hiển thị `message`, xóa quote cũ và không cho checkout khi chưa có quote hợp lệ. Xem các [response lỗi minh họa](FE_AUTO_VEHICLE_SHIPPING_GUIDE.md#không-có-xe-hoặc-tuyến-phù-hợp).

## 6. BUYER xem bài đăng và chọn địa chỉ nhận

```http
GET /api/v1/posts?page=0&size=10
```

Danh sách nằm trong `data.content`. FE sử dụng `postId`, tên sản phẩm, mô tả, ảnh, giá và thông tin người bán. Bài để mua ngay cần đang `ACTIVE` và không thuộc chính BUYER.

Hiện controller chưa expose `GET /api/v1/posts/{postId}` dành cho BUYER. Dữ liệu danh sách đang được sử dụng cho luồng này.

Màn hình mua hàng gồm sản phẩm, người nhận, số điện thoại và component chọn địa chỉ nhận. Tọa độ ví dụ chỉ phục vụ test; người dùng xác nhận điểm nhận thực tế. Chọn điểm nằm trong vùng phục vụ của dịch vụ Lalamove.

## 7. Chọn dịch vụ và tính phí

Lấy danh sách thành phố/dịch vụ:

```http
GET /api/v1/shipping/cities
```

Danh mục `services[]` dùng tham khảo; BE tự chọn xe, BUYER không truyền `serviceType`. Chỉ hiển thị thành phố/dịch vụ BE thực sự trả về; không thêm thành phố giả nếu Sandbox chưa có dữ liệu.

```http
POST /api/v1/shipping/quotes
```

```json
{
  "postId": "1c2dd5e3-36db-420b-b313-9ed606b017a9",
  "deliveryAddress": {
    "name": "Nguyễn Văn B",
    "phone": "0987654321",
    "latitude": 21.0285,
    "longitude": 105.8542
  }
}
```

- Mua ngay: bỏ `negotiationId`.
- Không gửi `serviceType` từ BUYER; BE tự xác định loại xe phù hợp.
- BE lấy danh mục xe theo điểm lấy hàng, lọc tải trọng/kích thước, ưu tiên xe có tải trọng nhỏ nhất và dừng ở báo giá hợp lệ đầu tiên. Chỉ thử xe kế tiếp khi Lalamove từ chối tuyến/loại xe; không truy vấn mọi xe để so giá.
- BE lookup địa chỉ nhận từ tọa độ, sử dụng địa chỉ lấy hàng của SELLER và kiện hàng của bài đăng.
- Không dùng xe mặc định. Chi tiết lựa chọn, snapshot và xử lý lỗi: [FE_AUTO_VEHICLE_SHIPPING_GUIDE.md](FE_AUTO_VEHICLE_SHIPPING_GUIDE.md).

| Response trong `data` | FE sử dụng |
|---|---|
| `quoteId` | Mã báo giá SecondLife để tạo đơn |
| `productPrice` | Giá sản phẩm |
| `shippingFee` | Phí giao hàng |
| `totalPayable` | Tổng tiền cần thanh toán |
| `expiresAt` | Hạn báo giá |
| `leg` | Chặng giao hàng |
| `serviceType` | Loại dịch vụ được chọn |
| `providerQuotationId` | Mã báo giá Lalamove; không dùng thay `quoteId` khi tạo đơn |
| `expectedDeliveryTime` | Có thể null; khi null không tự dựng thời gian giao dự kiến |

```text
totalPayable = productPrice + shippingFee
```

Khi đổi địa chỉ, người nhận, số điện thoại hoặc dịch vụ, lấy báo giá mới. Khi hết hạn, khóa thanh toán và yêu cầu tính phí lại. Dùng tổng tiền BE trả về, không tự quyết định số tiền gửi cho BE.

## 8. Ví BUYER và thanh toán

```http
GET /api/v1/wallets/me
```

Số dư phải đủ `totalPayable`. Nếu thiếu tiền:

```http
POST /api/v1/wallets/deposit-request
```

```json
{
  "amount": 1000000
}
```

FE hiển thị tài khoản ngân hàng và mã nội dung chuyển khoản từ response. Tạo yêu cầu nạp chưa cộng tiền vào ví; tiền tăng sau khi giao dịch được webhook SePay ghi nhận. Đọc lại số dư để xác nhận.

Tạo đơn và thanh toán:

```http
POST /api/v1/orders
```

```json
{
  "postId": "1c2dd5e3-36db-420b-b313-9ed606b017a9",
  "shippingQuoteId": "e456898e-7f33-47c9-8ea1-cdb3f4eb4202",
  "requestId": "ad170019-befe-4ad1-9074-fd853430a53d"
}
```

Thay `shippingQuoteId` bằng `data.quoteId` của báo giá mới.

- API này trực tiếp trừ tổng tiền khỏi ví và giữ ký quỹ; không có bước thanh toán đơn riêng sau đó.
- ADMIN cần có chính sách hoa hồng DEFAULT đang hoạt động để tạo snapshot hoa hồng theo đơn.
- Thành công: đơn `PROCESSING`, ký quỹ `HELD`, bài đăng `SOLD`.
- Response đơn hàng là DTO trực tiếp: lấy `response.id` làm `orderId`, không lấy `response.data.id`.
- Sinh UUID `requestId` một lần cho thao tác thanh toán. Retry do lỗi mạng giữ nguyên UUID và body.
- Khóa nút thanh toán trong lúc xử lý. Khi không rõ request đã thành công hay chưa, retry cùng request, không tạo thao tác thanh toán mới.

## 9. SELLER tạo vận đơn

```http
GET /api/v1/orders/seller?page=0&size=10
```

Đơn mua hàng đang `PROCESSING` đã được BUYER thanh toán. SELLER chuẩn bị hàng rồi tạo vận đơn:

```http
POST /api/v1/orders/{orderId}/shipments
```

```json
{
  "requestId": "7f217e13-f64b-4cf2-bd21-7ce783fe9ef3",
  "leg": "SELLER_TO_BUYER"
}
```

Không nhập lại `fromAddress`, `toAddress` cho chặng giao thẳng. BE sử dụng snapshot địa chỉ theo đơn và lấy báo giá mới ngay trước lần tạo vận đơn mới để tránh giờ lấy hàng cũ.

Phí báo giá mới không được vượt phí BUYER đã trả. Khi BE trả xung đột vì tăng phí, FE hiển thị yêu cầu xử lý đơn/báo giá lại, không tự trừ thêm tiền.

| Kết quả | FE xử lý |
|---|---|
| Có `data.orderCode` | Đã có mã vận đơn Lalamove; lưu `shipmentId` để tracking |
| `CANCELLED`, `orderCode: null` | Lần tạo vận đơn thất bại; hiển thị `lastError` |
| `CREATION_UNCERTAIN` | Yêu cầu đối soát; không tạo thêm bằng UUID mới |

Không chỉ dựa vào `success: true` để thông báo vận chuyển đã đặt thành công. `CANCELLED` của shipment không tự đồng nghĩa đơn mua hàng đã hủy hoặc tiền đã hoàn.

Retry cùng thao tác giữ nguyên `requestId`. Sau khi xử lý nguyên nhân của một lần từ chối chắc chắn, tạo lần thử mới bằng UUID mới. Không tự động retry việc tạo vận đơn vô hạn.

Nếu chặng đã thanh toán là `CENTER_TO_BUYER`, STAFF/ADMIN tạo vận đơn từ trung tâm kiểm định theo chặng tương ứng.

## 10. Tracking giao hàng

### 10.1. API

| API | Chức năng |
|---|---|
| `GET /api/v1/orders/{orderId}/shipments` | Danh sách vận đơn của đơn mua hàng |
| `POST /api/v1/shipments/{shipmentId}/sync` | Đọc trạng thái phía Lalamove và cập nhật BE |
| `GET /api/v1/shipments/{shipmentId}/tracking` | Trạng thái, tài xế, link và GPS khi có |
| `GET /api/v1/shipments/{shipmentId}/events` | Lịch sử sự kiện BE đã ghi nhận |

Không có body cho các API trên. BUYER/SELLER phải là người tham gia đơn; STAFF/ADMIN có quyền xem theo BE.

`/sync` không tự đổi trạng thái phía Lalamove. Trạng thái phát sinh từ Lalamove, qua webhook hoặc đồng bộ thủ công. Endpoint webhook là `/api/v1/shipping/lalamove/callback`; đây là callback nhà vận chuyển, không phải API STAFF chỉnh trạng thái.

### 10.2. Trạng thái

| Lalamove `providerStatus` | Shipment `status` | Đơn mua hàng |
|---|---|---|
| `ASSIGNING_DRIVER` | `READY_TO_PICK` | `PROCESSING` |
| `ON_GOING` | `ASSIGNED` | `PROCESSING` |
| `PICKED_UP` | `PICKED` | `SHIPPED` |
| `COMPLETED` | `DELIVERED` | `DELIVERED` |
| `CANCELED` | `CANCELLED` | Xử lý theo nghiệp vụ đơn; không tự giả định hoàn tiền |
| `REJECTED` / `EXPIRED` | `FAILED` | Ký quỹ có thể bị đóng băng để xử lý |

Lalamove `COMPLETED` mới là đã giao hàng. Đơn mua hàng SecondLife chỉ `COMPLETED` sau khi BUYER xác nhận và hệ thống quyết toán.

### 10.3. Màn hình tracking

- Tiến trình: tạo vận đơn → tài xế nhận đơn → lấy hàng → giao thành công → BUYER xác nhận.
- Mã vận đơn, sản phẩm, tổng tiền đã trả.
- Thông tin tài xế khi có: tên, số điện thoại, biển số, ảnh.
- `photo` rỗng: dùng avatar mặc định.
- Giữ nguyên biển số đã che; không cố tái tạo phần bị che.
- `shareLink` khi có: cho mở link theo dõi; dùng đúng URL trả về.
- Lịch sử sự kiện từ `/events`; không tự tạo thời điểm đã lấy/giao hàng.
- `expectedDeliveryTime` null: hiển thị chưa có dự kiến, không dựng ngày giao giả.
- Nút xác nhận nhận hàng chỉ bật khi đơn đạt điều kiện trong phần 11.

### 10.4. GPS tài xế và bản đồ

```javascript
const tracking = response.data;
const coordinates = tracking.driver?.coordinates;

if (tracking.locationStatus === "AVAILABLE" && coordinates) {
  const latitude = Number(coordinates.lat);
  const longitude = Number(coordinates.lng);
  if (Number.isFinite(latitude) && Number.isFinite(longitude)) {
    updateDriverMarker(latitude, longitude);
  }
} else {
  hideDriverMarker();
  showLocationMessage("Chưa có vị trí tài xế");
}
```

Các hàm cập nhật UI là phần FE cần triển khai. Tọa độ tài xế Lalamove thường là chuỗi; chuyển sang số trước khi đưa vào thư viện bản đồ.

BE lọc tọa độ không hợp lệ, cặp `0,0`, thiếu thời gian hợp lệ, thời gian epoch hoặc nằm trong tương lai. Khi không khả dụng:

```json
{
  "locationStatus": "UNAVAILABLE",
  "driver": {
    "coordinates": null
  }
}
```

Đây là phần response liên quan đến GPS; thông tin tài xế khác vẫn được giữ nếu có.

- Không vẽ marker tại `0,0`.
- Không lấy tọa độ của SELLER/BUYER để giả định đó là vị trí tài xế.
- Hiển thị thời điểm cập nhật vị trí khi có; `AVAILABLE` không tự chứng minh vị trí được cập nhật ngay tại thời điểm mở màn hình.
- Sau khi hoàn thành giao hàng, tọa độ tài xế có thể không còn khả dụng.
- API tracking hiện không trả tuyến đường thực tế hay ETA từ GPS. Nếu cần vẽ đường đi, phải có tích hợp routing riêng; không suy diễn một đường thẳng là hành trình tài xế.

Polling khoảng 10 giây/lần, không request chồng nhau, dừng khi rời màn hình. Hủy request đang chạy và bỏ response cũ khi chuyển sang đơn khác. BE nhận webhook để cập nhật trạng thái tự động khi callback đã cấu hình và truy cập được.

### 10.5. Test Sandbox

Trong Partner Portal chọn đúng Sandbox, tìm `orderCode` và mô phỏng trạng thái. Sau mỗi lần đổi, gọi `/sync` rồi đọc lại tracking/đơn mua hàng.

Sandbox kiểm tra được trạng thái, thông tin tài xế thử nghiệm, lịch sử, đồng bộ và quyết toán. Với tài xế đang trả `0,0`/năm `1970`, không thể xác định tài xế đang ở đâu thực tế.

Để phát triển giao diện bản đồ di chuyển, FE có thể sử dụng dữ liệu mock riêng và ghi rõ **Mô phỏng**. Mock không được thay thế GPS trong response BE hoặc ghi vào dữ liệu đơn thật.

## 11. BUYER xác nhận nhận hàng và quyết toán

```http
GET /api/v1/orders/buyer?page=0&size=10
```

Chỉ bật nút **Đã nhận hàng** khi đơn `DELIVERED` và có `shippingDeliveredAt`.

```http
PUT /api/v1/orders/{orderId}/delivered
```

Không có body. Thành công:

- Đơn mua hàng `COMPLETED`.
- Ký quỹ `RELEASED`.
- SELLER nhận giá sản phẩm sau khi trừ hoa hồng.

Xem kết quả:

```http
GET /api/v1/orders/{orderId}/commission
GET /api/v1/orders/{orderId}/settlement
GET /api/v1/wallets/me
GET /api/v1/wallets/me/transactions?page=0&size=10
```

FE sử dụng `sellerPayout`, `platformCommission` từ BE để hiển thị kết quả, không tự tính thay backend. `settlement` chưa có trước khi quyết toán.

## 12. Quy ước response, ID và xử lý lỗi

| Dữ liệu | Nguồn |
|---|---|
| `postId` | `data.content[].postId` từ danh sách bài đăng |
| `shippingQuoteId` | `data.quoteId` từ API tính phí |
| `orderId` | `id` ở gốc response tạo đơn |
| `shipmentId` | `data.shipmentId` từ API tạo vận đơn hoặc danh sách vận đơn |
| `orderCode` | Mã Lalamove; giữ dạng chuỗi, không chuyển sang JavaScript Number |
| `requestId` | FE sinh UUID cho thao tác tạo đơn/tạo vận đơn |

Các API shipping/onboarding/verification thường bọc `success`, `message`, `data`. API đơn hàng và ví có các response DTO trực tiếp; adapter FE cần xử lý theo từng contract, không áp dụng `response.data` cho mọi API.

- 400: hiển thị lỗi trường từ `errors[]`; nếu `errors` rỗng thì hiển thị `message`, đặc biệt khi không có xe đủ tải trọng/kích thước hoặc tuyến chưa được hỗ trợ; không cho checkout nếu chưa có quote hợp lệ.
- 401: xử lý refresh token/đăng nhập lại theo luồng Auth hiện có.
- 403: thông báo thiếu quyền hoặc không thuộc đơn.
- 409: xung đột trạng thái, báo giá hết hạn/đã dùng, bài đã bán hoặc phí mới tăng; đọc message để xử lý đúng.
- 429: Lalamove vượt hạn mức; giữ dữ liệu form và chờ trước khi thử lại, không tự gọi dồn.
- 502/503/504: lỗi/không khả dụng/timeout của dịch vụ ngoài; giữ dữ liệu form. Với tạo đơn/vận đơn, tuân thủ quy tắc retry và đối soát, tránh tạo thao tác mới khi kết quả chưa rõ.
- Response tạo shipment có thể HTTP 200 nhưng shipment thất bại: kiểm tra `orderCode`, `status`, `lastError`.

## 13. Checklist bàn giao FE

- [ ] Component chọn điểm dùng chung cho địa chỉ lấy và nhận hàng.
- [ ] Hiển thị địa chỉ BE trả; người dùng xác nhận điểm, không gửi địa chỉ tự gõ để ghi đè.
- [ ] Loading, lỗi lookup, quyền vị trí bị từ chối, bỏ response cũ được xử lý.
- [ ] Không còn field địa chỉ GHN trong form/request.
- [ ] Hoàn tất thông tin shop và email trước eKYC.
- [ ] Hiển thị đúng hồ sơ eKYC hiện có và trạng thái xử lý.
- [ ] SELLER khai báo kiện hàng trước tính phí.
- [ ] Dùng `quoteId`, tổng tiền và hạn báo giá của BE.
- [ ] UUID ổn định khi retry thanh toán/tạo vận đơn.
- [ ] Không thông báo tạo vận đơn thành công chỉ dựa vào `success: true`.
- [ ] Tracking phân biệt trạng thái đơn mua hàng và trạng thái nhà vận chuyển.
- [ ] GPS giả/không khả dụng không tạo marker tài xế.
- [ ] Timer/request được dọn khi rời màn hình hoặc đổi đơn.
- [ ] BUYER chỉ xác nhận sau khi nhà vận chuyển đã giao hàng.
- [ ] SELLER thực nhận tiền sau hoa hồng, lấy số liệu từ settlement.
- [ ] Dữ liệu mô phỏng được ghi rõ và tách khỏi dữ liệu thật.
