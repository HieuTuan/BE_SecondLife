# FE: Giải thích phương tiện thay thế và tuyến Lalamove chưa hỗ trợ

## 1. Phạm vi thay đổi BE

API giữ nguyên: `POST /api/v1/shipping/quotes`, có Bearer token và quyền BUYER hoặc SELLER như trước.

BE tiếp tục chọn xe có tải trọng nhỏ nhất đủ chở kiện hàng theo danh mục tại điểm lấy hàng. Nếu hãng từ chối xe đó bằng `ERR_OUT_OF_SERVICE_AREA` hoặc `ERR_INVALID_SERVICE_TYPE`, BE thử xe phù hợp tiếp theo và dừng ở báo giá đầu tiên thành công. Đây không phải thuật toán tìm xe có giá rẻ nhất.

FE không cần gọi hai lần, không cần xin quyền đổi xe, không thêm `allowVehicleUpgrade` hay gửi `serviceType`. Người mua vẫn xác nhận đặt hàng theo luồng hiện có, sau khi nhìn thấy đầy đủ phương tiện và phí.

Thay đổi mới:

- HTTP 200: giữ các trường báo giá cũ, bổ sung `data.vehicleSelection` để giải thích việc chọn xe.
- HTTP 422, `code=NO_SUITABLE_VEHICLE_FOR_ROUTE`: tất cả xe phù hợp đã thử đều bị hãng từ chối vì ngoài vùng phục vụ. Không có báo giá sử dụng được.
- HTTP 429/502/503/504: giữ cách xử lý lỗi kỹ thuật riêng; không được diễn giải thành tuyến không được phục vụ.
- Không thêm bảng/cột DB. Chẩn đoán chọn xe được lưu cùng JSON payload của báo giá. Không thay đổi cách thu tiền, đặt chuyến hoặc tiêu thụ báo giá.

## 2. Request giữ nguyên

```json
{
  "postId": "ccead440-2d5e-4bfc-a4ed-ebaceab15e48",
  "deliveryAddress": {
    "name": "Người nhận",
    "phone": "0912345678",
    "latitude": 10.870004,
    "longitude": 106.684601
  }
}
```

Nếu có thương lượng được BE chấp nhận, gửi thêm UUID `negotiationId` hợp lệ theo luồng cũ.

## 3. HTTP 200: Có báo giá, có thể tiếp tục đặt hàng

Ví dụ minh họa báo giá xe máy bị từ chối và có xe bán tải thay thế. UUID, giá và thời hạn dưới đây chỉ minh họa; FE phải dùng giá trị mới BE trả về.

```json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "quoteId": "78a9ea79-b5d1-4593-bd71-d6e5dffae681",
    "leg": "SELLER_TO_BUYER",
    "shippingFee": 6164800,
    "productPrice": 1400000,
    "totalPayable": 7564800,
    "insuranceValue": 0,
    "expiresAt": "2026-11-05T03:00:00Z",
    "expectedDeliveryTime": null,
    "provider": "LALAMOVE",
    "serviceType": "LD_PICKUP_TRUCK",
    "providerQuotationId": "3621308755495047445",
    "shippingWeight": 1700,
    "shippingLength": 2,
    "shippingWidth": 2,
    "shippingHeight": 4,
    "vehicleSelection": {
      "preferredServiceType": "MOTORCYCLE",
      "selectedServiceType": "LD_PICKUP_TRUCK",
      "fallbackUsed": true,
      "reasonCode": "PREFERRED_VEHICLE_OUT_OF_SERVICE_AREA",
      "providerErrorCode": "ERR_OUT_OF_SERVICE_AREA",
      "pickupCity": "Thành phố Hà Nội",
      "deliveryCity": "Thành phố Hồ Chí Minh",
      "distanceKm": 1498.6,
      "message": "Kiện hàng phù hợp với xe máy, nhưng Lalamove không hỗ trợ loại xe này trên tuyến từ Thành phố Hà Nội đến Thành phố Hồ Chí Minh. Hệ thống đã chọn xe bán tải thay thế. Vui lòng kiểm tra phí vận chuyển và địa chỉ trước khi đặt hàng."
    }
  },
  "timestamp": "2026-11-05T09:00:00"
}
```

