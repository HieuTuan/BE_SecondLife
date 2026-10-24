# Hướng dẫn FE: danh mục xe và kiểm tra kiện hàng Lalamove

## 1. Phạm vi bàn giao

Backend bổ sung `GET /api/v1/shipping/vehicles` cho bảng thông tin vận chuyển có thể thu gọn ở form đăng bài. FE tải danh mục theo điểm lấy hàng, hiển thị giới hạn từng xe và đối chiếu ngay khi SELLER nhập cân nặng/kích thước.

Tài liệu mô tả contract backend đã triển khai và hướng dẫn FE thực hiện tiếp. Các đoạn TypeScript/React là mẫu cần tích hợp; thay đổi này không bao gồm sửa frontend. Dữ liệu, thời gian, tên xe và phí trong ví dụ đều minh họa, không phải kết quả gọi Sandbox/Production trong lần bàn giao này.

Nguồn giới hạn tối đa là catalogue Lalamove, được BE chuẩn hóa về gram và centimet. Tham khảo cấu trúc dịch vụ của provider tại [Lalamove Get City Info](https://developers.lalamove.com/#get-city-info). Khi tích hợp SecondLife, FE dùng contract bên dưới thay vì đọc trực tiếp cấu trúc provider.

Các nguyên tắc cần giữ:

- Mỗi xe đều có `minWeightGram=1` do ứng dụng quy định; đây không phải mức tối thiểu Lalamove công bố.
- Không chia khoảng cân nặng độc quyền cho từng loại xe. Một kiện có thể vừa nhiều xe.
- Chiều cao luôn giữ đứng; chỉ được đổi chỗ chiều dài và chiều rộng.
- Giới hạn tối đa là bao gồm giá trị bằng giới hạn; không làm tròn giảm để tạo kết quả vừa xe.
- Catalogue chỉ xác nhận khả năng tải/kích thước. Tuyến, phí và xe dùng thực tế được quyết định khi BE lấy quote.
- Không hardcode giới hạn chung 50 kg/200 cm, danh sách xe, thành phố hoặc dữ liệu dự phòng giả.

**Prompt có thể sao chép để giao FE triển khai nằm ở mục 12 cuối tài liệu.**

Tài liệu liên quan: [luồng vận chuyển tự chọn xe](FE_AUTO_VEHICLE_SHIPPING_GUIDE.md), [địa chỉ và mua ngay](FE_ADDRESS_BUY_NOW_TRACKING_GUIDE.md), [quy chuẩn backend](BACKEND_CODING_STANDARDS.md).

## 2. Endpoint, xác thực và tọa độ

~~~http
GET /api/v1/shipping/vehicles
Authorization: Bearer <access_token>
Accept: application/json
~~~

Endpoint yêu cầu đăng nhập. FE dùng `request` hiện có với `requiresAuth: true`; không giữ key/secret Lalamove và không gọi provider từ trình duyệt.

| Query | Kiểu | Quy tắc |
|---|---|---|
| `latitude` | Số thập phân | Vĩ độ điểm lấy hàng; trong [-90, 90] |
| `longitude` | Số thập phân | Kinh độ điểm lấy hàng; trong [-180, 180] |

Chỉ có hai cách gọi hợp lệ:

~~~http
GET /api/v1/shipping/vehicles
GET /api/v1/shipping/vehicles?latitude=10.777&longitude=106.695
~~~

Không query: trả các thành phố được cấu hình cho phép và thực sự xuất hiện trong catalogue provider. Có đủ cặp tọa độ: trả dữ liệu của thành phố chứa điểm lấy hàng. BE kiểm tra cặp tọa độ, giới hạn địa lý và khu vực hỗ trợ trước khi gọi provider.

Gửi thiếu một tham số, số sai định dạng, ngoài khoảng hợp lệ hoặc điểm lấy hàng ngoài khu vực hỗ trợ: HTTP 400. Tọa độ hợp lệ về mặt số học chưa đồng nghĩa khu vực đó được hỗ trợ.

`latitude/longitude` phải thuộc kho lấy hàng của SELLER. Không dùng địa chỉ nhận của BUYER, vị trí GPS hiện tại của trình duyệt hoặc tọa độ trung tâm thành phố tự tạo. Nếu chưa có pickup, có thể tải toàn bộ catalogue để xem tham khảo, nhưng chưa đánh dấu xe phù hợp để cho qua bước xác nhận vận chuyển.

BE nhận diện các khu vực hỗ trợ bằng mã `VN HAN`, `VN DAD`, `VN SGN`, sau đó áp dụng cấu hình thành phố được phép. FE không suy ra rằng cả ba luôn có dữ liệu hoặc được phục vụ trên mọi tuyến.

### 2.1. Lấy điểm lấy hàng

~~~http
GET /api/v1/shipping/pickup-address
Authorization: Bearer <access_token>
~~~

API đọc pickup của người đang đăng nhập; không truyền `sellerId`. BUYER gọi API này không nhận pickup của SELLER đang xem. Response 200 có thể không có `data` vì chưa lưu pickup; xử lý trường hợp đó trước khi đọc tọa độ.

Khi SELLER đổi và lưu pickup thành công, hủy kết quả catalogue cũ và tải lại bằng cặp tọa độ mới. Dùng tọa độ đã được BE lưu/xác nhận trong luồng địa chỉ; không suy đoán thành phố từ chuỗi địa chỉ.

## 3. Response thành công

Ví dụ minh họa có hai xe, không phải cấu hình xe/tải trọng cố định:

~~~json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "provider": "LALAMOVE",
    "minWeightSource": "APPLICATION",
    "minDimensionCm": 1,
    "allowLengthWidthSwap": true,
    "keepUpright": true,
    "cities": [
      {
        "locode": "VN SGN",
        "name": "Ho Chi Minh City",
        "vehicles": [
          {
            "serviceType": "MOTORCYCLE",
            "description": "Xe máy minh họa",
            "minWeightGram": 1,
            "maxWeightGram": 30000,
            "maxLengthCm": 40,
            "maxWidthCm": 40,
            "maxHeightCm": 40
          },
          {
            "serviceType": "VAN",
            "description": "Xe tải nhỏ minh họa",
            "minWeightGram": 1,
            "maxWeightGram": 500000,
            "maxLengthCm": 220,
            "maxWidthCm": 120,
            "maxHeightCm": 120
          }
        ]
      }
    ]
  },
  "timestamp": "2026-10-24T10:00:00"
}
~~~

