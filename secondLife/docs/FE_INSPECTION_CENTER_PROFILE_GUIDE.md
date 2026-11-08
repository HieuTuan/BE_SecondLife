# FE — Hồ sơ và địa chỉ trung tâm kiểm định

## 1. Phạm vi và luồng dữ liệu

BE hỗ trợ tài khoản có role `INSPECTION_CENTER` tự xem/cập nhật tên trung tâm, hotline, địa chỉ và tọa độ. Áp dụng cả tài khoản trung tâm được seed. Không cần tạo lại tài khoản, sửa `.env` hoặc chạy lại database.

Thông tin lưu trong `user_profiles` của tài khoản đang đăng nhập. Không có bảng trung tâm trùng dữ liệu. Tên trung tâm dùng `user_profiles.full_name`, hotline dùng `phone`; địa chỉ và GPS dùng các cột hiện có. Đổi tên trung tâm cũng đổi tên hiển thị của tài khoản và tên hub của các kỹ thuật viên thuộc trung tâm.

Luồng mới:

1. Trung tâm đăng nhập và mở **Thông tin trung tâm**.
2. FE đọc hồ sơ; nếu thiếu địa chỉ/GPS thì hiển thị cảnh báo cần cấu hình.
3. Trung tâm điền tên, hotline, địa chỉ; tìm kiếm trên bản đồ và xác nhận đúng điểm tiếp nhận hàng.
4. FE lưu toàn bộ form bằng `PUT /api/v1/inspector-center/profile`.
5. Khi chọn trung tâm, FE lấy danh sách từ `GET /api/v1/inspection/centers` để hiển thị tên, địa chỉ, hotline và GPS thật.
6. Khi seller của đơn hoặc STAFF/ADMIN tạo chuyến `SELLER_TO_CENTER`, BE lấy điểm đến từ hồ sơ trung tâm đã gắn và dùng tuyến đã chốt nếu đơn có báo giá.

**Phân biệt hai loại tiền:**

| Phần | Hành vi sau thay đổi |
|---|---|
| GPS và địa chỉ trung tâm | Lưu được, chỉnh sửa được; dùng làm điểm đến của chuyến mới gắn trung tâm |
| Phí chuyến Lalamove khi tạo vận đơn | Hãng báo giá theo tuyến và kiện hàng thật mà BE gửi; giá được lưu trong `quotedFee` |
| `POST /api/v1/inspection/quote` — gói `AT_CENTER` | Lấy hai quotation Lalamove cho seller→center và center→seller; lưu `shippingQuote.id` và thời hạn. Xem `FE_INSPECTION_LALAMOVE_QUOTE_GUIDE.md` |
| Phí di chuyển KTV tại nhà | Luồng hiện có tiếp tục đọc GPS của trung tâm từ `user_profiles` |

Giá chiều gửi là báo giá Lalamove tại thời điểm lấy giá. Chiều hoàn là khoản dự phòng từ quotation riêng; giá lúc đặt chuyến trong tương lai có thể thay đổi. Buyer cần gửi `inspectionShippingQuoteId` khi tạo yêu cầu `AT_CENTER`; xem hướng dẫn báo giá mới.

## 2. Phân quyền

| API | Quyền |
|---|---|
| `GET /api/v1/inspector-center/profile` | `INSPECTION_CENTER` + `PROFILE_READ_SELF` |
| `PUT /api/v1/inspector-center/profile` | `INSPECTION_CENTER` + `PROFILE_UPDATE_SELF` |
| `GET /api/v1/inspection/centers` | Đăng nhập, theo security rule hiện có |
| `POST /api/v1/inspection-orders/{inspectionId}/shipments` | Seller của đơn được tạo `SELLER_TO_CENTER`. STAFF/ADMIN có quyền tạo các chặng từ trung tâm theo trạng thái đơn. |

Profile API không nhận `userId`/`centerId` để chọn tài khoản cập nhật; BE lấy ID từ JWT. Trung tâm chỉ sửa chính mình. Role INSPECTOR, BUYER, SELLER, STAFF hoặc ADMIN riêng lẻ không được dùng API hồ sơ tự quản của trung tâm.