### Ý nghĩa các trường bổ sung

| Trường | Kiểu | Ý nghĩa |
| --- | --- | --- |
| `preferredServiceType` | string | Xe đủ tải/kích thước được BE thử đầu tiên; không mặc định luôn là MOTORCYCLE. |
| `selectedServiceType` | string | Xe hãng đã báo giá thành công, trùng `data.serviceType`. |
| `fallbackUsed` | boolean | `true` khi BE đã chuyển khỏi xe ưu tiên sang một xe khác. |
| `reasonCode` | string, có thể thiếu | Lý do xe ưu tiên không được chọn; xem bảng bên dưới. |
| `providerErrorCode` | string, có thể thiếu | Mã Lalamove từ chối xe ưu tiên; không phải lỗi của báo giá thành công cuối cùng. |
| `pickupCity` | string, có thể thiếu | Thành phố từ tọa độ xuất phát thực tế của báo giá. Với `CENTER_TO_BUYER`, đây là trung tâm kiểm định. |
| `deliveryCity` | string, có thể thiếu | Thành phố từ tọa độ nhận thực tế của báo giá. |
| `distanceKm` | number, có thể thiếu | Quãng đường hãng trả về, đổi từ m sang km khi cần. Không phải khoảng cách FE tự tính. |
| `message` | string, có thể thiếu | Giải thích do BE tạo từ phản hồi hãng và dữ liệu tuyến thực tế. Chỉ có khi chuyển xe. |

| `reasonCode` | Cách hiển thị |
| --- | --- |
| `PREFERRED_VEHICLE_OUT_OF_SERVICE_AREA` | Xe ưu tiên đủ chở kiện hàng, nhưng hãng từ chối phục vụ loại xe đó trên tuyến đã chọn. Hiển thị nguyên `message`. |
| `PREFERRED_VEHICLE_UNAVAILABLE` | Hãng trả `ERR_INVALID_SERVICE_TYPE` cho xe ưu tiên; không được suy diễn thành tuyến ngoài vùng phục vụ. Hiển thị nguyên `message`. |

Nếu xe đầu tiên thành công, `fallbackUsed=false`; `reasonCode`, `providerErrorCode`, `message` được bỏ khỏi JSON. Có thể vẫn hiển thị tên tuyến và quãng đường. Nếu metadata không có (tương thích dữ liệu/phiên bản cũ), không tự dựng lý do chuyển xe.

Các tên thành phố lấy từ dữ liệu ranh giới hành chính đang có trên BE, dựa vào GPS. Không thêm lời gọi Geoapify hoặc hãng chỉ để tạo thông báo. Nếu không xác định được thành phố thì bỏ trường đó và dùng thông báo tuyến tổng quát. Không điền mặc định Hà Nội hoặc TP.HCM.

### Bố trí trong CheckoutModal

Trong thẻ báo giá hiện có:

1. Giữ phương tiện thực tế, cân nặng/kích thước và giá từ response.
2. Nếu `vehicleSelection.fallbackUsed===true`, đặt khung thông báo màu vàng, tiêu đề **Lý do chọn phương tiện thay thế**.
3. Nội dung ưu tiên `vehicleSelection.message`; có thể bổ sung dòng tuyến và quãng đường nếu có dữ liệu.
4. Vẫn hiển thị phí và tổng tiền. Đây là báo giá hợp lệ, không phải lỗi chặn thanh toán.
5. Không thêm bước gọi API xin đổi xe. Nút đặt hàng vẫn tuân thủ hạn báo giá, trạng thái loading và các điều kiện hiện có.

Không gắn nhãn "rẻ nhất", không suy đoán phí chuẩn của xe máy, không dùng trạng thái bài đăng hay mô tả sản phẩm để bịa lý do chuyển xe. Xe lớn được chọn ngay từ đầu vì kiện lớn không phải trường hợp fallback.

