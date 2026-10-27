# FE: Tích hợp lịch sử và chi tiết giao dịch ví

Tài liệu bàn giao phần bổ sung lịch sử giao dịch ví SecondLife, đối chiếu với controller, DTO, service BE và FE hiện tại ngày 25/10/2026.

**Phần cần có trên FE:** danh sách giao dịch của người đang đăng nhập, mô tả và nguồn giao dịch, số dư trước/sau, bộ lọc loại/thời gian, phân trang, xem chi tiết và xử lý dữ liệu lịch sử chưa có số dư.

Các UUID, số tiền và thời gian trong JSON dưới đây là dữ liệu minh họa. Khi tích hợp thật, sử dụng ID và dữ liệu do API trả về.

## 1. Vị trí và phạm vi màn hình

Luồng truy cập hiện tại:

```text
Người dùng đăng nhập
  → Mở popup ví từ khu vực số dư
  → Chọn tab Lịch Sử Giao Dịch
  → Lọc / chuyển trang / xem chi tiết
  → Quay lại danh sách hoặc đóng popup
```

API sử dụng danh tính trong access token. Buyer/Seller đọc lịch sử ví của chính mình; FE không gửi `userId` hoặc `walletId` để chọn người cần xem.

Đây là biến động tiền của ví người dùng. Những khoản thanh toán không làm tăng/giảm ví, ví dụ tiền chuyển khoản trực tiếp vào escrow, không nhất thiết xuất hiện ở danh sách này.

### 1.1. Các file FE đã được bổ sung/cập nhật

Các đường dẫn sau tính từ root repository FE `SEP490_FE_SecondLife`:

| File | Trách nhiệm |
| --- | --- |
| `src/types/index.ts` | Kiểu `WalletTransactionType`, `WalletTransaction` và các field mới |
| `src/services/walletService.ts` | Gọi API danh sách và chi tiết giao dịch |
| `src/utils/walletTransactions.ts` | Chuẩn hóa response, nhãn, định dạng tiền, query và khoảng ngày |
| `src/components/modals/WalletHistoryPanel.tsx` | Bộ lọc, danh sách, phân trang, chi tiết và các trạng thái giao diện |
| `src/components/modals/TopUpModal.tsx` | Gắn panel vào tab lịch sử của popup ví |
| `src/tests/walletTransactions.test.ts` | Kiểm tra chuẩn hóa dữ liệu, request, số dư 0/null, tiền và khoảng ngày |

FE hiện tại đã có phần triển khai này. Khi áp dụng trên nhánh FE khác, chuyển các thay đổi liên quan và giữ nguyên contract trong tài liệu; không cần tạo thêm một màn hình lịch sử trùng chức năng.

## 2. Quy ước gọi API

Base URL local:

```text
http://localhost:8080
```

Header cho các API trong tài liệu:

```http
Authorization: Bearer <ACCESS_TOKEN_CUA_USER_DANG_DANG_NHAP>
Accept: application/json
```

Các API đọc là `GET`, **không gửi JSON request body**. Dùng query string cho bộ lọc/phân trang.

**Response thành công trả object trực tiếp**, không bọc `success`, `message`, `data`:

| API | Đọc JSON body ở đâu? |
| --- | --- |
| `GET /api/v1/wallets/me` | `body.balance` |
| `GET /api/v1/wallets/me/transactions` | `body.items`, `body.totalElements`… |
| `GET /api/v1/wallets/me/transactions/{transactionId}` | `body.id`, `body.balanceAfter`… |

Với Axios, `axiosResponse.data` chính là JSON body ở bảng trên. Với `fetch`, dùng `await response.json()`. Không nhầm lớp `data` của Axios với field `data` của một `ApiResponse`.

FE hiện tại gọi qua `request()` trong `apiClient.ts`. Có thể truyền đường dẫn `/v1/wallets/...`; helper sẽ tạo URL `/api/v1/wallets/...` và dùng cơ chế token hiện có. Tránh tự nối thêm một lần `/api/v1`.

## 3. API danh sách giao dịch

### 3.1. Request mặc định của màn hình FE

```http
GET /api/v1/wallets/me/transactions?page=0&size=10&sort=createdAt,desc
```

### 3.2. Query parameters