### 3.1. Ý nghĩa và nguồn từng field

| Field | Kiểu JSON | Nguồn/ý nghĩa |
|---|---|---|
| `success` | boolean | Kết quả API SecondLife |
| `message` | string | Thông báo từ BE |
| `data` | object | Danh mục đã chuẩn hóa |
| `timestamp` | string | Thời điểm response; không dùng làm hạn dùng quote |
| `data.provider` | string | Hiện tại `LALAMOVE` |
| `data.minWeightSource` | string | `APPLICATION`: ứng dụng đặt mức nhập tối thiểu |
| `data.minDimensionCm` | number | 1 cm cho mỗi chiều kiện hàng |
| `data.allowLengthWidthSwap` | boolean | `true`: được đổi chỗ dài/rộng |
| `data.keepUpright` | boolean | `true`: không đổi chiều cao với dài/rộng |
| `data.cities` | array | Có thể rỗng; thứ tự provider sau lọc |
| `cities[].locode` | string | Mã thành phố được BE chuẩn hóa |
| `cities[].name` | string | Tên provider; có thể dùng mã làm nhãn dự phòng |
| `cities[].vehicles` | array | Các dịch vụ đủ dữ liệu kiểm tra; có thể rỗng |
| `vehicles[].serviceType` | string | Mã dịch vụ gốc, giữ nguyên khi đối chiếu |
| `vehicles[].description` | string | Nhãn provider; có thể rỗng |
| `vehicles[].minWeightGram` | number | 1 g, do ứng dụng quy định |
| `vehicles[].maxWeightGram` | number | Tải tối đa từ provider, đã đổi về g |
| `vehicles[].maxLengthCm` | number | Chiều dài tối đa từ provider, đã đổi về cm |
| `vehicles[].maxWidthCm` | number | Chiều rộng tối đa từ provider, đã đổi về cm |
| `vehicles[].maxHeightCm` | number | Chiều cao đứng tối đa từ provider, đã đổi về cm |

Các field `max*` có thể là số thập phân. FE giữ số gốc để so sánh; định dạng dễ đọc chỉ phục vụ hiển thị. Không `parseInt` hoặc `Math.round` giới hạn trước khi matching.

Nhãn gợi ý: “Cân nặng nhập từ 1 g; tải xe tối đa … kg”. Không ghi “Xe tải nhận từ 30 kg” chỉ vì xe máy tối đa 30 kg. Một kiện 5 kg có thể được nhiều loại xe chở.

### 3.2. Quy tắc chuẩn hóa và bỏ dữ liệu không xác minh được

BE đổi `g/kg/t` thành gram và `cm/m/mm` thành centimet. Ví dụ `0.5 t` thành `500000 g`; `2.2 m` thành `220 cm`; `405 mm` thành `40.5 cm`.

BE bỏ dịch vụ có mã không hợp lệ, thiếu một giới hạn, số không dương, số không đọc được hoặc đơn vị không hỗ trợ. Mã dịch vụ hợp lệ khớp `[A-Z][A-Z0-9_]{0,59}`. Dịch vụ có tải dưới 1 g hoặc bất kỳ chiều nào dưới 1 cm cũng bị bỏ vì không có kiện số nguyên hợp lệ nào vừa.

BE không tự bổ sung tải trọng/kích thước thiếu. Danh mục có thể vẫn giữ một thành phố nhưng `vehicles=[]` khi toàn bộ dịch vụ của thành phố bị loại. Thứ tự thành phố và dịch vụ được giữ sau lọc; FE không cần sort chỉ để render.

Đường chuẩn hóa dùng chung ở `integration/shipping/LalamoveVehicleCatalog.java` được dùng cho API catalogue và bộ chọn xe khi quote. DTO typed nằm ở `dto/response/ShippingVehicleCatalogResponse.java`; controller gọi service interface, logic chuẩn hóa không đặt ở controller.

### 3.3. API raw cũ vẫn giữ contract

| API | Dữ liệu | FE dùng cho |
|---|---|---|
| `GET /shipping/cities` | Catalogue provider sau lọc thành phố, còn cấu trúc `services/load/dimensions` | Các nơi đang đọc raw |
| `GET /shipping/provinces` | Alias của `/cities` | Tương thích luồng cũ |
| `GET /shipping/vehicles` | DTO ổn định, gram/cm và metadata matching | Bảng xe của form đăng bài |

Không thay `getCities` toàn cục bằng endpoint mới vì hình dạng dữ liệu khác. `/provinces` không phải cây tỉnh/quận/phường GHN và các mảng tỉnh dự phòng hiện có không được dùng để dựng danh mục xe.

## 4. Response rỗng và lỗi

### 4.1. Không có thành phố phù hợp trong cấu hình/provider

HTTP 200, ví dụ minh họa:

~~~json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "provider": "LALAMOVE",
    "minWeightSource": "APPLICATION",
    "minDimensionCm": 1,
    "allowLengthWidthSwap": true,
    "keepUpright": true,
    "cities": []
  },
  "timestamp": "2026-10-24T10:00:00"
}
~~~

Điểm thuộc khu vực được BE nhận diện nhưng thành phố không xuất hiện trong catalogue đã lọc có thể nhận kết quả này. Hiển thị “Chưa có danh mục xe cho điểm lấy hàng này”. Không lấy xe thành phố khác để thay thế.

### 4.2. Thành phố có mặt nhưng không có xe đủ metadata

HTTP 200, ví dụ minh họa:

~~~json
{
  "success": true,
  "message": "Operation successful",
  "data": {
    "provider": "LALAMOVE",
    "minWeightSource": "APPLICATION",
    "minDimensionCm": 1,
    "allowLengthWidthSwap": true,
    "keepUpright": true,
    "cities": [
      { "locode": "VN SGN", "name": "Ho Chi Minh City", "vehicles": [] }
    ]
  },
  "timestamp": "2026-10-24T10:00:00"
}
~~~

