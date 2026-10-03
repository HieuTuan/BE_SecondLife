# Main Flow 1 — Đăng tin và nhận định giá AI: hướng dẫn FE/test

Base URL: `http://localhost:8080`.
Swagger: `http://localhost:8080/swagger-ui/index.html`.

## 1. Luồng UI và nguyên tắc

**Chọn category/item → tải 3–6 ảnh → AI mô tả → chỉnh sửa/chat nếu cần → lưu draft → xác nhận mô tả → định giá AI (tùy chọn) → chọn giá bán → gửi đăng → duyệt/kiểm định nếu cần → ACTIVE.**

- API seller dùng token của seller sở hữu bài: `Authorization: Bearer <accessToken>`.
- Seller phải có hồ sơ xác minh `APPROVED` trước khi gửi đăng; có role SELLER thôi chưa đủ.
- Tạo draft, chat, tổng hợp mô tả, lưu/xác nhận mô tả và xem kết quả: không trừ credit.
- Định giá thành công: trừ 1 `VALUATION`; retry hợp lệ cùng `requestId` không trừ lại.
- Chỉ khi bài chuyển `ACTIVE` mới trừ 1 `LISTING`. `PENDING`, `PENDING_INSPECTION`, từ chối hoặc xử lý lỗi không trừ credit đăng bài mới.
- Giá bán do seller quyết định, không bắt buộc bằng giá AI đề xuất.
- Bài đã đăng không hỗ trợ re-up. Retry gửi đăng cùng dữ liệu trả kết quả trước đó.
- Lưu `postId` và `sessionId` để mở lại draft. Không tạo draft mới mỗi khi người dùng chuyển bước hoặc reload trang.
- Các UUID trong hướng dẫn phải thay bằng UUID thực tế từ response.

### Điều kiện chạy AI

Luồng hiện dùng Google Gemini cho phân tích ảnh và định giá có ảnh; Ollama cho chat chữ, tổng hợp mô tả và sinh câu hỏi bổ sung.
Nếu cấu hình Ollama là `http://127.0.0.1:11434`, phải có dịch vụ Ollama chạy tại địa chỉ đó. `Connection refused` nghĩa là không kết nối được dịch vụ; có thể gặp cả ở bước init khi cần sinh câu hỏi.

Ảnh upload mới lưu `secure_url` HTTPS. URL HTTP Cloudinary cũ được đổi sang HTTPS trước khi định giá, nhưng vẫn phải thuộc đúng `CLOUDINARY_CLOUD_NAME` của BE.
Test tự động có mock AI/Cloudinary; chạy test qua không xác nhận dịch vụ AI thực tế đang hoạt động.

## 2. Chuẩn bị credit

Kiểm tra số dư bằng token SELLER:

```http
GET /api/seller/credits
```

Để test đủ luồng cần ít nhất 1 LISTING và 1 VALUATION. Nếu bỏ qua định giá AI thì không cần VALUATION.

Nếu cần mua:

```http
POST /api/seller/credit-purchases
```

```json
{
  "listingQuantity": 2,
  "valuationQuantity": 2
}
```

API tạo giao dịch mua; thanh toán thành công mới cộng credit. Không coi HTTP 201 là đã có credit.
Nếu báo `Credit pricing is not configured`, ADMIN phải cấu hình giá trước. Ví dụ giá test:

```http
PUT /api/admin/credit-pricing/LISTING
```

```json
{
  "unitPrice": 10000
}
```

```http
PUT /api/admin/credit-pricing/VALUATION
```

```json
{
  "unitPrice": 5000
}
```

## 3. Màn hình chọn sản phẩm và ảnh

### API chọn category/item

```http
GET /api/v1/categories
GET /api/v1/items/category/{categoryId}
```

Hai API trả danh sách trực tiếp. Lấy `id` của category và item. Khi đổi category, FE xóa item đã chọn và tải lại danh sách item.

### API tạo draft

```http
POST /api/v1/posts/init
Content-Type: multipart/form-data
```

Không gửi JSON. Trong Swagger điền:

| Trường | Giá trị |
|---|---|
| `categoryId` | UUID category, bắt buộc |
| `itemId` | UUID item thuộc category đã chọn; để test theo loại sản phẩm nên chọn item |
| `images` | 3–6 file ảnh JPEG/PNG/WebP, mỗi file tối đa 10 MB |

FE cho xem trước ảnh và chỉ bật nút **Tạo mô tả AI** khi hợp lệ. Khóa nút trong lúc gửi, hiển thị trạng thái đang phân tích.

Ví dụ JavaScript dùng trong React:

```javascript
const form = new FormData();
form.append("categoryId", categoryId);
if (itemId) form.append("itemId", itemId);
images.forEach(file => form.append("images", file));

const response = await fetch(`${API_URL}/api/v1/posts/init`, {
  method: "POST",
  headers: { Authorization: `Bearer ${accessToken}` },
  body: form
});
const result = await response.json();
if (!response.ok) throw new Error(result.message || "Không thể tạo draft");

setPostId(result.postId);
setSessionId(result.sessionId);
```

Không tự đặt `Content-Type` cho FormData; trình duyệt thêm boundary.
Response init là DTO trực tiếp, không nằm trong `data`: `postId`, `sessionId`, `aiInitialMessage`.

AI phân tích toàn bộ ảnh cùng category/item. Người dùng phải chọn ảnh và nội dung đúng sản phẩm; không lấy ví dụ tủ lạnh để mô tả ảnh nồi cơm điện.

## 4. Màn hình mô tả AI và chỉnh sửa

```http
GET /api/v1/posts/{postId}
```

Đọc `response.data`:

| Trường | UI / ý nghĩa |
|---|---|
| `imageUrls` | Bộ ảnh đã lưu |
| `aiDescription` | Mô tả AI đề xuất |
| `title` | Ô tiêu đề |
| `description` | Ô mô tả người dùng có thể sửa |
| `itemCondition` | Tình trạng sản phẩm |
| `descriptionAccepted` | Đã xác nhận mô tả hay chưa |
| `status` | Trạng thái draft/bài đăng |

Nút **Dùng mô tả AI** điền `aiDescription` vào ô mô tả. Người dùng có thể chỉnh sửa trực tiếp hoặc chat bổ sung.
Không tự động xác nhận mô tả khi AI trả lời; người dùng cần kiểm tra trước, nhất là khi AI báo category/item không khớp ảnh.

### Chat bổ sung — tùy chọn

```http
POST /api/v1/ai/chat
Content-Type: multipart/form-data
```

| Trường form | Giá trị test |
|---|---|
| `sessionId` | UUID từ init |
| `postId` | UUID bài từ init |
| `message` | Sản phẩm đã sử dụng 2 năm, hoạt động tốt, mặt bên có vết xước nhỏ. Hãy cập nhật mô tả. |
| `images` | Để trống nếu chỉ chat chữ; có ảnh thì chọn file thật |

API không nhận JSON. Không gửi `images: "string"`; không dùng trường file `image` cũ.
Response chat nằm trong `data`, gồm `reply` và `sessionId`.

Chat xong gọi:

```http
POST /api/v1/posts/finalize-chat/{sessionId}
```

Không cần body. Response tổng hợp là DTO trực tiếp với `description`. Gọi lại GET draft để cập nhật form. Đây chưa phải thao tác xác nhận mô tả.

## 5. Lưu draft và xác nhận mô tả

Nút **Xác nhận mô tả và tiếp tục** gọi hai API theo thứ tự; API sau chỉ chạy nếu API trước thành công.

### 5.1 Lưu thông tin

```http
PUT /api/v1/posts/{postId}/draft
```

```json
{
  "title": "Tủ lạnh Panasonic đã qua sử dụng",
  "description": "Tủ lạnh đã sử dụng 2 năm, hoạt động tốt. Mặt bên có vết xước nhỏ.",
  "itemCondition": "USED",
  "price": null
}
```