| Tham số | Kiểu | Cách sử dụng |
| --- | --- | --- |
| `page` | Integer, bắt đầu từ 0 | Trang FE đầu tiên gửi `0`; nhãn giao diện hiển thị `page + 1` |
| `size` | Integer dương | Màn hình hiện tại gửi `10` |
| `sort` | String | Dùng `createdAt,desc` để xem giao dịch mới nhất trước |
| `type` | Enum, tùy chọn | Một trong sáu loại ở mục 6; chọn Tất cả thì bỏ tham số |
| `from` | ISO-8601 có múi giờ, tùy chọn | Mốc bắt đầu **có bao gồm** thời điểm này |
| `to` | ISO-8601 có múi giờ, tùy chọn | Mốc kết thúc **không bao gồm** thời điểm này |

Nếu không truyền sort, BE sắp xếp `createdAt DESC`, sau đó `id DESC` để phân trang ổn định khi nhiều giao dịch trùng thời gian. Nếu FE truyền `createdAt,desc`, BE vẫn thêm `id DESC` làm khóa phụ.

BE hỗ trợ sort theo `id`, `createdAt`, `amount`, `type`, `referenceId`. Màn hình lịch sử hiện tại chỉ cần sort theo thời gian. `sort=string` và sort có `ignoreCase` không được hỗ trợ.

Có thể gửi riêng `from` hoặc riêng `to`. Nếu có cả hai, bắt buộc `from < to`.

### 3.3. Response 200

```json
{
  "items": [
    {
      "id": "10000000-0000-4000-8000-000000000001",
      "walletId": "20000000-0000-4000-8000-000000000001",
      "amount": -30000.00,
      "type": "PAYMENT",
      "referenceId": "30000000-0000-4000-8000-000000000001",
      "createdAt": "2026-10-25T03:30:00Z",
      "currency": "VND",
      "direction": "DEBIT",
      "description": "Thanh toán đơn hàng từ ví",
      "referenceType": "ORDER",
      "balanceBefore": 100000.00,
      "balanceAfter": 70000.00
    }
  ],
  "page": 0,
  "size": 10,
  "totalElements": 1,
  "totalPages": 1,
  "isLast": true
}
```

### 3.4. Không có giao dịch phù hợp

```json
{
  "items": [],
  "page": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0,
  "isLast": true
}
```

Hiển thị “Chưa có giao dịch phù hợp”, gợi ý đổi bộ lọc và khóa nút chuyển trang. Đây là kết quả hợp lệ, khác với request lỗi.

## 4. API chi tiết giao dịch

### 4.1. Request

Lấy `id` từ một phần tử trong `items`:

```http
GET /api/v1/wallets/me/transactions/10000000-0000-4000-8000-000000000001
```

Không dùng `walletId` hoặc `referenceId` thay cho `transactionId`.

### 4.2. Response 200

```json
{
  "id": "10000000-0000-4000-8000-000000000001",
  "walletId": "20000000-0000-4000-8000-000000000001",
  "amount": -30000.00,
  "type": "PAYMENT",
  "referenceId": "30000000-0000-4000-8000-000000000001",
  "createdAt": "2026-10-25T03:30:00Z",
  "currency": "VND",
  "direction": "DEBIT",
  "description": "Thanh toán đơn hàng từ ví",
  "referenceType": "ORDER",
  "balanceBefore": 100000.00,
  "balanceAfter": 70000.00
}
```

Khi bấm “Xem chi tiết”, gọi API này thay vì chỉ dựng chi tiết từ dữ liệu đang có ở danh sách. Trong lúc chờ phải có loading; khi lỗi hiển thị lỗi và nút thử lại.

Giao dịch không tồn tại và giao dịch của người khác đều trả **404**. Không hiện hành động yêu cầu quyền xem ví người khác.

## 5. Ý nghĩa các field