Hiển thị “Chưa có thông số xe được xác minh cho khu vực này” và nút tải lại. Không kết luận kiện quá lớn khi chưa có xe nào để đối chiếu.

### 4.3. Lỗi không có field cụ thể vẫn phải hiện

HTTP 400 do thiếu cặp tọa độ, ví dụ minh họa:

~~~json
{
  "success": false,
  "message": "Cần cung cấp đồng thời latitude và longitude của điểm lấy hàng",
  "path": "/api/v1/shipping/vehicles",
  "errors": [],
  "timestamp": "2026-10-24T10:00:00"
}
~~~

`errors=[]` không có nghĩa không có lỗi. Luôn hiện `message` ở cấp form; nếu có `errors[].field/message` thì bổ sung lỗi cạnh trường. Response lỗi dùng `path/errors` và không có `data` theo kiểu thành công.

HTTP 429, ví dụ minh họa về hình dạng; nội dung message thực tế do BE trả:

~~~json
{
  "success": false,
  "message": "Lalamove tạm giới hạn số lượng yêu cầu",
  "path": "/api/v1/shipping/vehicles",
  "errors": [],
  "timestamp": "2026-10-24T10:00:00"
}
~~~

| HTTP | Cách xử lý FE |
|---|---|
| 400 | Hiện message, kiểm tra pickup/cặp tọa độ/khu vực; không tự retry liên tục |
| 401 | Dùng luồng refresh/đăng nhập của apiClient |
| 403 | Hiện thiếu quyền theo response, không retry vô hạn |
| 429 | Giữ input; giảm tần suất, cho thử lại có kiểm soát |
| 502 | Provider trả lỗi hoặc catalogue sai dạng; không thay bằng mảng rỗng |
| 503 | Dịch vụ vận chuyển tạm không sẵn sàng; giữ input, cho thử lại |
| 504 | Timeout provider; giữ input, cho thử lại |
| Lỗi mạng | Hiện chưa tải được dữ liệu, cho retry; không coi là không có xe |

429/502/503/504 là các trạng thái khác nhau trên wire. Không gộp chúng thành 400 hoặc thành danh mục thành công rỗng. Không dựng thời gian chờ cố định thành cam kết từ server khi response không cung cấp.

## 5. UI thu gọn và trạng thái form

Gợi ý bảng đặt cạnh các trường “Thông số kiện sau đóng gói”; nút mở/đóng có `type="button"`, `aria-expanded` và `aria-controls`. Việc đóng bảng chỉ ảnh hưởng hiển thị, không bỏ qua kiểm tra.

~~~text
Thông số kiện sau đóng gói
Cân nặng [ 5 ] [kg]    Dài [30] Rộng [25] Cao [20] [cm]

2 loại xe phù hợp về tải trọng và kích thước.
[ Xem thông tin vận chuyển ▼ ]

Khi mở:
✓ Xe máy       1 g–30 kg       Tối đa 40 × 40 × 40 cm
✓ Xe tải nhỏ   1 g–500 kg      Tối đa 220 × 120 × 120 cm
  Gợi ý: Xe máy
Giữ kiện đứng; chỉ đổi chỗ dài và rộng.
Phí và khả năng phục vụ tuyến được xác định ở bước báo giá.
~~~

Các số trên chỉ minh họa cách trình bày. Tên xe ưu tiên `description || serviceType`; dùng icon sẵn có hoặc icon chung cho mã chưa biết. Không bỏ xe chỉ vì thiếu nhãn/icon trong mapping FE.

Khuyến nghị đánh dấu tất cả xe vừa kiện, đồng thời có thể gắn “Gợi ý” cho một xe tải nhỏ nhất đủ chở. Đây là mặc định UI đề xuất; yêu cầu chưa chốt chỉ tick một hay nhiều xe. Nếu FE chọn chỉ nhấn mạnh một xe, vẫn giữ dữ liệu và kết quả matching của toàn bộ xe, giải thích đó là gợi ý. Không biến lựa chọn hiển thị thành field chọn xe bắt buộc hoặc sửa contract API.

| Trạng thái | Hiển thị | Xác nhận thông số vận chuyển |
|---|---|---|
| Chưa có pickup | Hướng dẫn bổ sung điểm lấy hàng | Chặn |
| Đang tải | Skeleton/spinner và nhãn đang tải | Chặn |
| Input trống/chưa hợp lệ | Lỗi trường hoặc hướng dẫn nhập đủ | Chặn, không tick xanh |
| Catalogue sẵn sàng, có xe vừa | Số xe phù hợp, tick từng xe | Cho qua khi các điều kiện form khác đạt |
| Catalogue sẵn sàng, có xe nhưng không xe vừa | Lý do vượt tải/kích thước và giới hạn | Chặn |
| `cities=[]` | Chưa có danh mục cho pickup | Chặn |
| `vehicles=[]` | Chưa có thông số xe được xác minh | Chặn |
| Provider/mạng lỗi | Message và nút thử lại, giữ input | Chặn, không gắn nhãn kiện quá lớn |
| Pickup đã đổi, response cũ còn lưu | Đang tải theo pickup mới | Chặn, không dùng tick cũ |

Thông báo chặn và retry phải nhìn thấy ngay cả khi bảng đang đóng. Không tự xóa cân nặng/kích thước khi lỗi tải hoặc đổi đơn vị. Có thể vẫn cho lưu bản nháp theo nghiệp vụ hiện có; không hiển thị bước kiểm tra vận chuyển đã đạt khi chưa đủ điều kiện.

## 6. TypeScript: helper nhỏ và logic thuần

### 6.1. Types và gọi API

Đặt types trong vùng types hiện có hoặc cạnh service; không tạo hệ thống abstraction mới chỉ cho một endpoint.