- `title`: bắt buộc, tối đa 255 ký tự.
- `description`: bắt buộc, tối đa 10000 ký tự.
- `itemCondition`: chuỗi tình trạng, tối đa 50 ký tự.
- `price`: có thể null ở bước này; nếu nhập phải >= 1, tối đa 2 chữ số thập phân.
- PUT thay thế các trường thông tin trên; FE gửi đủ thông tin đang muốn lưu.
- Thay đổi mô tả sẽ đặt `descriptionAccepted` về false.

### 5.2 Xác nhận nội dung đang hiển thị

```http
POST /api/v1/posts/{postId}/accept-description
```

```json
{
  "description": "Tủ lạnh đã sử dụng 2 năm, hoạt động tốt. Mặt bên có vết xước nhỏ."
}
```

FE nên gửi mô tả hiện tại trong form, kể cả khi dùng nguyên mô tả AI. Chỉ chuyển bước khi `data.descriptionAccepted` là true.

Nếu gửi `{}` hoặc không gửi body, BE lấy `aiDescription` thay vào `description`; không dùng cách này khi muốn giữ nội dung người dùng đã sửa.

Sau khi sửa mô tả hoặc chat tiếp phải xác nhận lại trước khi định giá/gửi đăng.

## 6. Định giá AI và chọn giá bán

Hiển thị số dư VALUATION, nút **Định giá bằng AI — 1 credit**, nút **Bỏ qua định giá AI**, và ô **Giá bán của bạn**.

```http
POST /api/v1/posts/{postId}/ai-price-estimation
```

```json
{
  "requestId": "9fc614d7-7860-41e8-94a1-416e43e9b183"
}
```

FE dùng `crypto.randomUUID()` cho một yêu cầu định giá mới. Lưu requestId của yêu cầu đang xử lý.

- Retry sau timeout/lỗi mạng dùng cùng requestId và cùng dữ liệu.
- Một lần định giá mới hoặc dữ liệu sản phẩm đã thay đổi dùng requestId mới.
- Khóa nút khi đang xử lý; không gửi nhiều requestId vì người dùng nhấn lặp.
- Thành công tải lại số dư. AI lỗi không tự điền giá mặc định.

Đọc kết quả trong `data`:

| Trường | UI |
|---|---|
| `fairPriceMin`, `fairPriceMax` | Khoảng giá tham khảo |
| `suggestedPrice` | Giá AI đề xuất |
| `expectedSellTime` | Thời gian bán dự kiến, nếu có |

Nút **Dùng giá đề xuất** điền `suggestedPrice` vào ô giá bán. Seller được sửa giá hoặc tự nhập khi bỏ qua định giá.

Xem kết quả mới nhất và lịch sử:

```http
GET /api/v1/posts/{postId}/ai-price-estimation
GET /api/v1/posts/{postId}/ai-price-estimation/history?page=0&size=20
```

Chưa có kết quả định giá thì GET kết quả mới nhất trả 404; FE hiển thị chưa định giá. Xem lịch sử không trừ credit.

## 7. Xem lại và gửi đăng

Hiển thị ảnh, tiêu đề, mô tả đã xác nhận, giá bán. Nút **Gửi đăng bài** gọi:

```http
POST /api/v1/posts/submit/{postId}
```

```json
{
  "title": "Tủ lạnh Panasonic đã qua sử dụng",
  "description": "Tủ lạnh đã sử dụng 2 năm, hoạt động tốt. Mặt bên có vết xước nhỏ.",
  "price": 1300000
}
```

Mô tả phải khớp nội dung đã xác nhận. Không sửa mô tả chỉ trong request submit; phải lưu và xác nhận lại trước.
Response submit là DTO trực tiếp: đọc `response.status`, không phải `response.data.status`.

| Trạng thái | FE hiển thị | Credit LISTING |
|---|---|---|
| `ACTIVE` | Bài đã đăng thành công | Trừ 1 lần |
| `PENDING` | Nghi trùng, đang chờ STAFF kiểm duyệt | Chưa trừ |
| `PENDING_INSPECTION` | Bài đang chờ kiểm định | Chưa trừ |
| `REJECTED` | Bị từ chối; hiển thị thông báo/lý do | Không trừ mới |