| Field | Kiểu JSON | Quy tắc FE |
| --- | --- | --- |
| `id` | UUID string | Khóa dòng và ID dùng để gọi API chi tiết |
| `walletId` | UUID string | Mã ví của bản ghi; có thể hiển thị trong chi tiết |
| `amount` | Number có dấu | Dương: tiền vào; âm: tiền ra; bằng 0: không đổi số dư |
| `type` | String enum | Loại biến động ví, dùng cho nhãn/icon/bộ lọc |
| `referenceId` | UUID string hoặc null | Mã đối tượng nghiệp vụ; giải nghĩa bằng `referenceType` |
| `createdAt` | ISO-8601 UTC | Chuyển thành ngày giờ theo múi giờ hiển thị |
| `currency` | String | Hiện tại là `VND` |
| `direction` | `CREDIT`, `DEBIT`, `NEUTRAL` | Chiều tiền; phải nhất quán với dấu của `amount` |
| `description` | String | Mô tả tiếng Việt do BE cung cấp |
| `referenceType` | String enum | Nguồn giao dịch; dữ liệu lịch sử có thể là `UNKNOWN` |
| `balanceBefore` | Number hoặc null | Số dư ngay trước giao dịch; null nghĩa là chưa ghi nhận |
| `balanceAfter` | Number hoặc null | Số dư ngay sau giao dịch; null nghĩa là chưa ghi nhận |

Với giao dịch mới có đủ dữ liệu, BE đảm bảo:

```text
balanceAfter = balanceBefore + amount
```

Ví dụ: `100000 + (-30000) = 70000`.

Màn hình đọc các số dư BE đã trả; công thức trên dùng để kiểm tra dữ liệu, không dùng để tự bù số dư bị thiếu trên FE.

## 6. Nhãn loại và nguồn giao dịch

### 6.1. Loại giao dịch (`type`)

| Giá trị | Nhãn tiếng Việt | Cách hiểu |
| --- | --- | --- |
| `DEPOSIT` | Nạp tiền | Khoản tiền được cộng vào ví |
| `WITHDRAW` | Rút tiền | Khoản tiền đã trừ khỏi ví cho yêu cầu rút |
| `PAYMENT` | Thanh toán | Thanh toán bằng số dư ví |
| `EARNING` | Nhận tiền | Khoản doanh thu/tiền nhận được ghi vào ví |
| `REFUND` | Hoàn tiền | Khoản tiền hoàn lại vào ví |
| `RETURN_SHIPPING_FEE` | Phí ship hoàn hàng | Khoản phí hoàn hàng thực sự thu từ ví seller |

Chọn icon theo loại; chọn màu và dấu tiền theo `amount`. Tiền vào có thể dùng xanh và dấu `+`, tiền ra dùng đỏ và dấu `−`; không chỉ dựa vào màu để truyền đạt ý nghĩa.

### 6.2. Nguồn giao dịch (`referenceType`)

| Giá trị | Nhãn tiếng Việt | `referenceId` trỏ tới |
| --- | --- | --- |
| `ORDER` | Đơn hàng | Đơn mua bán |
| `INSPECTION_ORDER` | Đơn kiểm định | Đơn kiểm định |
| `DEPOSIT_REQUEST` | Yêu cầu nạp tiền | Yêu cầu nạp/chuyển khoản liên quan |
| `WITHDRAWAL` | Yêu cầu rút tiền | Yêu cầu rút hoặc khoản hoàn do yêu cầu bị từ chối |
| `SHIPMENT` | Chuyến vận chuyển | Chuyến hoàn hàng liên quan đến khoản phí |
| `CREDIT_PURCHASE` | Mua quyền sử dụng dịch vụ | Lần mua quyền sử dụng dịch vụ |
| `TOPUP_PACKAGE` | Gói quyền sử dụng dịch vụ | Gói quyền sử dụng dịch vụ |
| `UNKNOWN` | Chưa có thông tin | Nguồn chưa được ghi nhận |

`type=PAYMENT` không đủ để khẳng định đó là một đơn mua bán; còn có thanh toán kiểm định hoặc mua quyền sử dụng dịch vụ. Dùng `referenceType` để xác định nguồn.

Phiên bản FE hiện tại hiển thị nhãn nguồn và mã tham chiếu trong chi tiết. Nếu bổ sung liên kết đến đối tượng nguồn, chỉ bật khi có `referenceId`, route tương ứng đã tồn tại và người dùng có quyền xem. Không tự tạo route bằng cách đoán từ UUID.

### 6.3. Trạng thái rút tiền và phí hoàn hàng

**Rút tiền:** response giao dịch ví không có field trạng thái chuyển khoản ngân hàng. Không tự gắn `SUCCESS` cho mọi dòng `WITHDRAW`. Đọc trạng thái yêu cầu rút qua API hiện có:

```http
GET /api/v1/wallets/withdrawals?page=0&size=10
```