~~~ts
export interface VehicleLimit {
  serviceType: string;
  description: string;
  minWeightGram: number;
  maxWeightGram: number;
  maxLengthCm: number;
  maxWidthCm: number;
  maxHeightCm: number;
}
export interface VehicleCity {
  locode: string;
  name: string;
  vehicles: VehicleLimit[];
}
export interface VehicleCatalog {
  provider: 'LALAMOVE';
  minWeightSource: 'APPLICATION';
  minDimensionCm: number;
  allowLengthWidthSwap: boolean;
  keepUpright: boolean;
  cities: VehicleCity[];
}
export interface PickupCoordinates {
  latitude: number;
  longitude: number;
}
export async function getVehicleCatalog(
  pickup?: PickupCoordinates,
  signal?: AbortSignal,
): Promise<VehicleCatalog> {
  const query = pickup
    ? '?' + new URLSearchParams({
        latitude: String(pickup.latitude),
        longitude: String(pickup.longitude),
      }).toString()
    : '';
  const response = await request<VehicleCatalog>('/shipping/vehicles' + query, {
    method: 'GET',
    requiresAuth: true,
    signal,
  });
  if (!response.success || !response.data) {
    throw new Error(response.message || 'Chưa tải được danh mục xe');
  }
  return response.data;
}
~~~

Mẫu cần import `request` từ `./apiClient` nếu đặt trong `services/shippingService.ts`. Có thể thêm hàm này vào object `shippingService` hiện có theo phong cách dự án, không tạo hai hàm gọi API trùng nhau.

`RequestOptions` hiện tại extends `RequestInit` và truyền options vào `fetch`, nên hỗ trợ `signal`. Hàm mới để lỗi đi lên UI; không sao chép `getCities` đang catch rồi trả `null`. Giữ kiểm tra tọa độ finite và phạm vi trong luồng lấy pickup trước khi tạo query; BE vẫn validate lại.

`apiClient.request` hiện ném `Error` thông thường và làm mất HTTP status/`errors` có cấu trúc. Không viết `catch(error) { if (error.status === 429) ... }` rồi coi như đang hoạt động. Để có nhánh retry riêng theo mã, FE nên bổ sung một `ApiError extends Error` nhỏ, chứa `status` và field errors, tại vị trí hiện đang throw trong apiClient; giữ nguyên `message` để các caller cũ tương thích. Khi chưa bổ sung, hiển thị `Error.message` và dùng retry thủ công, không đoán mã từ nội dung chuỗi.

### 6.2. Đổi đơn vị mà không mất 1 g

Giá trị gửi BE phải là số nguyên dương: cân nặng g, các chiều cm. Hiện tại switch g → kg dùng `toFixed(2)` có thể đổi 1 g thành 0 kg hoặc mất vài gram. Dùng tối đa 3 chữ số sau dấu phẩy khi hiển thị kg; m cần tối đa 2 chữ số để giữ cm.

Ví dụ parser theo chuỗi tránh làm tròn ngầm số lẻ về đơn vị cơ sở:

~~~ts
export function toBaseInteger(raw: string, decimalPlaces: number): number | null {
  const normalized = raw.trim().replace(',', '.');
  if (!/^\d+(?:\.\d+)?$/.test(normalized)) return null;
  const [whole, fraction = ''] = normalized.split('.');
  if (/[1-9]/.test(fraction.slice(decimalPlaces))) return null;
  const digits = fraction.slice(0, decimalPlaces).padEnd(decimalPlaces, '0');
  const value = Number(whole + digits);
  return Number.isSafeInteger(value) && value >= 1 && value <= 2147483647
    ? value : null;
}
export function fromBaseInteger(value: number, decimalPlaces: number): string {
  const factor = 10 ** decimalPlaces;
  const whole = Math.floor(value / factor);
  const fraction = String(value % factor)
    .padStart(decimalPlaces, '0').replace(/0+$/, '');
  return fraction ? String(whole) + '.' + fraction : String(whole);
}
// g/cm: decimalPlaces = 0; kg: 3; m: 2.
const oneGram = toBaseInteger('0.001', 3); // 1
const invalidHalfGram = toBaseInteger('0.0005', 3); // null
const kilograms = fromBaseInteger(1, 3); // "0.001"
~~~

Mức `2147483647` là giới hạn biểu diễn `int` của DTO Java hiện tại, không phải mức tải chung của carrier. Thông báo UI vẫn phân biệt dữ liệu nhập sai và kiện vượt xe.

Duy trì một giá trị cơ sở đã xác thực cho mỗi trường; raw string để người dùng nhập dở. Khi đổi đơn vị, format lại từ giá trị cơ sở, không chuyển qua lại từ giá trị đã làm tròn trên màn hình. Nếu raw chưa hợp lệ, không tự tạo giá trị hợp lệ hoặc giữ tick từ lần nhập trước.

Không dùng `parseFloat('12abc')`, không làm tròn `0.5 g` thành `1 g`, không biến ô trống thành 0 rồi coi như dữ liệu đã nhập. Ví dụ `0.001 kg` hợp lệ, `1.5 g` không hợp lệ; `0.01 m` hợp lệ, `0.005 m` không hợp lệ.

### 6.3. Matching thuần và gợi ý

Dùng `ShippingParcel` hiện có nếu đã khai báo; tránh định nghĩa hai kiểu cùng nghĩa trong FE.

~~~ts
export interface Parcel {
  weight: number; // g
  length: number; // cm
  width: number;  // cm
  height: number; // cm
}
export function validParcel(parcel: Parcel, catalog: VehicleCatalog): boolean {
  return [parcel.weight, parcel.length, parcel.width, parcel.height]
    .every(value => Number.isSafeInteger(value) && value <= 2147483647)
    && parcel.weight >= 1
    && [parcel.length, parcel.width, parcel.height]
      .every(value => value >= catalog.minDimensionCm);
}
export function fitsVehicle(
  parcel: Parcel,
  vehicle: VehicleLimit,
  catalog: VehicleCatalog,
): boolean {
  if (!validParcel(parcel, catalog)) return false;
  if (parcel.weight < vehicle.minWeightGram
      || parcel.weight > vehicle.maxWeightGram
      || parcel.height > vehicle.maxHeightCm) return false;
  const normal = parcel.length <= vehicle.maxLengthCm
    && parcel.width <= vehicle.maxWidthCm;
  const swapped = catalog.allowLengthWidthSwap
    && parcel.length <= vehicle.maxWidthCm
    && parcel.width <= vehicle.maxLengthCm;
  return normal || swapped;
}
export function suggestVehicle(fitting: VehicleLimit[]): VehicleLimit | undefined {
  return fitting.reduce<VehicleLimit | undefined>(
    (best, vehicle) => !best || vehicle.maxWeightGram < best.maxWeightGram
      ? vehicle : best,
    undefined,
  );
}
~~~