Không coi HTTP 200 là bài đã hiển thị. Chỉ thông báo đăng thành công khi ACTIVE.
Bài chờ xử lý không cho sửa thông tin/gửi lại dữ liệu khác. Retry cùng dữ liệu trả trạng thái hiện tại, không tạo thêm đơn hay trừ lại.

Theo dõi bằng nút **Kiểm tra trạng thái** hoặc polling có khoảng nghỉ và dừng khi rời màn hình:

```http
GET /api/v1/posts/{postId}
GET /api/seller/credits
GET /api/seller/credit-ledger
```

GET draft trả trạng thái ở `data.status`. Tải lại credit khi bài chuyển ACTIVE.

## 8. Nhánh STAFF xử lý nghi trùng

Dùng token STAFF có quyền `STAFF_LISTING_REVIEW`. ADMIN có `POST_REVIEW` cũng được vào các API này.

```http
GET /api/staff/listings?page=0&size=20
GET /api/staff/listings/{postId}
```

UI STAFF hiển thị ảnh/nội dung bài, `reviewReason`, và `duplicateMatches` (các UUID bài đối chiếu). Dùng API chi tiết trên để lấy từng bài đối chiếu.

Duyệt, không cần body:

```http
POST /api/staff/listings/{postId}/approve
```

Từ chối:

```http
POST /api/staff/listings/{postId}/reject
```

```json
{
  "reason": "Ảnh sản phẩm thuộc bài đăng của seller khác."
}
```

Duyệt bài giá thường chuyển ACTIVE và trừ 1 LISTING. Duyệt bài giá cao chuyển PENDING_INSPECTION, chưa trừ. Thiếu LISTING khi cần chuyển ACTIVE trả lỗi và giữ bài chờ duyệt. Từ chối không trừ credit.

### Kịch bản test trùng

1. Seller A đăng bài thành ACTIVE.
2. Seller B tạo draft mới, tải 3–6 ảnh với ít nhất một file giống ảnh A.
3. B lưu/xác nhận mô tả rồi submit; không cần gọi định giá để test nhánh này.
4. Kiểm tra bài B là PENDING, `duplicateMatches` chứa ID bài A, `listingCreditCharged` là false.
5. STAFF duyệt hoặc từ chối; kiểm tra số dư và ledger của B.
6. Gọi approve lại sau khi duyệt thành công: không trừ thêm LISTING.

Ảnh trùng được kiểm tra giữa các seller bằng SHA-256 và so khớp thị giác hỗ trợ crop/resize/re-encode. So khớp tương tự áp dụng cho ảnh có dấu vân tay thị giác mới; ảnh cũ chưa được backfill. Đây là phát hiện nghi trùng để STAFF kiểm tra, không đảm bảo nhận ra mọi ảnh chụp sản phẩm từ góc khác.

Trùng nội dung: hiện kiểm tra cùng seller, cùng category/item, tiêu đề và mô tả đã chuẩn hóa, đối chiếu bài ACTIVE/PENDING/PENDING_INSPECTION. Chỉ gửi JSON giống nhau giữa hai seller khác nhau không đủ kích hoạt trùng nội dung; hãy dùng lại file ảnh để test trùng giữa seller.

## 9. Nhánh bài giá cao đi kiểm định

Điều kiện: giá bán seller nhập lớn hơn `app.inspection.high-value-threshold`, mặc định 5000000 VND. Đúng 5000000 chưa vào nhánh; giá AI đề xuất không quyết định nhánh này.

Ví dụ submit với `price: 6000000` cùng tiêu đề/mô tả đã xác nhận:

- Nếu không nghi trùng và AI kiểm duyệt chấp thuận: tạo InspectionOrder PENDING, bài PENDING_INSPECTION.
- Nếu nghi trùng: STAFF duyệt trước, sau đó mới tạo đơn kiểm định.
- Chưa trừ LISTING, chưa hiển thị công khai.

Người có quyền `INSPECTION_ORDER_READ_ANY` xem đơn:

```http
GET /api/v1/inspector/orders/all?status=PENDING&page=0&size=20
```

Người có quyền `INSPECTION_ORDER_ASSIGN` phân công bằng query parameter, không body:

```http
POST /api/v1/inspector/orders/{orderId}/assign?inspectorId={inspectorId}
```

INSPECTOR xem đơn của mình:

```http
GET /api/v1/inspector/orders
```

INSPECTOR được phân công và có quyền `INSPECTION_REPORT_SUBMIT` nộp kết quả:

```http
POST /api/v1/inspector/orders/{orderId}/result
```

```json
{
  "status": "PASSED",
  "note": "Sản phẩm hoạt động tốt và phù hợp với mô tả."
}
```

Hoặc:

```json
{
  "status": "FAILED",
  "note": "Tình trạng sản phẩm không phù hợp với mô tả."
}
```

PASSED + AI kiểm duyệt cuối chấp thuận + đủ LISTING → ACTIVE và trừ 1 LISTING. FAILED → REJECTED, không trừ mới. AI lỗi hoặc thiếu credit khi kích hoạt không được coi là đăng thành công.
Code tạo đơn và chờ phân công; không đồng nghĩa sản phẩm đã được vận chuyển thực tế. Thanh toán phí kiểm định/vận chuyển thuộc luồng riêng.

## 10. Checklist FE/test và xử lý lỗi

| Trường hợp | Kết quả cần kiểm tra |
|---|---|
| Init 2 hoặc 7 ảnh | 400, không tạo draft hợp lệ |
| Init 3–6 ảnh hợp lệ | Có postId/sessionId, gallery đủ ảnh, không trừ credit |
| Chưa xác nhận mô tả | Không được định giá hoặc gửi đăng |
| Sửa mô tả/chat sau xác nhận | Cần xác nhận lại |
| Định giá thành công | Trừ 1 VALUATION, lưu khoảng giá/lịch sử |
| Retry định giá cùng requestId, cùng dữ liệu | Cùng kết quả, không trừ lại |
| AI định giá lỗi | Không trừ VALUATION |
| Bài thường hợp lệ | ACTIVE, trừ 1 LISTING |
| Ảnh nghi trùng | PENDING, chưa trừ LISTING |
| STAFF từ chối | REJECTED, không trừ LISTING |
| Bài giá cao | PENDING_INSPECTION, chưa trừ LISTING |
| Kiểm định đạt và được chấp thuận | ACTIVE, trừ 1 LISTING |
| Kiểm định thất bại | REJECTED, không trừ LISTING |
| Seller khác đọc/sửa draft không thuộc mình | 403 |

FE đọc `message` và `errors` của response lỗi:

- 401: xử lý phiên đăng nhập/token hết hạn.
- 403: thiếu quyền hoặc bài không thuộc người gọi.
- 400: hiển thị lỗi nhập liệu cạnh trường tương ứng khi có `errors`.
- 409: hiển thị thông báo nghiệp vụ, ví dụ thiếu credit/chưa xác nhận mô tả/trạng thái không cho sửa.
- Lỗi AI/kết nối: giữ dữ liệu trên form và cho retry đúng requestId; không tự tạo định giá mới.
- `AI valuation images must use the configured Cloudinary storage`: kiểm tra `data.imageUrls` và cloud name cấu hình; không yêu cầu người dùng nhập URL ảnh vào body định giá.

### Đối chiếu yêu cầu Main Flow 1

| Yêu cầu | Bước đáp ứng |
|---|---|
| 3–6 ảnh trước khi tạo bài | Init, bước 3 |
| Mô tả từ ảnh + category/item | Phân tích ban đầu, bước 3–4 |
| Đồng ý mô tả hoặc tự bổ sung | Form/chat và accept-description, bước 4–5 |
| Định giá từ ảnh và thông tin đã nhập | ai-price-estimation, bước 6 |
| Một lần định giá thành công trừ một credit | requestId và VALUATION, bước 6 |
| Nghi trùng ảnh chuyển STAFF | Submit và màn hình STAFF, bước 7–8 |
| Giá cao kiểm định trước khi đăng | InspectionOrder, bước 9 |
| Đăng thành công mới trừ credit | Chỉ ACTIVE, bước 7–9 |