## 4. HTTP 422: Không có xe phù hợp phục vụ tuyến đã kiểm tra

Chỉ trả mã này nếu mọi xe đủ tải/kích thước đã thử đều nhận `ERR_OUT_OF_SERVICE_AREA`.

```json
{
  "success": false,
  "message": "Không tìm được phương tiện Lalamove phù hợp phục vụ tuyến từ Thành phố Hà Nội đến Thành phố Hồ Chí Minh. Vui lòng kiểm tra lại địa chỉ lấy hàng và địa chỉ nhận hàng.",
  "path": "/api/v1/shipping/quotes",
  "errors": [],
  "timestamp": "2026-11-05T09:00:00",
  "code": "NO_SUITABLE_VEHICLE_FOR_ROUTE",
  "details": {
    "preferredServiceType": "MOTORCYCLE",
    "attemptedServiceTypes": ["MOTORCYCLE", "LD_PICKUP_TRUCK"],
    "pickupCity": "Thành phố Hà Nội",
    "deliveryCity": "Thành phố Hồ Chí Minh",
    "providerErrorCode": "ERR_OUT_OF_SERVICE_AREA"
  }
}
```

FE xử lý theo `status===422 && code==='NO_SUITABLE_VEHICLE_FOR_ROUTE'`:

- Xóa báo giá cũ, `quoteId` cũ, thông tin xe và thông báo fallback cũ.
- Hiển thị `message` của BE. Không đổi thành khẳng định chung rằng Lalamove chỉ giao cùng thành phố.
- Không hiển thị phí 0đ như báo giá miễn phí; thay bằng **Chưa có báo giá vận chuyển**.
- Chặn đặt hàng khi chưa có báo giá mới hợp lệ.
- Cho người dùng kiểm tra/chỉnh địa chỉ rồi lấy báo giá mới. Chỉ đề xuất hãng khác nếu dự án thực sự có tích hợp.

Code không có nghĩa tất cả phương tiện trên toàn mạng lưới Lalamove đều không hỗ trợ. Nó mô tả danh sách xe đủ chở kiện hàng mà BE đã kiểm tra tại điểm lấy hàng.

## 5. Lỗi kỹ thuật và dữ liệu đầu vào

| Tình huống | HTTP hiện tại | Xử lý FE |
| --- | --- | --- |
| Hãng giới hạn tần suất | 429 | Chờ rồi thử lại, không tự lặp liên tục. |
| Hãng timeout | 504 | Thông báo tạm chưa lấy được giá, cho thử lại. |
| Hãng không khả dụng hoặc cấu hình không bật | 502/503 | Thông báo kỹ thuật, không khẳng định tuyến không hỗ trợ. |
| Sai xác thực hãng, response lỗi hoặc danh mục service không hợp lệ chưa giải quyết được | 502 | Thông báo lỗi dịch vụ; không diễn giải thành ngoài vùng phục vụ. |
| Kiện hàng không đủ dữ liệu hoặc không có xe nào đủ tải/kích thước | 400 | Giữ xử lý validation hiện có; không gắn `NO_SUITABLE_VEHICLE_FOR_ROUTE`. |

Nếu quá trình thử xe có lỗi kỹ thuật, BE dừng ngay. Nếu đã thử hết mà còn lỗi `ERR_INVALID_SERVICE_TYPE`, BE trả lỗi hãng 502, không kết luận mọi xe đều ngoài vùng phục vụ.

Mọi lỗi lấy báo giá đều phải làm mất hiệu lực báo giá đang hiển thị trong FE. Khi đổi post, thương lượng hoặc địa chỉ, xóa giá cũ ngay; bỏ qua response cũ đến muộn bằng AbortController hoặc số thứ tự request. BE vẫn kiểm tra quyền sở hữu, thời hạn, giá sản phẩm và thông số kiện hàng khi sử dụng báo giá.

## 6. Kiểu TypeScript gợi ý

