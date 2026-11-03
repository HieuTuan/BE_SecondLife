# Forward Geocoding qua BE — hướng dẫn FE và Swagger

## Luồng xử lý

FE nhập địa chỉ → BE gọi Geoapify → kiểm tra tọa độ/khu vực → FE cập nhật ghim → người dùng xác nhận.
BE xác định thành phố bằng ranh giới hành chính nội bộ, không tin tên thành phố do provider gửi.
Không cần dependency hoặc migration mới.

## Cấu hình BE

Dùng cấu hình Geoapify hiện có. Không đưa API key vào biến VITE_* của FE.

```dotenv
GEOAPIFY_ENABLED=true
GEOAPIFY_API_KEY=<key-cua-ban>
GEOAPIFY_LANGUAGE=vi
GEOAPIFY_BASE_URL=https://api.geoapify.com/v1/geocode/reverse
GEOAPIFY_TIMEOUT_MS=5000
GEOAPIFY_MAX_RESPONSE_CHARACTERS=1048576
```

Giữ GEOAPIFY_BASE_URL là endpoint reverse. Forward dùng endpoint cố định
https://api.geoapify.com/v1/geocode/search; không đổi base URL reverse thành search.
Nếu không đặt GEOAPIFY_LANGUAGE, BE mặc định vi. Giữ các biến fallback/reverse và cấu hình thành phố khác hiện có.
Khởi động lại BE sau khi đổi cấu hình.
FE dùng VITE_API_BASE_URL hoặc VITE_API_ORIGIN hiện có, mặc định http://localhost:8080.

## API

**GET /api/v1/shipping/geocode?address=...**

- Bắt buộc đăng nhập; BUYER, SELLER và tài khoản đã xác thực khác có thể gọi.
- Query address bắt buộc, không rỗng, tối đa 500 ký tự. BE bỏ khoảng trắng hai đầu.
- FE yêu cầu 4–500 ký tự để tránh tìm quá chung chung.
- Không có JSON body. API key không được trả về FE.
- Provider nhận text, filter=countrycode:vn, lang theo cấu hình, limit=1.