Trong chi tiết `WITHDRAW`, hiện chú thích:

> Đây là khoản tiền đã trừ khỏi ví cho yêu cầu rút tiền. Vui lòng xem yêu cầu rút tiền để biết trạng thái chuyển khoản ngân hàng.

**Phí hoàn hàng:** `RETURN_SHIPPING_FEE` chỉ thể hiện tiền đã thu thật. Ví dụ phí thực tế 30.000đ, ví seller có 10.000đ: dòng ví là `amount=-10000`, số dư sau là `0`; phần nợ 20.000đ theo dõi ở chức năng công nợ, không tạo thêm một khoản trừ giả trong lịch sử ví.

`REFUND` với `referenceType=WITHDRAWAL` là hoàn tiền vì yêu cầu rút bị từ chối; giữ mô tả BE để người dùng phân biệt với hoàn tiền đơn hàng.

## 7. Xử lý số dư 0 và giao dịch cũ

Migration V60 thêm `balance_before`, `balance_after`, `reference_type`. Giao dịch có từ trước có thể có hai số dư là `null`, nguồn là `UNKNOWN`; migration không tự khôi phục số dư lịch sử.

### 7.1. Ví dụ giao dịch cũ

```json
{
  "id": "10000000-0000-4000-8000-000000000002",
  "walletId": "20000000-0000-4000-8000-000000000001",
  "amount": -255600.00,
  "type": "PAYMENT",
  "referenceId": "30000000-0000-4000-8000-000000000002",
  "createdAt": "2026-10-21T10:42:03Z",
  "currency": "VND",
  "direction": "DEBIT",
  "description": "Thanh toán từ ví",
  "referenceType": "UNKNOWN",
  "balanceBefore": null,
  "balanceAfter": null
}
```

Hiển thị số dư là **“Chưa được ghi nhận”**, không phải `0đ`. Chi tiết hiển thị thêm:

> Số dư tại thời điểm phát sinh giao dịch này chưa được ghi nhận.

Nếu trang đang xem có giao dịch thiếu `balanceAfter`, hiển thị thông báo:

> Một số giao dịch trong danh sách chưa ghi nhận số dư tại thời điểm phát sinh. Số dư sẽ hiển thị khi giao dịch có thông tin này.

Quyết định theo dữ liệu `null`/thiếu field thực tế, không dùng ngày tạo làm điều kiện duy nhất. Dữ liệu có thể được chuyển từ môi trường khác hoặc có thời gian không đồng bộ.

### 7.2. Ví dụ giao dịch có số dư sau bằng 0

```json
{
  "id": "10000000-0000-4000-8000-000000000003",
  "walletId": "20000000-0000-4000-8000-000000000001",
  "amount": -50000.00,
  "type": "WITHDRAW",
  "referenceId": "30000000-0000-4000-8000-000000000003",
  "createdAt": "2026-10-25T04:00:00Z",
  "currency": "VND",
  "direction": "DEBIT",
  "description": "Trừ tiền ví cho yêu cầu rút về ngân hàng",
  "referenceType": "WITHDRAWAL",
  "balanceBefore": 50000.00,
  "balanceAfter": 0.00
}
```

Phải hiển thị **0đ**. Đoạn render phù hợp với helper hiện có:

```tsx
const balanceText = transaction.balanceAfter == null
  ? 'Chưa được ghi nhận'
  : formatWalletMoney(transaction.balanceAfter, lang);
```

Không dùng `balanceAfter || ...`, vì `0` cũng là giá trị falsy. Không dùng `Number(null)` để chuẩn hóa tiền, vì kết quả là `0` và sẽ làm sai ý nghĩa dữ liệu.

Không lấy số dư ví hiện tại, cộng/trừ toàn bộ `amount` của trang đang xem để tính số dư cũ. Danh sách có lọc và phân trang, còn lịch sử có thể không đầy đủ để suy ngược chính xác.

## 8. Thiết kế các trạng thái giao diện

### 8.1. Danh sách

Mỗi dòng có:

1. Icon và tên loại giao dịch.
2. `description` do BE trả về.
3. Số tiền có dấu.
4. Thời gian phát sinh.
5. Số dư sau giao dịch hoặc nhãn chưa ghi nhận.
6. Nút “Xem chi tiết”; có thể cho phép bấm cả dòng như FE hiện tại.