Mỗi request gửi `Authorization: Bearer <access_token>`. Không lưu mật khẩu seed trong mã FE. Dùng API client/auth interceptor hiện có.

## 3. GET hồ sơ

```http
GET /api/v1/inspector-center/profile
```

Ví dụ sau khi đã cấu hình; tên, số điện thoại, địa chỉ và GPS dưới đây chỉ là dữ liệu minh họa, **không phải địa chỉ trung tâm được xác minh**:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "id": "7f33f576-d71b-4e90-bf8c-59a446a8a9ab",
    "name": "Trung tâm Quận 7",
    "phone": "02838229999",
    "province": "Thành phố Hồ Chí Minh",
    "district": "Quận 7",
    "ward": "Tân Phong",
    "streetAddress": "1059 Nguyễn Văn Linh",
    "address": "1059 Nguyễn Văn Linh, Tân Phong, Quận 7, Thành phố Hồ Chí Minh",
    "latitude": 10.73,
    "longitude": 106.705,
    "shippingAddressConfigured": true,
    "updatedAt": "2026-11-06T06:00:00Z"
  }
}
```

`ApiResponse` còn có timestamp theo quy ước chung. Hồ sơ mới seed có thể có các trường địa chỉ/contact/GPS bằng `null`, `shippingAddressConfigured=false`; GET vẫn thành công. FE không tự thay null bằng GPS trung tâm thành phố hoặc địa chỉ demo.

`address` là chuỗi BE ghép từ `streetAddress`, `ward`, `district` nếu có và `province`. Dùng chuỗi này để hiển thị thẻ như ảnh mẫu. Chỉ gửi các trường thành phần khi cập nhật, không gửi lại `address` như một trường chỉnh sửa riêng.

## 4. PUT cập nhật toàn bộ form

```http
PUT /api/v1/inspector-center/profile
Content-Type: application/json
```

```json
{
  "name": "Trung tâm Quận 7",
  "phone": "02838229999",
  "province": "Thành phố Hồ Chí Minh",
  "district": "Quận 7",
  "ward": "Tân Phong",
  "streetAddress": "1059 Nguyễn Văn Linh",
  "latitude": 10.73,
  "longitude": 106.705
}
```

**Trước khi dùng thật, thay địa chỉ, hotline và cả hai tọa độ bằng thông tin đúng của trung tâm.** Không suy ra vị trí chính xác từ cặp GPS minh họa.

Response 200 trả hồ sơ đã lưu cùng hình dạng ở mục 3. PUT là cập nhật đầy đủ: kể cả chỉ sửa hotline, FE phải gửi lại các trường bắt buộc của form.

| Field | Kiểu JSON | Quy tắc |
|---|---|---|
| `name` | string | Bắt buộc, 2–100 ký tự sau khi chuẩn hóa khoảng trắng đầu/cuối |
| `phone` | string | Bắt buộc; số Việt Nam bắt đầu `0`, `84` hoặc `+84`, theo regex `(?:0|\+?84)[0-9]{9,10}`; không gửi khoảng trắng/dấu ngoặc |
| `province` | string | Bắt buộc, tối đa 100 ký tự; phải tương ứng thành phố xác định từ GPS |
| `district` | string/null | Tùy chọn, tối đa 100 ký tự; cho phép bỏ trống khi dùng địa chỉ hành chính hai cấp |
| `ward` | string | Bắt buộc, tối đa 100 ký tự |
| `streetAddress` | string | Bắt buộc, tối đa 255 ký tự; số nhà, đường, tòa nhà; không lặp lại cả phường/tỉnh |
| `latitude` | number | Bắt buộc, -90 đến 90 |
| `longitude` | number | Bắt buộc, -180 đến 180 |

Địa chỉ ghép phải dài tối đa 500 ký tự. GPS phải nằm trong vùng hành chính Việt Nam được dữ liệu bản đồ BE nhận diện và trong `app.shipping.catalog.allowed-cities` (mặc định Hà Nội, Đà Nẵng, Thành phố Hồ Chí Minh). Không chỉ kiểm tra một hình chữ nhật giới hạn tọa độ. FE nên hiển thị tên tỉnh/thành đúng thay vì tự suy diễn từ vị trí gần nhất.

BE kiểm tra GPS và tỉnh/thành, nhưng không tự xác minh số nhà hoặc việc trung tâm sở hữu địa chỉ đó. Người quản lý phải xác nhận pin đúng cửa nhận hàng.

`shippingAddressConfigured=true` chỉ có nghĩa hồ sơ đủ điều kiện làm địa chỉ vận chuyển. Nó **không** xác nhận hãng hỗ trợ mọi tuyến đến địa chỉ đó, đã có tài xế, hay đã có giá cước. Hãng vẫn có thể từ chối tuyến hoặc gặp lỗi kỹ thuật khi báo giá.

Các trường không thuộc form như mật khẩu, email, role, avatar và giới tính không bị cập nhật bởi API này.

## 5. Chọn địa chỉ/GPS trên FE

Tái sử dụng API geocoding BE:

```http
GET /api/v1/shipping/geocode?address=<chuỗi địa chỉ đã URL-encode>
```

```http
POST /api/v1/shipping/reverse-geocode
Content-Type: application/json
```

```json
{
  "latitude": 10.73,
  "longitude": 106.705
}
```

Các API geocoding trả `data.address`, `data.latitude`, `data.longitude`, `data.city` và thông tin khác theo contract hiện có; không giả định chúng đã trả riêng province/district/ward. FE vẫn cần form nhập các thành phần còn thiếu và hiển thị địa chỉ chuẩn hóa để người dùng đối chiếu.

- Có thể tái sử dụng `src/components/common/LalamoveAddressPicker.tsx`; không gọi thẳng Nominatim từ trình duyệt.
- Bản đồ có thể có tâm mặc định để xem, nhưng tâm mặc định **không được coi là vị trí trung tâm đã xác nhận**.
- Nếu người dùng đổi địa chỉ sau khi xác nhận vị trí, yêu cầu chọn/xác nhận lại GPS để tránh lưu địa chỉ mới cùng tọa độ cũ.
- Nếu tải geocoding lỗi, hiển thị lỗi và cho thử lại/chọn pin thủ công; không tự điền GPS giả.
- Không cần gọi reverse geocode lại chỉ để sửa hotline ở địa chỉ không đổi.
- Khi bấm Lưu, khóa nút trong thời gian request; sau thành công dùng response BE làm state mới, tải lại danh sách trung tâm nếu đang cache.

## 6. Danh sách trung tâm và tạo chuyến

### Danh sách lựa chọn

```http
GET /api/v1/inspection/centers
```

Các trường cũ `id`, `name` được giữ; bổ sung:

```json
{
  "success": true,
  "data": [
    {
      "id": "7f33f576-d71b-4e90-bf8c-59a446a8a9ab",
      "name": "Trung tâm Quận 7",
      "phone": "02838229999",
      "address": "1059 Nguyễn Văn Linh, Tân Phong, Quận 7, Thành phố Hồ Chí Minh",
      "latitude": 10.73,
      "longitude": 106.705,
      "shippingAddressConfigured": true
    }
  ]
}
```

Trung tâm hoạt động nhưng chưa đủ địa chỉ vẫn có trong danh sách, cờ bằng false. FE hiển thị “Trung tâm chưa cấu hình địa chỉ nhận hàng” và không cho tiếp tục chức năng giao hàng đến trung tâm đó.

### Vận đơn tới trung tâm đã gắn với đơn

Đăng nhập bằng seller của đơn, STAFF hoặc ADMIN và dùng **ID đơn kiểm định**:

```http
POST /api/v1/inspection-orders/{inspectionId}/shipments
Content-Type: application/json
```

```json
{
  "requestId": "a857eea1-7551-43cb-99d7-837ce031fb64",
  "leg": "SELLER_TO_CENTER"
}
```

- Đơn phải thuộc loại `AT_CENTER` và trạng thái hợp lệ theo luồng vận chuyển.
- Với đơn có `inspectionCenterId`, bỏ `toAddress`; BE tự đọc hồ sơ đúng trung tâm của đơn. Không lấy địa chỉ demo trong FE làm điểm đến.
- Nếu FE cũ còn truyền `toAddress`, tên, hotline và GPS phải khớp hồ sơ trung tâm; khác thì trả 409, không gọi hãng đặt chuyến.
- Trung tâm thiếu thông tin trả 409; BE không dùng địa chỉ tùy ý từ FE để thay cho hồ sơ thiếu.
- Với đơn cũ chưa gắn trung tâm, người có quyền tạo chặng đầu cung cấp `toAddress` đã xác minh. Điểm lấy hàng lấy từ `fromAddress` hoặc địa chỉ pickup đã lưu của seller.
- Shipment được lưu cả địa chỉ và GPS tại thời điểm tạo. Đổi hồ sơ không đổi vận đơn đã tồn tại; gửi lại cùng `requestId` dùng payload cũ, không tạo thêm chuyến. Nếu vẫn gửi `toAddress` khi retry, phải là địa chỉ của request/vận đơn cũ; cách đơn giản là luôn bỏ `toAddress` với luồng mới.
- Chuyến hoàn từ trung tâm về seller tiếp tục lấy điểm đi từ vận đơn seller→center trước đó; không đổi sang hồ sơ trung tâm mới sau khi hàng đã gửi.

API tạo vận đơn có thể gọi hãng đặt chuyến thật. Chỉ kiểm thử phần này bằng môi trường Sandbox đã cấu hình và đơn test thích hợp. Không gọi API tạo chuyến chỉ để hiển thị phí dự kiến.

## 7. Giao diện đề nghị

Thêm mục **Thông tin trung tâm** trong portal `/inspector-center`.

Thẻ đầu trang theo ảnh mẫu:

```text
[Biểu tượng vị trí] {name}                    Hotline: {phone}
                    {address}                [Chỉnh sửa thông tin]