Với contract hiện tại `keepUpright=true`, luôn so chiều cao với `maxHeightCm`. Không thử sáu hoán vị của ba chiều. Nếu contract thay đổi cờ này trong tương lai, cần cập nhật thuật toán có chủ đích; không âm thầm chọn phép xoay khác.

Giữ list xe đúng thành phố của response scoped. Khi đã ready: `fitting = city.vehicles.filter(v => fitsVehicle(parcel, v, catalog))`. Không trộn xe nhiều thành phố rồi chọn loại xe dễ vừa nhất.

Gợi ý chọn `maxWeightGram` nhỏ nhất trong các xe vừa; khi bằng nhau giữ xe xuất hiện trước. Không gọi đây là “xe rẻ nhất” hoặc “xe chắc chắn được đặt”. BE thử báo giá theo thứ tự xe đủ điều kiện và kết quả quote có thể khác gợi ý FE.

## 7. React: tải theo pickup, đối chiếu theo input

Mẫu dưới là khung tích hợp trong component/hook hiện có. Các trường `pickupLatitude/pickupLongitude` lấy từ pickup hợp lệ; giá trị `null` biểu thị chưa có. Không thêm cân nặng/kích thước vào dependencies tải catalogue.

~~~tsx
type CatalogState =
  | { status: 'idle' }
  | { status: 'loading'; key: string }
  | { status: 'ready'; key: string; data: VehicleCatalog }
  | { status: 'error'; key: string; message: string };

const [catalogState, setCatalogState] = useState<CatalogState>({ status: 'idle' });
const [refreshVersion, setRefreshVersion] = useState(0);
const pickupKey = pickupLatitude != null && pickupLongitude != null
  ? String(pickupLatitude) + '/' + String(pickupLongitude) : null;

useEffect(() => {
  if (pickupLatitude == null || pickupLongitude == null || pickupKey == null) {
    setCatalogState({ status: 'idle' });
    return;
  }
  const controller = new AbortController();
  let active = true;
  setCatalogState({ status: 'loading', key: pickupKey });
  getVehicleCatalog(
    { latitude: pickupLatitude, longitude: pickupLongitude },
    controller.signal,
  ).then(data => {
    if (active) setCatalogState({ status: 'ready', key: pickupKey, data });
  }).catch((error: unknown) => {
    if (!active || controller.signal.aborted) return;
    setCatalogState({
      status: 'error',
      key: pickupKey,
      message: error instanceof Error ? error.message : 'Chưa tải được danh mục xe',
    });
  });
  return () => {
    active = false;
    controller.abort();
  };
}, [pickupLatitude, pickupLongitude, pickupKey, refreshVersion]);

const catalog = catalogState.status === 'ready' && catalogState.key === pickupKey
  ? catalogState.data : null;
const city = catalog?.cities[0]; // Request có pickup đã được BE scope thành phố.
const parcelIsValid = catalog != null && validParcel(parcel, catalog);
const fitting = catalog && city && parcelIsValid
  ? city.vehicles.filter(vehicle => fitsVehicle(parcel, vehicle, catalog)) : [];
const suggested = suggestVehicle(fitting);
const canConfirmShipping = pickupKey != null && parcelIsValid && fitting.length > 0;
// Nút thử lại: setRefreshVersion(version => version + 1).
~~~

Chỉ tạo `parcel` với giá trị số nguyên cơ sở đã kiểm tra; trường nhập chưa hợp lệ phải làm `parcelIsValid=false`. Với thư viện form hiện có có thể dùng trực tiếp kết quả parser và lỗi trường thay vì tạo state trùng nhau.

Khóa theo `pickupKey` ngăn dùng kết quả cũ trong render ngay sau khi đổi pickup; `active` và `abort` ngăn response cũ ghi đè request mới. Không chỉ dựa vào abort vì auth refresh hoặc request đã hoàn tất có thể làm cancellation đến muộn.

Tải khi mở form/bước vận chuyển, pickup thay đổi hoặc bấm refresh. Nhập từng ký tự chỉ chạy matching local; không gọi catalogue/quote mỗi lần nhập. Có thể cache theo pickup/thành phố trong vòng đời form nếu kiến trúc hiện có hỗ trợ; không thêm cache phức tạp hoặc giữ dữ liệu vô hạn để vượt lỗi provider.

Nếu refresh với cùng pickup, xóa tick khi chuyển sang loading. Khi thu gọn bảng, vẫn giữ summary/lỗi ngoài phần ẩn và vẫn khóa nút xác nhận nếu không đạt.

## 8. Các vị trí FE cần tích hợp

Repo FE hiện tại: `SEP490/fe/SEP490_FE_SecondLife`. Danh sách sau là việc FE cần làm, không phải thay đổi đã được áp dụng.

| File | Công việc |
|---|---|
| `src/services/shippingService.ts` | Thêm helper typed cho `/shipping/vehicles`; để lỗi đi lên caller; dùng signal |
| `src/services/apiClient.ts` | Giữ flow auth hiện có; nếu cần retry theo status, bổ sung typed error tương thích |
| `src/pages/CreateListingView.tsx` | Lấy pickup, tải scoped catalogue, sửa conversion, derived matching, bảng thu gọn, khóa bước |
| `src/components/modals/CheckoutModal.tsx` | Giữ quote do BE chọn xe, hiện message khi không có xe/tuyến, không bắt BUYER chọn xe |

Trình tự tích hợp:

1. Thêm types/helper; không đổi contract `getCities` đang có caller.
2. Xác định pickup từ response BE, xử lý thiếu data/toạ độ và lỗi tải riêng.
3. Thêm parser/formatter và matcher thuần; không đặt network call trong matcher.
4. Nối effect tải catalogue theo pickup; có loading/error/empty và guard response cũ.
5. Render toàn bộ xe của thành phố, giới hạn và kết quả matching; dùng `locode + serviceType` làm key theo dữ liệu thực tế.
6. Đưa lỗi chặn ra ngoài phần thu gọn; nối điều kiện vào bước xác nhận/lưu thông số và luồng submit hiện có.
7. Giữ việc lưu draft theo chính sách hiện tại; quote/checkout luôn dùng phản hồi mới từ BE.
8. Kiểm tra các ca QA dưới đây trước khi FE thông báo hoàn thành.