Danh sách cuộn bên trong popup. Trên mobile cho phép nội dung xuống dòng, giữ số tiền dễ đọc và các nút điều khiển có thể truy cập.

### 8.2. Chi tiết

Hiển thị loại, chiều tiền, số tiền, thời gian, số dư trước/sau, nguồn, nội dung, đơn vị tiền, mã giao dịch, mã tham chiếu và mã ví. Mã UUID cần xuống dòng được, có thể bổ sung nút copy.

Giữ filter và trang đang xem khi quay lại từ chi tiết. Không hiện lại nội dung của giao dịch trước trong khi đang tải một giao dịch khác.

### 8.3. Điều kiện các nút

| Thành phần | Hiển thị / hoạt động |
| --- | --- |
| Áp dụng | Khi gửi thì xác thực khoảng ngày; reset `page=0`; khóa trong lúc tải |
| Xóa bộ lọc | Xóa loại/ngày, reset `page=0`, tải lại danh sách; khóa trong lúc tải |
| Làm mới | Tải lại dữ liệu của bộ lọc/trang đang áp dụng; khóa trong lúc tải |
| Trang trước | Khóa khi `page===0` hoặc request đang tải |
| Trang sau | Khóa khi `isLast===true`, `page+1>=totalPages` hoặc request đang tải |
| Xem chi tiết | Có ở từng giao dịch đã tải hợp lệ; gọi API bằng `id` |
| Quay lại lịch sử | Có ở màn hình chi tiết, kể cả khi chi tiết lỗi |
| Thử lại danh sách | Chỉ hiện khi tải danh sách lỗi; dùng lại query hiện tại |
| Thử lại chi tiết | Chỉ hiện khi tải chi tiết lỗi; dùng lại transaction ID đã chọn |

Khi đang tải, FE hiện tại thay vùng danh sách và phần phân trang bằng loading; vì vậy không thể bấm chuyển trang trong thời gian này.

Không thêm nút chỉnh sửa hoặc xóa giao dịch vào màn hình đọc lịch sử này.

## 9. Bộ lọc ngày và múi giờ

Người dùng chọn ngày lịch, ví dụ từ `25/10/2026` đến `25/10/2026`. Mục tiêu là bao gồm cả ngày kết thúc đã chọn.

Nếu trình duyệt chạy ở giờ Việt Nam UTC+7, gửi:

```http
GET /api/v1/wallets/me/transactions?type=PAYMENT&from=2026-10-24T17:00:00.000Z&to=2026-10-25T17:00:00.000Z&page=0&size=10&sort=createdAt,desc
```

Khoảng tương ứng:

```text
00:00:00 ngày 25/10/2026 Việt Nam ≤ createdAt < 00:00:00 ngày 26/10/2026 Việt Nam
```

FE hiện tại đã có helper:

```ts
const range = walletDateRange('2026-10-25', '2026-10-25');

const page = await walletService.getTransactions({
  page: 0,
  size: 10,
  type: 'PAYMENT',
  ...range,
});
```

`walletDateRange` dùng ngày theo múi giờ của trình duyệt và đổi sang UTC. Không ép thêm `Z` vào chuỗi ngày người dùng nhập rồi coi đó là nửa đêm Việt Nam. Nếu sản phẩm sau này yêu cầu cố định lịch Việt Nam cho người dùng ở mọi quốc gia, cần đồng bộ cả nhãn múi giờ, bộ lọc và phần hiển thị thời gian.

Các quy tắc cần giữ:

- Cho phép chọn cùng ngày; `to` gửi sang BE là đầu ngày kế tiếp.
- Nếu Từ ngày lớn hơn Đến ngày, báo lỗi tại form và không gửi request.
- Không chọn loại thì bỏ `type`, không gửi `type=ALL` hoặc `type=string`.
- Không chọn ngày thì bỏ tham số tương ứng, không gửi chuỗi rỗng.
- Thay filter phải quay về trang 0.
- Dùng `URLSearchParams` để encode query.
- Dùng `totalElements` BE trả về làm tổng số giao dịch phù hợp, không dùng `items.length` thay thế.
- Giữ thứ tự server; không sắp xếp lại riêng từng trang ở FE.

## 10. Tích hợp với code FE hiện tại

### 10.1. Dùng service và helper có sẵn