Response dưới đây minh họa cấu trúc; tọa độ, formatted address và placeId thực tế do provider trả về:

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "address": "12 Cầu Giấy, Hà Nội",
    "latitude": 21.0333,
    "longitude": 105.7937,
    "city": "HANOI",
    "countryCode": "VN",
    "placeId": "<place-id-do-provider-tra-ve>"
  },
  "timestamp": "2026-10-31T10:00:00"
}
```

city là enum HANOI, DANANG hoặc HO_CHI_MINH. FE có thể đổi thành nhãn hiển thị.
Kết quả phải có tọa độ hợp lệ, thuộc Việt Nam và thành phố được cấu hình hỗ trợ.

| HTTP | Trường hợp | Xử lý FE |
| --- | --- | --- |
| 200 | Tìm thấy trong khu vực hỗ trợ | Cập nhật ghim/địa chỉ, yêu cầu xác nhận lại. |
| 400 | Thiếu/rỗng/address dài hơn 500 ký tự | Hiển thị lỗi validation. |
| 400 | Không có kết quả, provider lỗi/mất kết nối hoặc cấu hình key không dùng được | Hiển thị lỗi thân thiện, giữ vị trí đã chọn. |
| 400 | Ngoài khu vực phục vụ | Chọn địa chỉ khác. |
| 401/403 | Chưa đăng nhập, phiên hết hạn hoặc bị từ chối | Xử lý đăng nhập qua API client hiện có. |

Ví dụ lỗi không có kết quả/provider thất bại:

```json
{
  "success": false,
  "message": "Không tìm thấy tọa độ GPS cho địa chỉ này. Vui lòng nhập chi tiết hơn.",
  "path": "/api/v1/shipping/geocode",
  "errors": [],
  "timestamp": "2026-10-31T10:00:00"
}
```

## Cách FE hoạt động và fallback

shippingService.geocode(address, signal) gọi BE qua API client có JWT/refresh token.
LalamoveAddressPicker gửi khi bấm Tìm vị trí hoặc Enter, không gọi theo từng ký tự.

- Timeout FE 12 giây; BE dùng timeout Geoapify hiện có.
- Sửa/xóa query hoặc chọn ghim khác sẽ hủy request cũ; phản hồi cũ không được ghi đè vị trí mới.
- Forward thành công không gọi reverse thêm lần nữa.
- Forward lỗi giữ nguyên vị trí trước đó. Nếu đã có vị trí hợp lệ, người dùng vẫn có thể xác nhận vị trí ấy.
- Fallback: nhập địa chỉ chi tiết hơn hoặc chọn ghim thủ công. Ghim mới gọi API reverse của BE bên dưới.
- Reverse giữ cơ chế fallback phía server hiện có. FE không gọi trực tiếp Nominatim/Geoapify.
- Reverse lỗi sẽ xóa địa chỉ của ghim mới và khóa xác nhận; không dùng địa chỉ cũ cho tọa độ mới, không tự gán Hà Nội.
- Xác nhận cũng bị khóa khi đang tìm kiếm, tra địa chỉ hoặc lấy GPS thiết bị.
- Người dùng cần kiểm tra ghim/số nhà trước khi xác nhận. API lưu địa chỉ BE vẫn kiểm tra lại vị trí theo luồng hiện có.

POST /api/v1/shipping/reverse-geocode:

```json
{
  "latitude": 21.0333,
  "longitude": 105.7937
}
```

## Test Swagger

1. Khởi động BE, mở http://localhost:8080/swagger-ui/index.html.
2. Đăng nhập và điền access token vào Authorize.
3. Trong Shipping mở GET /api/v1/shipping/geocode → Try it out.
4. Nhập trực tiếp 12 Cầu Giấy, Hà Nội vào address → Execute. Không dán JSON vào query; Swagger tự encode.
5. Kiểm tra success=true, tọa độ hữu hạn, countryCode=VN, thành phố phù hợp.
6. Thử địa chỉ chi tiết tại Đà Nẵng và TP. Hồ Chí Minh.

| Test | Input/thao tác | Mong đợi |
| --- | --- | --- |
| Địa chỉ chi tiết | 12 Cầu Giấy, Hà Nội | 200 nếu provider tìm được điểm hỗ trợ. |
| Khoảng trắng | Thêm dấu cách hai đầu | BE trim trước khi tìm. |
| Unicode/ký tự đặc biệt | Tên địa điểm có tiếng Việt, + và & | Encode đúng, không phát sinh query parameter khác. |
| Rỗng | Chuỗi trắng | 400, không gọi provider. |
| Thiếu parameter | Bỏ hẳn address bằng curl/DevTools | 400; Swagger UI bắt buộc trường nên không gửi được ca thiếu parameter. |
| Quá dài | 501 ký tự | 400, không gọi provider. |
| Không tìm được | Chuỗi ngẫu nhiên | 400 nếu provider không có kết quả; provider có thể trả gợi ý, cần kiểm tra ghim. |
| Ngoài khu vực | Địa chỉ chi tiết ở Cần Thơ | 400 nếu tọa độ ngoài ranh giới các thành phố được hỗ trợ. |
| Chưa đăng nhập | Xóa Authorize | Bị từ chối, không gọi provider. |
| Key sai/mất kết nối | Chỉ thử trong môi trường test rồi khôi phục | 400 thân thiện, không lộ key hoặc URL upstream. |

## Test FE web/mobile

1. Đăng nhập, mở form có LalamoveAddressPicker, mở DevTools → Network.
2. Tìm địa chỉ chi tiết: request đến BE /api/v1/shipping/geocode; không có request browser đến Nominatim search.
3. Kiểm tra ghim, địa chỉ, thành phố; bấm xác nhận và kiểm tra tọa độ.
4. Tìm lỗi sau khi đã chọn điểm hợp lệ: điểm cũ được giữ nguyên.
5. Throttle mạng, tìm rồi chọn ghim khác: phản hồi tìm kiếm cũ không được ghi đè ghim mới.
6. Throttle mạng, tìm rồi sửa/xóa query: kết quả cũ bị hủy.
7. Giả lập lỗi reverse rồi chọn ghim mới: địa chỉ bị xóa, nút xác nhận khóa.
8. Kiểm tra cùng luồng trên viewport mobile.

Kiểm thử BE tự động dùng provider giả lập, không truy cập database hoặc tiêu quota:

```powershell
mvn "-Dtest=ShippingGeocodingControllerTest,ForwardGeocodingServiceTest,ReverseGeocodingServiceTest,GeocodingPropertiesTest" test
```

FE: npm run lint và npm run build. Kiểm thử Chromium bổ sung dùng API giả lập để kiểm tra lỗi và phản hồi đến sai thứ tự.
Chưa kiểm tra live key/quota Geoapify; cần chạy Swagger với cấu hình thật để xác nhận kết nối của môi trường.

Tham khảo: [Geoapify Forward Geocoding API](https://apidocs.geoapify.com/docs/geocoding/forward-geocoding/).