```ts
export interface ShippingVehicleSelection {
  preferredServiceType: string;
  selectedServiceType: string;
  fallbackUsed: boolean;
  reasonCode?: 'PREFERRED_VEHICLE_OUT_OF_SERVICE_AREA'
    | 'PREFERRED_VEHICLE_UNAVAILABLE'
    | (string & {});
  providerErrorCode?: string;
  pickupCity?: string;
  deliveryCity?: string;
  distanceKm?: number;
  message?: string;
}

// Bổ sung vào ShippingQuoteResponseDto đang có, giữ các trường cũ.
// vehicleSelection?: ShippingVehicleSelection | null;

export interface ShippingRouteErrorDetails {
  preferredServiceType: string;
  attemptedServiceTypes: string[];
  pickupCity?: string;
  deliveryCity?: string;
  providerErrorCode: string;
}
```

Hiện `src/services/apiClient.ts` có `ApiError` chỉ giữ `status`, `errors`, `path`. AI FE cần bổ sung `code` và `details` dạng tùy chọn, chuyển từ error JSON vào đối tượng lỗi và giữ tương thích các caller cũ. Không parse `code` bằng cách tìm từ khóa trong message.

## 7. Checklist kiểm thử FE

| Case | Dữ liệu/kết quả BE | Kết quả mong đợi |
| --- | --- | --- |
| F01 | 200, xe máy, `fallbackUsed=false` | Giữ thẻ xe/giá, không có cảnh báo chuyển xe. |
| F02 | 200, xe máy bị từ chối, bán tải thành công | Hiển thị giá bán tải và giải thích từ BE; vẫn được đặt hàng nếu các điều kiện khác hợp lệ. |
| F03 | 200, kiện lớn, xe tải được chọn ngay từ đầu | Không hiển thị thông báo "xe máy không hỗ trợ". |
| F04 | 200, `PREFERRED_VEHICLE_UNAVAILABLE` | Hiển thị lý do không khả dụng, không bịa tuyến ngoài vùng phục vụ. |
| F05 | 422, `NO_SUITABLE_VEHICLE_FOR_ROUTE` | Xóa giá/quoteId cũ, hiện thông báo kiểm tra địa chỉ, chặn đặt hàng. |
| F06 | 429/502/503/504 | Hiện lỗi kỹ thuật, không hiện "không hỗ trợ tuyến". |
| F07 | Thiếu `vehicleSelection` hoặc thiếu distance/city | Không crash, không bịa dữ liệu, vẫn dùng báo giá hợp lệ. |
| F08 | Đổi địa chỉ trong lúc request cũ chưa xong | Response cũ không ghi đè giá và thông báo cho địa chỉ mới. |
| F09 | `CENTER_TO_BUYER`, điểm lấy ở Đà Nẵng | Dùng thành phố trung tâm BE trả, không lấy thành phố trong profile seller. |
| F10 | Báo giá hết hạn và lấy lại bị lỗi | Không đặt hàng bằng giá cũ; không tự dùng 0đ. |

## 8. Prompt giao AI FE