Ví dụ các import sau dành cho một component đặt trong `src/components/modals/`:

```ts
import { walletService } from '../../services/walletService';
import {
  formatWalletMoney,
  walletDateRange,
  walletReferenceLabel,
  walletTransactionLabel,
} from '../../utils/walletTransactions';
```

Gọi danh sách:

```ts
const page = await walletService.getTransactions({
  page: 0,
  size: 10,
  type: 'RETURN_SHIPPING_FEE',
});

// page.items, page.totalElements, page.totalPages, page.isLast
```

Gọi chi tiết:

```ts
const detail = await walletService.getTransactionDetail(transaction.id);
```

Hai method trên nhận `AbortSignal` ở tham số thứ hai. Khi đổi filter, đổi giao dịch chi tiết, đóng component hoặc đổi tài khoản, hủy request cũ và không cập nhật state từ response đã hết hiệu lực.

`getTransactionHistory()` cũ vẫn được giữ để tương thích các caller cần một mảng. Method này lấy trang đầu với `size=50`; **màn hình cần phân trang phải dùng `getTransactions()`** để có metadata và tải đúng trang.

### 10.2. Chuẩn hóa dữ liệu

`normalizeWalletTransaction()` đã xử lý:

- Bảo toàn dấu của `amount` và suy ra chiều tiền nhất quán.
- Chấp nhận giá trị tiền dạng number hoặc chuỗi số hợp lệ khi chuẩn hóa.
- Giữ số dư thiếu/null là `null`; chuỗi `"0.00"` trở thành số `0`.
- Từ chối số tiền thiếu, chuỗi rỗng hoặc không hữu hạn; không âm thầm biến thành `0`.
- Không tự tạo trạng thái `SUCCESS`.
- Field nguồn thiếu được hiển thị bằng `UNKNOWN`.

`normalizeWalletTransactionPage()` đọc `items` theo contract hiện tại. Helper còn hỗ trợ dạng `content` và lớp bọc `data` để tương thích response cũ; không dùng khả năng tương thích này làm lý do thay đổi contract BE hoặc dựng thêm lớp bọc ở component.

Định dạng tiền dùng `formatWalletMoney()`: tiền VND, giữ tối đa hai chữ số thập phân theo dữ liệu BE hỗ trợ. Không làm tròn mọi số tiền về số nguyên nếu API có phần lẻ.

### 10.3. Gắn panel vào popup ví

Trong `TopUpModal.tsx` đã có:

```tsx
{activeTab === 'history' && (
  <WalletHistoryPanel
    key={currentUser?.id ?? 'wallet-history'}
    lang={lang}
    refreshKey={historyRefreshKey}
  />
)}
```

`key` theo tài khoản giúp reset state khi chuyển user. Khi xử lý đăng xuất/đổi tài khoản cần đóng hoặc làm mới popup, không để lịch sử của phiên cũ còn hiển thị.

`refreshKey` dùng để yêu cầu panel tải lại danh sách từ nút làm mới ở popup cha. Nút làm mới bên trong panel cũng tải lại danh sách hiện tại.

### 10.4. Đọc số dư hiện tại riêng

Header ví tiếp tục đọc:

```http
GET /api/v1/wallets/me
```

Ví dụ response:

```json
{
  "id": "20000000-0000-4000-8000-000000000001",
  "userId": "40000000-0000-4000-8000-000000000001",
  "balance": 70000.00,
  "updatedAt": "2026-10-25T03:30:00Z"
}
```

Số dư header là số dư hiện tại. `balanceAfter` trong mỗi giao dịch là số dư tại thời điểm giao dịch đó được ghi nhận. Hai giá trị có thể khác nhau khi đã phát sinh giao dịch tiếp theo.

## 11. Xử lý lỗi

| HTTP / tình huống | Hành vi FE |
| --- | --- |
| `200`, `items=[]` | Hiện trạng thái không có giao dịch phù hợp |
| `400` | Kiểm tra enum, khoảng thời gian, UUID hoặc sort; báo lỗi phù hợp |
| `401` | Dùng cơ chế refresh/đăng nhập của `apiClient`; không biến thành danh sách rỗng |
| `403` | Báo không có quyền truy cập; không tự đổi ID/token để thử đọc dữ liệu khác |
| `404` ở chi tiết | Báo không tìm thấy giao dịch, cho phép quay lại/thử lại |
| `5xx` hoặc lỗi mạng | Hiện lỗi và nút thử lại; không kết luận user chưa có giao dịch |
| Request bị hủy khi đổi màn hình/filter | Không hiện toast lỗi cho thao tác hủy chủ động |
| JSON không đúng cấu trúc hoặc tiền không hợp lệ | Báo không đọc được dữ liệu, không dựng số tiền mặc định |