```

Form gồm tên trung tâm, hotline, tỉnh/thành, phường/xã, quận/huyện tùy chọn, số nhà/đường, bản đồ, hai trường GPS và nút Lưu. Dùng dữ liệu từ API, không hardcode “Quận 7 Flagship”, số hotline hoặc địa chỉ minh họa.

Hiển thị trạng thái chưa cấu hình và trường còn thiếu; không gọi dữ liệu trống là lỗi hệ thống. Chỉ hiển thị form sau khi tải hồ sơ thành công để tránh ghi đè bằng form rỗng khi mạng lỗi.

## 8. Test thực tế trên Swagger

1. Build/restart BE đã có thay đổi. Đăng nhập tài khoản trung tâm hiện có và Authorize bằng JWT.
2. GET profile: tài khoản seed chỉ có tên → 200, cờ `shippingAddressConfigured=false`.
3. PUT profile với JSON ở mục 4, thay bằng địa chỉ và GPS thực tế. Mong đợi 200 và cờ true.
4. GET profile lại hoặc đăng xuất/đăng nhập: thông tin vẫn còn. Kiểm tra `/api/v1/users/me` cũng phản ánh tên, phone, địa chỉ và GPS vừa lưu.
5. Đổi hotline và GPS hợp lệ; PUT lại → GET thấy thông tin mới. Thử `district=null` → vẫn hợp lệ.
6. Bỏ latitude, longitude, name, phone hoặc ward → 400. Nhập latitude=91 hoặc longitude=181 → 400.
7. GPS=(0,0), hotline sai định dạng hoặc tỉnh/thành không khớp GPS → 400; không ghi thông tin không hợp lệ.
8. Dùng JWT BUYER/SELLER/INSPECTOR/STAFF/ADMIN riêng lẻ gọi profile → 403. Không có JWT → 401. Trung tâm B cập nhật chỉ ảnh hưởng B.
9. GET `/api/v1/inspection/centers`: kiểm tra các trường mới theo từng trung tâm; dữ liệu thiếu trả false.
10. Trên đơn kiểm định test `AT_CENTER` có gắn đúng trung tâm, STAFF/ADMIN tạo chuyến bằng JSON không có `toAddress`. Mong đợi BE báo giá/đặt chuyến theo GPS seller→center thật, tùy kết quả hãng.
11. Truyền `toAddress` của trung tâm khác → 409. Trung tâm chưa cấu hình hồ sơ → 409.
12. Sau khi đã tạo chuyến, sửa hồ sơ trung tâm rồi gửi lại cùng requestId ban đầu không có `toAddress` → giữ vận đơn và tuyến cũ; không đặt chuyến thứ hai.

Lỗi thường gặp:

| HTTP | Ý nghĩa / FE xử lý |
|---|---|
| 400 | Field không hợp lệ hoặc GPS/tỉnh thành không phù hợp; hiển thị `message` và `errors` theo BE |
| 401 | Chưa đăng nhập/token không hợp lệ |
| 403 | Không có role/quyền phù hợp hoặc tài khoản bị vô hiệu hóa |
| 409 khi tạo chuyến | Thiếu hồ sơ, điểm đến không khớp, requestId bị dùng với dữ liệu khác, hoặc trạng thái đơn không cho phép |
| 422 từ hãng | Không có phương tiện phù hợp cho tuyến; không tự thay trung tâm/địa chỉ |
| 429/502/503/504 | Lỗi giới hạn tần suất/kỹ thuật từ tích hợp; hiển thị lỗi, không tự coi phí là 0 |

## 9. Prompt cho AI sửa FE

```text
Đọc docs/FE_INSPECTION_CENTER_PROFILE_GUIDE.md của BE và code FE hiện tại.
Thêm màn hình “Thông tin trung tâm” trong portal /inspector-center để tài khoản INSPECTION_CENTER quản lý tên, hotline, địa chỉ và GPS.