```text
Sửa FE React TypeScript của SecondLife để hiển thị giải thích chọn phương tiện Lalamove theo contract BE mới. Chỉ sửa FE, giữ nguyên các thay đổi chưa commit của tôi.

Đọc docs/FE_SHIPPING_VEHICLE_SELECTION_GUIDE.md trong BE và đối chiếu code FE thực tế, nhất là:
- src/components/modals/CheckoutModal.tsx
- src/services/shippingService.ts
- src/services/apiClient.ts
- File định nghĩa ShippingQuoteResponseDto và ShippingQuoteRequestDto trong src/types.

Luồng giữ nguyên: POST /api/v1/shipping/quotes tự chọn xe phù hợp nhỏ nhất, có thể thử xe khác nếu hãng từ chối và trả báo giá đầu tiên thành công. Không thêm allowVehicleUpgrade, không gửi serviceType, không yêu cầu gọi API lần hai hay thêm dialog xin đổi xe.

BE giữ các trường báo giá cũ và bổ sung data.vehicleSelection (optional):
{ preferredServiceType, selectedServiceType, fallbackUsed, reasonCode?, providerErrorCode?, pickupCity?, deliveryCity?, distanceKm?, message? }.

1. Khi HTTP 200 và fallbackUsed=true, vẫn hiển thị xe, phí, tổng tiền, hạn báo giá như hiện tại; thêm khung vàng "Lý do chọn phương tiện thay thế", hiển thị message từ BE. Có thể thêm tuyến và khoảng cách nếu BE có dữ liệu. Đây là báo giá hợp lệ, không chặn đặt hàng chỉ vì fallback.
2. Khi fallbackUsed=false hoặc không có vehicleSelection, không bịa cảnh báo. Kiện lớn được chọn xe tải ngay từ đầu không phải chuyển xe. Dùng serviceType từ BE để hiển thị phương tiện; số đo dùng snapshot shippingWeight/Length/Width/Height.
3. reasonCode=PREFERRED_VEHICLE_OUT_OF_SERVICE_AREA có nghĩa xe ưu tiên bị từ chối trên tuyến. PREFERRED_VEHICLE_UNAVAILABLE có nghĩa xe ưu tiên không khả dụng; không tự đổi thành ngoài vùng phục vụ. Không suy đoán theo giá cao, loại xe hoặc so sánh thành phố.
4. BE trả HTTP 422 với success=false, code=NO_SUITABLE_VEHICLE_FOR_ROUTE, message, path, errors, timestamp, details={preferredServiceType,attemptedServiceTypes,pickupCity?,deliveryCity?,providerErrorCode}. Chỉ trường hợp này hiển thị thông báo không tìm được xe phù hợp phục vụ tuyến, dùng message BE. Xóa quote/quoteId cũ, hiện "Chưa có báo giá vận chuyển", khóa đặt hàng và cho kiểm tra địa chỉ.
5. ApiError hiện chỉ giữ status/errors/path: bổ sung code và details tùy chọn từ JSON lỗi, giữ tương thích constructor/caller và các API khác. Giữ thông báo BE; không parse mã lỗi từ chuỗi message. Bỏ diễn giải cũ rằng mọi ERR_OUT_OF_SERVICE_AREA đều có nghĩa Lalamove chỉ giao nội thành/cùng thành phố.
6. 429/502/503/504 là lỗi kỹ thuật, không gắn thành tuyến không hỗ trợ. Không coi lỗi/thiếu giá là phí ship 0đ hay dùng giá cũ. Cho thử lại có kiểm soát, tránh double-call.
7. Khi thay bài, negotiation hoặc địa chỉ, vô hiệu hóa báo giá và thông báo cũ; dùng AbortController hoặc request sequence để response cũ đến muộn không ghi đè state mới. Áp dụng cả nhánh tự lấy lại giá trước khi thanh toán; chỉ tạo đơn bằng báo giá đúng dữ liệu hiện tại và còn hạn.
8. Tên thành phố/giá/khoảng cách lấy từ BE, không hardcode Hà Nội/TP.HCM, không hiển thị ước tính xe máy giả. Không thay đổi nghiệp vụ kiểm định, ví, thương lượng hay thanh toán ngoài phạm vi này.

Ví dụ mới: MOTORCYCLE bị ERR_OUT_OF_SERVICE_AREA; LD_PICKUP_TRUCK thành công; fallbackUsed=true; pickupCity="Thành phố Hà Nội"; deliveryCity="Thành phố Hồ Chí Minh"; distanceKm=1498.6; shippingFee=6164800. Các giá trị này chỉ là ví dụ, không hardcode vào app.

Kiểm thử: xe ưu tiên thành công; xe thay thế thành công; kiện lớn chọn tải ngay từ đầu; invalid service khác với ngoài vùng; 422 không có xe; 429/502/503/504; thiếu metadata; đổi địa chỉ và response đến muộn; báo giá hết hạn; trung tâm kiểm định là điểm xuất phát. Chạy kiểm tra TypeScript/lint/build theo script của dự án, báo file đã sửa và kết quả.
```