Ví dụ lỗi sort không hỗ trợ:

```json
{
  "success": false,
  "message": "Unsupported wallet transaction sort field: string",
  "path": "/api/v1/wallets/me/transactions",
  "errors": [],
  "timestamp": "2026-10-25T10:30:00"
}
```

Ví dụ lỗi giao dịch không tồn tại/không thuộc user:

```json
{
  "success": false,
  "message": "Wallet transaction not found",
  "path": "/api/v1/wallets/me/transactions/10000000-0000-4000-8000-000000000001",
  "errors": [],
  "timestamp": "2026-10-25T10:30:00"
}
```

Body lỗi có thể có dạng khác ở tầng xác thực hoặc khi BE không truy cập được. Xử lý HTTP status và thông báo dự phòng qua helper hiện có.

## 12. Quy trình test trên Swagger và FE

### 12.1. Chuẩn bị

1. Chạy BE với code mới; Flyway cần áp dụng V60 trên database đang dùng.
2. Nếu BE vẫn chạy bản cũ, build và khởi động lại để nạp API/DTO/service mới. Không sửa migration đã áp dụng để cố điền số dư cũ.
3. Mở Swagger tại `http://localhost:8080/swagger-ui/index.html`.
4. Đăng nhập tài khoản Buyer hoặc Seller bằng luồng hiện có và bấm Authorize.
5. Mở FE, đăng nhập đúng tài khoản đang test và vào tab Lịch Sử Giao Dịch.

### 12.2. Test đọc lịch sử

Trong `GET /api/v1/wallets/me/transactions`, điền:

```text
page: 0
size: 10
sort: createdAt,desc
type: bỏ trống
from: bỏ trống
to: bỏ trống
```

Không có JSON body. Kiểm tra JSON có `items`, thông tin phân trang và các field mới ở từng giao dịch.

### 12.3. Test một ngày theo giờ Việt Nam

```text
page: 0
size: 10
sort: createdAt,desc
type: PAYMENT
from: 2026-10-24T17:00:00Z
to: 2026-10-25T17:00:00Z
```

Trên FE chọn loại Thanh toán, từ 25/10/2026 đến 25/10/2026, bấm Áp dụng. Khi trình duyệt ở UTC+7, kiểm tra Network gửi cùng hai mốc UTC trên.

### 12.4. Test chi tiết

1. Copy `items[0].id` từ response thật.
2. Gọi `GET /api/v1/wallets/me/transactions/{transactionId}` bằng ID đó.
3. Đối chiếu số tiền, mô tả, nguồn và số dư với dòng danh sách.
4. Bấm Xem chi tiết trên FE và xác nhận có request chi tiết tương ứng trong Network.
5. Quay lại: filter và trang trước đó vẫn được giữ.

### 12.5. Test số dư của giao dịch mới

Ở tài khoản/môi trường test, thực hiện một luồng hiện có làm thay đổi ví, ví dụ thanh toán đơn hàng bằng ví sau khi đã có đủ số dư. Chỉ tạo yêu cầu nạp tiền chưa đồng nghĩa với nạp thành công; phải hoàn tất bước xác nhận nạp theo luồng đang có.

Ví dụ trước thanh toán ví có 100.000đ, thanh toán 30.000đ. Dòng giao dịch mới phải có:

```json
{
  "amount": -30000.00,
  "balanceBefore": 100000.00,
  "balanceAfter": 70000.00
}
```

Đây là phần trích response để kiểm tra, không phải JSON gửi vào API tạo giao dịch. Không có API công khai trong tính năng này cho phép FE tự ghi `balanceBefore`/`balanceAfter`.

Nếu giao dịch mới thực sự tạo bởi service hiện tại vẫn trả số dư null, kiểm tra code BE đang chạy và response Network; không xử lý bằng cách gán `0` trên FE.