API:
- GET /api/v1/inspector-center/profile
- PUT /api/v1/inspector-center/profile
- GET /api/v1/inspection/centers đã bổ sung phone, address, latitude, longitude, shippingAddressConfigured; giữ id và name.

PUT gửi đủ name, phone, province, district (optional), ward, streetAddress, latitude, longitude; không gửi userId/centerId.
Tái sử dụng API client xác thực hiện tại, DTO TypeScript rõ ràng và geocoding BE. Có thể tái sử dụng LalamoveAddressPicker, nhưng không lưu tâm bản đồ mặc định thành GPS của trung tâm. Người dùng phải xác nhận pin đúng; địa chỉ đổi thì xác nhận lại vị trí.
Thẻ trung tâm hiển thị tên, địa chỉ, hotline từ API theo mẫu; có nút chỉnh sửa. Không hardcode dữ liệu demo. Hồ sơ thiếu thông tin hiển thị cảnh báo và form để hoàn thiện.
Xử lý loading/error/saving, chống bấm lưu lặp, validate field và hiển thị lỗi BE. Sau khi lưu, dùng response và cập nhật cache/danh sách trung tâm liên quan.
Khi seller của đơn hoặc STAFF/ADMIN tạo SELLER_TO_CENTER cho đơn có inspectionCenterId, bỏ toAddress để BE lấy địa chỉ trung tâm đã lưu. Nếu đơn có báo giá đã chốt, bỏ fromAddress hoặc gửi địa chỉ khớp báo giá. Các chặng từ trung tâm do STAFF/ADMIN tạo theo trạng thái đơn.
Không dùng API tạo vận đơn như một API preview giá: nó có thể đặt chuyến thật.
Đọc thêm FE_INSPECTION_LALAMOVE_QUOTE_GUIDE.md: POST /api/v1/inspection/quote trả quotation Lalamove hai chiều; truyền inspectionShippingQuoteId khi tạo yêu cầu AT_CENTER và xử lý hết hạn/đổi tuyến bằng việc lấy giá mới.
Không sửa các chức năng khác. Chạy kiểm tra TypeScript/build và báo rõ file đã sửa, lỗi nền nếu có.
```