Tuân thủ chuẩn code: tên hàm nói rõ trách nhiệm, type cụ thể, không `any` trong phần mới khi có DTO, không catch rỗng, không sao chép matcher vào nhiều component, không thêm dependency cho phép nhân/chia hoặc một list xe. Không refactor toàn bộ form/API client ngoài phần cần thiết. Code backend tiếp tục theo các layer và DTO record đã chốt trong quy chuẩn dự án.

## 9. Lưu kiện hàng và lấy quote

### 9.1. Lưu thông số đã đóng gói

~~~http
PUT /api/v1/posts/{postId}/shipping-package
Authorization: Bearer <seller_access_token>
Content-Type: application/json
~~~

~~~json
{
  "weight": 60000,
  "length": 210,
  "width": 80,
  "height": 100
}
~~~

Đây là kiện 60 kg, dài 210 cm; không được FE chặn chỉ vì vượt 50 kg hoặc 200 cm. Với xe VAN minh họa ở mục 3, kiện vừa cả tải và kích thước; với catalogue thật phải so dữ liệu thật.

Payload chỉ có bốn số nguyên g/cm, không gửi `serviceType`, giới hạn xe, đơn vị hiển thị hoặc kết quả tick. SELLER phải có quyền và sở hữu bài theo kiểm tra BE. Khi BE từ chối lưu, giữ input và hiện message; dữ liệu localStorage không phải bằng chứng đã lưu thành công.

API lưu kiện kiểm tra số nguyên dương và các điều kiện nghiệp vụ hiện có; không gọi catalogue để chặn kiện không vừa xe ngay ở PUT. Kiểm tra catalogue trên form là phản hồi sớm của FE; quote là điểm BE đối chiếu tải/kích thước và khả năng tuyến. Không mô tả PUT thành API xác nhận có xe.

### 9.2. Quote vẫn do BE quyết định

~~~http
POST /api/v1/shipping/quotes
Authorization: Bearer <access_token>
Content-Type: application/json
~~~

~~~json
{
  "postId": "1c2dd5e3-36db-420b-b313-9ed606b017a9",
  "deliveryAddress": {
    "name": "Người nhận minh họa",
    "phone": "0912345678",
    "latitude": 10.782,
    "longitude": 106.700
  }
}
~~~

Có thể thêm `negotiationId` khi checkout theo thương lượng. Không gửi `serviceType` để ép loại xe; field cũ nếu được gửi sẽ bị BE bỏ qua. BE lấy kiện đã lưu và origin theo luồng đơn, tự kiểm tra lại catalogue rồi lấy quote.

Response quote có `quoteId`, `shippingFee`, `productPrice`, `totalPayable`, `expiresAt`, `provider`, `serviceType` cùng các field của contract hiện có. Hiển thị xe thực tế từ `data.serviceType`; dùng phí/hạn quote từ BE, không suy ra từ catalogue.

Dù có tick xanh, quote vẫn có thể thất bại do tuyến, địa chỉ, provider hoặc tình trạng dịch vụ. Giữ thông báo “phù hợp tải trọng/kích thước”, không đổi thành “đã có tài xế” hoặc “giao được chắc chắn”. Địa chỉ nhận thay đổi phải lấy quote mới; không dùng nó để chuyển catalogue của SELLER sang thành phố BUYER.

Kiện 60 kg/210 cm chỉ là ví dụ hợp lệ theo khả năng một xe minh họa. Nếu mọi xe thật nhỏ hơn kiện, hiển thị không có xe phù hợp; không tự chọn VAN/TRUCK mặc định.

## 10. Ma trận QA bàn giao cho FE

Dùng fixture minh họa mục 3 khi test local, cộng một fixture bất đối xứng có `maxLengthCm=220, maxWidthCm=120, maxHeightCm=120`. Các ca tích hợp dùng giới hạn thật từ response, không coi fixture là cấu hình Production.

| Ca | Kỳ vọng |
|---|---|
| Vừa đúng maxWeight, dài/rộng/cao đúng max | Xe đó được tick; phép so là `<=` |
| Tăng cân nặng thêm 1 g qua max | Xe đó bỏ tick; xe khác đủ tải vẫn có thể tick |
| Tăng một chiều thêm 1 cm qua khả năng cả hai cách dài/rộng | Xe đó bỏ tick |
| Nhập dài 100, rộng 200, cao 100 với xe 220×120×120 | Vừa do đổi dài/rộng |
| Nhập dài 100, rộng 100, cao 200 với xe 220×120×120 | Không vừa; không đặt kiện nằm |
| 1 g → kg → g nhiều lần | Hiện 0.001 kg và giữ đúng 1 g |
| 0.001 kg; 0.0005 kg; 1.5 g | Lần lượt 1 g hợp lệ; hai giá trị sau lỗi |
| 0.01 m; 0.005 m; ô trống/0/số âm | 1 cm hợp lệ; các trường hợp còn lại lỗi |
| Nhập dở dấu thập phân, NaN, Infinity, chữ sau số | Chưa hợp lệ, không tick và không submit |
| Kiện 5 kg/30×25×20 vừa hai xe fixture | Tick cả hai; gợi ý xe tải nhỏ nhất đủ chở |
| Xe bằng maxWeight nhau | Gợi ý giữ thứ tự provider, không tự gọi rẻ nhất |
| Kiện 60 kg/210×80×100 | Không chặn bởi 50 kg/200 cm; so từng xe thật |
| maxLength=40.5 cm, kiện dài 41 cm | Không làm tròn 40.5 thành 41 để cho qua |
| Chưa có pickup; GET pickup 200 thiếu data | Hướng dẫn bổ sung, không crash/không tick |
| Pickup SGN, xe HAN lớn hơn | Không dùng xe HAN để cho kiện SGN qua |
| Thiếu longitude, latitude=91, longitude=181, điểm ngoài vùng | 400 hiển thị message; không giả danh mục rỗng |
| `cities=[]`; một city với `vehicles=[]` | Hai empty state đúng; không gắn lỗi kiện quá lớn |
| Provider 429 | Giữ input, hiện lỗi và retry có kiểm soát, không gọi dồn |
| Provider 502/503/504 hoặc mất mạng | Lỗi tải riêng; không kết luận không có xe |
| Pickup A → B; response A về sau B | UI chỉ dùng kết quả B |
| Gõ nhanh 10 ký tự cân nặng | Matching đổi ngay, không phát 10 catalogue/quote request |
| Bảng đang đóng nhưng lỗi/no-fit | Message và khóa xác nhận vẫn nhìn thấy |
| Form vừa xe nhưng quote từ chối tuyến | Hiện message quote, không tạo checkout với phí giả |
| PUT lưu kiện bị từ chối | Không báo đã lưu chỉ vì localStorage đã có dữ liệu |