### 12.6. Test dữ liệu cũ và số dư 0

- Giao dịch cũ có số dư null: dòng và chi tiết hiện “Chưa được ghi nhận”, có chú thích phù hợp.
- Giao dịch hợp lệ có `balanceAfter=0`: hiện 0đ, không hiện nhãn thiếu dữ liệu.
- Nguồn `UNKNOWN`: hiện “Chưa có thông tin”, không đoán là đơn hàng.
- Xem lịch sử cũ sau khi refresh/restart: không kỳ vọng migration tự khôi phục số dư đã thiếu.

### 12.7. Checklist nghiệm thu

| Trường hợp | Kết quả mong đợi |
| --- | --- |
| Mở lịch sử khi đã đăng nhập | Gửi page 0, size 10, sort thời gian giảm dần |
| Lọc từng loại giao dịch | Chỉ gửi enum hợp lệ; tổng và số trang theo response BE |
| Xóa bộ lọc khi đang ở trang sau | Trở lại page 0 và tải lại |
| Lọc cùng một ngày | Bao gồm cả ngày được chọn theo múi giờ hiển thị |
| Chọn ngày bắt đầu sau ngày kết thúc | Báo lỗi tại form, không gửi request |
| Chuyển trang | Dùng query mới; không sort hoặc tính tổng lại từ trang con |
| Giao dịch cộng/trừ tiền | Dấu và màu nhất quán với `amount` |
| Có phần thập phân như 1000.05 | Hiển thị đúng, không mất phần lẻ |
| Số dư sau bằng 0 | Hiện 0đ |
| Số dư null/field thiếu | Hiện chưa ghi nhận, không đổi thành 0 |
| Bấm chi tiết liên tục trên các dòng | Không để response cũ ghi đè giao dịch đang chọn |
| Chi tiết trả 404 | Hiện lỗi, có nút quay lại/thử lại |
| Danh sách trả lỗi mạng/5xx | Hiện lỗi, không hiển thị như danh sách rỗng |
| Request cũ bị hủy khi đổi filter | Không báo lỗi giả hoặc ghi đè kết quả mới |
| Xem `WITHDRAW` | Không tự hiển thị ngân hàng đã chuyển thành công |
| Phí ship seller trả một phần, phần còn lại là nợ | Lịch sử ví chỉ hiện phần đã trừ thật |
| Đổi tài khoản/đăng xuất | Không còn lịch sử của user trước |
| Gọi ID của tài khoản khác bằng Swagger | 404, không tiết lộ dữ liệu |
| Mobile | Không tràn ngang, có thể đóng popup và thao tác các nút |

### 12.8. Lệnh kiểm tra FE

Chạy tại root repository FE:

```powershell
node --import tsx --test src/tests/walletTransactions.test.ts
npm run build
npm run lint
```

Trong repo này, `npm run lint` chạy `tsc --noEmit`. Nếu lệnh báo lỗi, đối chiếu vị trí lỗi với thay đổi đang làm; build Vite thành công không thay thế kiểm tra kiểu TypeScript.

## 13. Mã nguồn và tài liệu BE để đối chiếu

Các đường dẫn dưới đây tính từ root dự án BE `secondLife`:

- `src/main/java/com/secondlife/secondlife/controller/WalletController.java`: endpoint, query, xác thực.
- `src/main/java/com/secondlife/secondlife/dto/response/WalletTransactionResponseDTO.java`: contract JSON giao dịch.
- `src/main/java/com/secondlife/secondlife/common/PageResponse.java`: contract phân trang.
- `src/main/java/com/secondlife/secondlife/service/impl/WalletQueryServiceImpl.java`: lọc, sort, ownership của API đọc.
- `src/main/java/com/secondlife/secondlife/mapper/WalletMapper.java`: mô tả, chiều tiền và ánh xạ DTO.
- `src/main/java/com/secondlife/secondlife/service/impl/WalletServiceImpl.java`: ghi số dư trước/sau cùng biến động ví.
- `src/main/resources/db/migration/V60__wallet_transaction_details.sql`: schema bổ sung và quy tắc dữ liệu cũ.
- [Hướng dẫn API BE](BE_WALLET_TRANSACTION_HISTORY_GUIDE.md).
- [Bằng chứng kiểm thử BE](WALLET_TRANSACTION_HISTORY_VERIFICATION.md).