Các trường hợp chuẩn hóa BE cần đối chiếu khi fixture/API thay đổi: `g/kg/t`, `cm/m/mm`, giới hạn thiếu/đơn vị lạ, dịch vụ có tải dưới 1 g hoặc chiều dưới 1 cm, thành phố bị cấu hình loại, và lỗi catalogue sai dạng. FE không sửa fixture để tự tạo xe bù cho dữ liệu bị loại.

## 11. Acceptance checklist

- [ ] FE gọi endpoint typed bằng Bearer token, đúng cặp tọa độ pickup.
- [ ] API raw cũ và caller của chúng vẫn hoạt động theo contract cũ.
- [ ] Hiển thị min do APPLICATION và max do provider; không tạo khoảng cân nặng giả.
- [ ] Đổi g/kg, cm/m không mất độ chính xác; payload là số nguyên g/cm.
- [ ] Matcher thuần, giới hạn bao gồm max, đổi dài/rộng và giữ chiều cao.
- [ ] Không hardcode 50 kg/200 cm hoặc xe/thành phố dự phòng.
- [ ] Có loading, thiếu pickup, invalid input, hai empty state, no-fit và lỗi tải riêng.
- [ ] Không tick khi dữ liệu chưa đầy đủ hoặc thuộc pickup cũ.
- [ ] Lỗi vẫn xuất hiện khi bảng đóng; không bỏ qua `message` khi `errors=[]`.
- [ ] Chính sách tick/gợi ý được FE thống nhất và không biến thành chọn xe trên quote.
- [ ] Nhập từng ký tự chạy local; tải lại có kiểm soát và chặn response cũ.
- [ ] Bước xác nhận vận chuyển bị chặn nếu chưa kiểm tra được hoặc không có xe vừa.
- [ ] PUT chỉ lưu thông số; quote giữ quyền xác định xe/tuyến/phí ở BE.
- [ ] UI hiển thị xe/phí từ quote thực tế và xử lý quote lỗi dù catalogue vừa.
- [ ] Types/helper/component theo chuẩn code dự án, không thêm dependency không cần thiết.

Backend cần chạy phiên bản có endpoint mới khi FE bắt đầu tích hợp. Danh mục thực tế phụ thuộc môi trường và cấu hình provider; tài liệu này không xác nhận đã thử API vận chuyển thật hoặc đặt chuyến thành công.

## 12. Prompt giao việc cho FE

Sao chép toàn bộ nội dung trong khối dưới đây để giao FE triển khai. Prompt yêu cầu sửa frontend; phần BE và contract ở các mục trên đã có sẵn.

~~~text
Hãy sửa frontend SecondLife theo tài liệu FE_SHIPPING_VEHICLE_CATALOGUE_GUIDE.md được gửi kèm. Đọc toàn bộ tài liệu, hướng dẫn trong repository và code hiện tại trước khi sửa; dùng contract API trong tài liệu làm căn cứ. Thực hiện thay đổi thành code và kiểm chứng, không chỉ đưa ra kế hoạch.

Mục tiêu: tại phần nhập thông số kiện hàng trong form đăng bài của SELLER, bổ sung bảng thông tin vận chuyển có thể ẩn/hiện và tự tích tất cả xe phù hợp khi người dùng nhập cân nặng, chiều dài, chiều rộng, chiều cao.

1. Phạm vi và chuẩn code
- Chỉ sửa FE. BE đã cung cấp GET /api/v1/shipping/vehicles; không thay đổi backend, contract hoặc tự tạo API mới.
- Kiểm tra src/pages/CreateListingView.tsx, src/services/shippingService.ts, src/services/apiClient.ts và các component/helper có liên quan. Tái sử dụng kiến trúc, UI, types và cơ chế auth của dự án.
- Dùng TypeScript có kiểu rõ ràng; không thêm any, catch rỗng, logic trùng lặp hoặc dependency không cần thiết. Tách helper thuần cho chuyển đơn vị và đối chiếu xe khi phù hợp; không refactor phần ngoài phạm vi.
- Giữ các thay đổi có sẵn trong working tree, hành vi lưu draft và luồng đăng bài/checkout hiện tại.

2. Tải đúng danh mục xe
- Thêm helper typed gọi GET /api/v1/shipping/vehicles qua apiClient hiện có, requiresAuth: true. Đọc data theo envelope success/message/data/errors của BE; không gọi Lalamove trực tiếp từ trình duyệt.
- Dùng latitude và longitude của điểm lấy hàng SELLER đã được BE lưu/xác nhận. Đọc pickup qua GET /api/v1/shipping/pickup-address và xử lý cả response 200 thiếu data.
- Tải danh mục khi vào bước vận chuyển, khi pickup thay đổi hoặc khi bấm thử lại. Không gọi catalogue hay quote theo từng ký tự nhập cân nặng/kích thước.
- Khi có pickup, luôn gửi đủ cặp tọa độ và chỉ đối chiếu xe trong thành phố BE trả về. Không dùng địa chỉ BUYER hoặc xe ở thành phố khác để cho kiện qua.
- Nếu chưa có pickup, hướng dẫn bổ sung. Có thể cho xem danh mục tổng để tham khảo nhưng không tích xe hoặc cho xác nhận vận chuyển từ danh mục tổng.
- Chống response cũ ghi đè khi đổi pickup bằng AbortController và guard theo pickup/request hiện tại. Không dùng kết quả cũ trong lúc tải mới hoặc khi tải lỗi.

3. Bảng ẩn/hiện “Xem thông tin vận chuyển”
- Đặt nút ngay dưới nhóm trường cân nặng/kích thước. Mặc định thu gọn; bấm để mở/đóng. Dùng button type="button", aria-expanded và aria-controls, thao tác được bằng bàn phím.
- Khi mở, hiển thị tất cả xe trong danh mục của pickup: tên/description (fallback serviceType), cân nặng tối thiểu/tối đa, kích thước tối đa dài × rộng × cao và trạng thái phù hợp.
- Hiển thị đơn vị rõ ràng. minWeightGram=1 và minDimensionCm=1 là quy tắc nhập liệu APPLICATION, không ghi thành mức tối thiểu do Lalamove công bố. Không chia cân nặng thành các khoảng độc quyền theo xe.
- Tích tất cả xe vừa cả tải trọng và kích thước. Đây là dấu báo phù hợp tự động, không phải checkbox chọn xe để gửi lên BE. Khi nhiều xe cùng vừa, giữ tất cả dấu tích; không ép chỉ tích một xe.
- Khi thiếu/sai dữ liệu nhập hoặc chưa có catalogue đúng pickup, không hiển thị dấu tích cũ. Dùng nhãn trạng thái kèm biểu tượng, không chỉ dựa vào màu.
- Giữ một dòng kết quả ở ngoài phần thu gọn: cần nhập đủ dữ liệu, đang tải, có N loại xe phù hợp hoặc thông báo lỗi tương ứng. Thu gọn bảng không được che lỗi hay làm bỏ qua điều kiện chặn xác nhận.

4. Đối chiếu ngay khi nhập
- Sử dụng maxWeightGram, maxLengthCm, maxWidthCm, maxHeightCm của từng xe từ response; không hardcode 50 kg, 200 cm, danh sách xe hoặc giới hạn mẫu trong tài liệu.
- Dữ liệu gửi BE vẫn là bốn số nguyên dương: weight theo gram; length/width/height theo cm. Mức 2147483647 trong tài liệu là giới hạn kiểu int, không phải giới hạn tải trọng carrier.
- Nếu UI cho đổi g/kg hoặc cm/m, chuyển đổi chính xác theo hướng dẫn. 0.001 kg phải giữ được 1 g; không dùng toFixed(2) làm mất gram, parseFloat cho chuỗi có ký tự thừa hoặc tự làm tròn 0.5 g thành 1 g.
- Xe phù hợp khi cân nặng nằm trong min/max, chiều cao không vượt maxHeightCm và dài/rộng vừa ở hướng bình thường hoặc đổi chỗ dài/rộng khi allowLengthWidthSwap=true. So sánh bao gồm giá trị bằng giới hạn. Contract hiện tại keepUpright=true: không đổi chiều cao sang chiều khác để cho kiện vừa.
- Tính danh sách xe phù hợp từ input hợp lệ và catalogue hiện tại; không lưu thêm state kết quả dễ lệch với input. Khi raw input chưa hợp lệ, không dùng lại giá trị hợp lệ lần trước để giữ dấu tích.
- Nếu dữ liệu hợp lệ và danh mục đúng pickup có xe nhưng không xe nào vừa, hiện “Không có loại xe Lalamove đủ tải trọng và kích thước cho kiện hàng tại điểm lấy hàng”. Chặn bước xác nhận vận chuyển theo luồng form; vẫn giữ dữ liệu người dùng để chỉnh sửa và giữ chính sách lưu nháp hiện có.

5. Trạng thái lỗi và lưu dữ liệu
- Phân biệt thiếu pickup, input không hợp lệ, loading, cities=[], vehicles=[], không có xe vừa, lỗi mạng và lỗi provider. Catalogue lỗi/rỗng không phải bằng chứng kiện quá lớn; không tạo xe dự phòng.
- Giữ input và có nút thử lại khi tải lỗi. Hiển thị message kể cả khi errors=[]. Xử lý 400/429/502/503/504 theo tài liệu; không retry liên tục.
- apiClient hiện có thể chỉ ném Error thông thường: không tự giả định error.status tồn tại. Nếu cần nhánh xử lý HTTP status, bổ sung typed error tương thích các caller cũ; nếu chưa có, dùng message và retry thủ công như hướng dẫn.
- PUT /api/v1/posts/{postId}/shipping-package chỉ gửi weight, length, width, height. Không gửi serviceType, giới hạn xe hoặc dấu tích. Chỉ báo lưu thành công sau khi BE xác nhận; localStorage không thay thế kết quả BE.
- Dấu tích chỉ nghĩa phù hợp tải trọng/kích thước. BE vẫn quyết định xe, tuyến và phí khi quote; không suy ra phí từ catalogue và không bắt BUYER chọn xe. Nếu cần chỉnh CheckoutModal.tsx, chỉ xử lý hiển thị xe/phí/message từ response trong phạm vi này.
- Giữ contract và caller của /shipping/cities, /shipping/provinces; không đổi các API raw đó sang schema mới.

6. Kiểm chứng và bàn giao
- Thực hiện các ca QA tại mục 10 và đối chiếu checklist mục 11. Ưu tiên: bằng max; vượt 1 g/1 cm; nhiều xe cùng vừa; đổi dài/rộng; không xoay chiều cao; kiện trên 50 kg/200 cm; 1 g đổi đơn vị; giới hạn thập phân; thiếu pickup; catalogue rỗng/lỗi; đổi pickup có response về ngược thứ tự; lỗi vẫn thấy khi bảng đóng.
- Thêm hoặc cập nhật kiểm thử hành vi cho parser/matcher và trạng thái UI phù hợp với công cụ sẵn có. Kiểm tra nhập nhiều ký tự không tạo nhiều request catalogue/quote.
- Chạy typecheck/lint/build và test liên quan theo scripts thực tế của repository. Báo rõ lệnh và kết quả, phân biệt lỗi có sẵn với lỗi mới; không tuyên bố mọi kiểm tra đạt nếu chưa chạy.
- Hoàn thành phần FE, rồi báo các file đã sửa, hành vi UI mới và kết quả kiểm chứng. Nếu có trở ngại thật sự, nêu cụ thể; không tự sửa BE hoặc bịa dữ liệu để cho qua.
~~~
