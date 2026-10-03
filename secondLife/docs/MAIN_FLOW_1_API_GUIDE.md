# Main Flow 1 — Đăng tin và định giá AI: hướng dẫn FE/test

Base URL: `http://localhost:8080`. API có token dùng header `Authorization: Bearer <accessToken>`.
FE đọc quyền từ profile/quyền hiện tại; backend vẫn kiểm tra quyền và chủ sở hữu cho từng request.

## Luồng màn hình

Chọn danh mục + loại sản phẩm + ảnh → tạo draft → chat bổ sung thông tin → kết thúc chat lấy mô tả
→ lưu tiêu đề/mô tả/tình trạng → định giá AI (tùy chọn) → xem khoảng giá/lịch sử → chọn giá bán
→ gửi đăng → hiển thị ACTIVE, PENDING_INSPECTION hoặc REJECTED.

- Draft/chat/kết thúc chat/lưu draft/xem lịch sử: không trừ credit.
- Mỗi **định giá thành công** với `requestId` mới: trừ đúng 1 `VALUATION`.
- Gửi đăng được chấp nhận lần đầu: trừ đúng 1 `LISTING`; hàng giá cao có trạng thái `PENDING_INSPECTION`.
- Giá bán do người bán quyết định; backend không tự đổi giá bán sang giá gợi ý.
- Người gửi đăng phải có hồ sơ seller `APPROVED`; tài khoản seller seed chỉ có role chưa đủ điều kiện này.
- Số dư dùng `credit_balances`, lịch sử dùng `credit_ledger`; không chuyển số dư `user_credits` cũ.
- Giữ ảnh đơn hiện có. Phạm vi này không thêm re-up, bộ ảnh/bằng chứng sở hữu hoặc thanh toán phí kiểm định.

## API và quyền

| Method | Path | Quyền | Dữ liệu trả về |
|---|---|---|---|
| GET | `/api/v1/categories` | Theo cấu hình public hiện có | Mảng danh mục |
| GET | `/api/v1/items/category/{categoryId}` | Theo cấu hình public hiện có | Mảng loại sản phẩm |
| GET | `/api/seller/credits` | `CREDIT_READ_SELF` | `data.listing`, `data.valuation` |
| POST | `/api/v1/posts/init` | `LISTING_CREATE_SELF` | DTO trực tiếp: `postId`, `sessionId`, `aiInitialMessage` |
| POST | `/api/v1/ai/chat` | `AI_CHAT_SELF` | `data.sessionId`, `data.reply` |
| POST | `/api/v1/posts/finalize-chat/{sessionId}` | `LISTING_CREATE_SELF` | DTO trực tiếp: `description`; `suggestedPrice` null/không có |
| GET | `/api/v1/posts/{postId}` | `LISTING_CREATE_SELF` | `data`: chi tiết bài của người gọi |
| PUT | `/api/v1/posts/{postId}/draft` | `LISTING_CREATE_SELF` | `data`: chi tiết đã lưu |
| POST | `/api/v1/posts/{postId}/ai-price-estimation` | `LISTING_VALUATION_SELF` | `data`: một kết quả định giá |
| GET | `/api/v1/posts/{postId}/ai-price-estimation` | `LISTING_VALUATION_SELF` | `data`: kết quả mới nhất |
| GET | `/api/v1/posts/{postId}/ai-price-estimation/history?page=0&size=20` | `LISTING_VALUATION_SELF` | `data.content`, `data.totalElements`, các thông tin phân trang |
| POST | `/api/v1/posts/submit/{postId}` | `LISTING_PUBLISH_SELF` | DTO trực tiếp: `status`, `inspectionRequired`, phí, `message` |

Permission mới được V19 gắn mặc định cho SELLER và ADMIN. Quyền ADMIN vẫn phải tuân theo ownership;
ADMIN không dùng các API SELF để thao tác bài của người khác. BUYER/STAFF không có các quyền đăng/định giá mặc định.

Các endpoint mới dùng envelope `{ "success": true, "message": "...", "data": {...}, "timestamp": "..." }`.
Các endpoint posts cũ giữ response trực tiếp để hạn chế phá FE hiện có.

## 1. Tạo draft — multipart/form-data

Lấy UUID danh mục/loại sản phẩm thực từ API danh mục. Đây **không phải JSON**, vì có file ảnh.

| Field | Ý nghĩa |
|---|---|
| `categoryId` | UUID danh mục tồn tại, bắt buộc |
| `itemId` | UUID loại sản phẩm, không bắt buộc; nếu gửi phải thuộc danh mục |
| `image` | File JPEG/PNG/WebP, bắt buộc, tối đa 10 MB |

```javascript
const form = new FormData();
form.append('categoryId', categoryId);
if (itemId) form.append('itemId', itemId);
form.append('image', imageFile);
const res = await fetch(`${baseUrl}/api/v1/posts/init`, {
  method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: form
});
const draft = await res.json(); // draft.postId và draft.sessionId
```

Không tự đặt header Content-Type cho FormData. Backend upload ảnh, phân tích ngoại hình, trả câu hỏi bổ sung.
Nếu upload/AI thất bại, draft chưa được commit; một ảnh upload thành công trước khi AI lỗi có thể còn trên Cloudinary.
Mỗi tài khoản tối đa 30 bài DRAFT/REJECTED chưa được gửi thành công.

## 2. Chat và hoàn thiện mô tả

`POST /api/v1/ai/chat` dùng form-data hoặc form-urlencoded:

```text
sessionId = <sessionId từ init>
message = Tủ lạnh Panasonic 250L, sử dụng 2 năm, máy chạy tốt, có xước nhẹ ở cửa, hết bảo hành.
```

`postId` chỉ dùng khi tạo phiên chat mới gắn với draft; khi tiếp tục nên gửi `sessionId` đã nhận.
Chat thu thập thông tin/mô tả, không phải chức năng định giá.
Giới hạn 4.000 ký tự/tin, 30 tin người dùng/phiên (bao gồm tin phân tích ảnh đầu tiên), 20 tin/tài khoản/phút.
Hết giới hạn: 409, không gợi ý mua Chat Credit. Phiên đã kết thúc không nhận tin thêm.

Gọi `POST /api/v1/posts/finalize-chat/{sessionId}`, không cần body. Ví dụ response:

```json
{
  "description": "Tủ lạnh Panasonic 250L đã sử dụng 2 năm, hoạt động tốt, cửa có xước nhẹ, đã hết bảo hành.",
  "suggestedPrice": null
}
```

Mô tả được lưu vào draft. Kết thúc chat không gọi model định giá và không xóa kết quả định giá trước đó.

## 3. Lưu thông tin draft để AI có dữ liệu

`PUT /api/v1/posts/{postId}/draft`:

```json
{
  "title": "Tủ lạnh Panasonic 250L đã dùng 2 năm",
  "description": "Máy hoạt động tốt, cửa có xước nhẹ, hết bảo hành, đủ phụ kiện.",
  "itemCondition": "USED_GOOD",
  "price": null
}
```

- `title`: bắt buộc, tối đa 255 ký tự.
- `description`: bắt buộc, tối đa 10.000 ký tự.
- `itemCondition`: chuỗi tình trạng, không bắt buộc, tối đa 50 ký tự.
- `price`: giá người bán dự kiến; có thể null ở draft, nếu có phải >= 1 VND, tối đa 16 chữ số phần nguyên và 2 chữ số thập phân.

PUT thay thế các field trên; field không gửi như `price` sẽ thành null. Ảnh/danh mục/loại sản phẩm giữ từ init.
Chỉ DRAFT/REJECTED chưa từng charge mới được sửa hoặc định giá mới; bài đã gửi không có chức năng re-up.

## 4. Định giá AI

`POST /api/v1/posts/{postId}/ai-price-estimation`:

```json
{
  "requestId": "05ad67bc-1065-4550-96ae-a72c534e64f0"
}
```

`requestId`: UUID do FE tạo (`crypto.randomUUID()`) cho **một lần định giá**.
Backend lấy tiêu đề, mô tả, tình trạng, danh mục, loại sản phẩm và URL ảnh đã lưu làm snapshot;
model định giá hiện tại xử lý dữ liệu dạng text, không tải ảnh trực tiếp tại endpoint này.
AI có thể yêu cầu bổ sung thông tin và trả 502 nếu không đủ dữ liệu để tạo khoảng giá hợp lệ.

Ví dụ `data` (giá do model quyết định, ví dụ không đảm bảo đúng giá thị trường):

```json
{
  "estimateId": "5e5bd5d5-e7c1-4d79-a6e4-6ed178c304df",
  "postId": "8e4e2841-b17f-47d1-b5e7-8fd8a43d1c66",
  "requestId": "05ad67bc-1065-4550-96ae-a72c534e64f0",
  "fairPriceMin": 1800000.00,
  "fairPriceMax": 2400000.00,
  "suggestedPrice": 2100000.00,
  "currency": "VND",
  "modelVersion": "gemma4:31b-cloud",
  "expectedSellTime": "1-2 tuần",
  "createdAt": "2026-10-02T14:00:00Z"
}
```

| Field | Ý nghĩa |
|---|---|
| `estimateId` | ID kết quả audit |
| `postId` | Bài được định giá |
| `requestId` | Khóa retry của lần định giá |
| `fairPriceMin`, `fairPriceMax` | Khoảng giá tham khảo |
| `suggestedPrice` | Giá gợi ý nằm trong khoảng, không tự thay giá bán |
| `currency` | Luôn VND |
| `modelVersion` | Tên/tag model được dùng; không phải bằng chứng dữ liệu thị trường thời gian thực |
| `expectedSellTime` | Thời gian bán dự kiến, có thể null |
| `createdAt` | Thời điểm lưu kết quả |

FE giữ cùng requestId khi retry do mạng lỗi/timeout. Nếu kết quả đã commit và dữ liệu sản phẩm không đổi,
backend trả lại cùng estimateId, không gọi model/trừ credit lại. Đổi tiêu đề/mô tả/tình trạng/danh mục/ảnh rồi
gửi lại requestId cũ: 409. Định giá lại sau khi sửa: dùng UUID mới, mất thêm 1 VALUATION.
Thay riêng giá bán không đổi đầu vào định giá.

Backend chạy đồng bộ; credit debit, ledger và kết quả commit cùng giao dịch. Mọi lỗi provider/giá sai trước commit
rollback debit và ledger, có thể retry cùng requestId. Nếu process chết sau model nhưng trước commit,
retry có thể chạy model lần nữa; không có job xử lý bất đồng bộ trong phạm vi này.
Kết quả cùng snapshot được giữ trong DB và chống UPDATE/DELETE; không có API sửa/xóa lịch sử.

## 5. Chốt giá và gửi đăng

`POST /api/v1/posts/submit/{postId}`:

```json
{
  "title": "Tủ lạnh Panasonic 250L đã dùng 2 năm",
  "description": "Máy hoạt động tốt, cửa có xước nhẹ, hết bảo hành, đủ phụ kiện.",
  "price": 2200000
}
```

`title`/`description` như draft; `price` bắt buộc và cùng quy tắc số dương. Không cần gửi giá AI, requestId hay số credit.
Định giá AI tùy chọn: có thể gửi đăng mà không mua VALUATION, nếu có đủ LISTING và đáp ứng điều kiện còn lại.

Backend kiểm tra owner → trạng thái → seller APPROVED → ảnh → giới hạn gửi đăng → trùng lặp → AI moderation
→ trừ LISTING → chuyển ACTIVE hoặc PENDING_INSPECTION. AI phản hồi không đúng định dạng không được coi là duyệt.

- Kiểm tra trùng hiện tại: trong các bài ACTIVE/PENDING/PENDING_INSPECTION của cùng seller, chặn ảnh có SHA-256 giống nhau
  hoặc tiêu đề + mô tả sau chuẩn hóa giống nhau trong cùng category/item. Đây là kiểm tra trùng chính xác;
  chưa có phát hiện ảnh crop/re-encode bằng embedding hoặc kết luận gian lận giữa các seller.
- Tối đa 5 lần gửi được chấp nhận/tài khoản/10 phút; retry đã nhận không tính thêm.
- AI từ chối: HTTP 200 với `status: REJECTED`, lưu lý do, không mất LISTING; FE phải kiểm tra `status`, không chỉ HTTP code.
- Chấp nhận: `status: ACTIVE` hoặc `PENDING_INSPECTION`, trừ 1 LISTING.
- Gửi lại cùng postId, tiêu đề/mô tả tương đương sau chuẩn hóa và cùng giá: trả trạng thái đã nhận, không trừ lại/tạo lại đơn kiểm định.
- Bài đã nhận mà gửi nội dung/giá khác: 409; không hỗ trợ chỉnh sửa sau publish/re-up trong luồng này.
- Giá > ngưỡng kiểm định hiện có (mặc định 5.000.000 VND): PENDING_INSPECTION. Phí trả trong response là thông tin;
  không trừ thêm LISTING để trả tiền. Thanh toán phí và hoàn credit khi kiểm định thất bại không nằm trong thay đổi này.

## Xử lý lỗi trên FE

| HTTP / outcome | Cách xử lý |
|---|---|
| 400 | Hiển thị validation errors; sửa UUID, trường bắt buộc, ảnh hoặc giá |
| 401 | Đăng nhập/refresh token |
| 403 | Thiếu quyền hoặc bài/phiên chat của người khác |
| 404 | Không có bài/phiên/kết quả định giá |
| 409 insufficient credit | Đọc lại số dư; mở luồng mua credit tương ứng |
| 409 retry conflict | Đọc lại bài; nếu muốn định giá nội dung mới, tạo requestId mới |
| 409 duplicate/rate/state | Hiển thị lý do và yêu cầu người dùng điều chỉnh hoặc chờ |
| 415 | Sai Content-Type; `/posts/init` cần multipart/form-data |
| 502 | AI lỗi/kết quả không hợp lệ; credit của thao tác chưa commit không bị trừ |
| 200 + REJECTED | Hiển thị lý do moderation, cho sửa draft và gửi lại |

Sau định giá/gửi đăng, FE gọi lại `GET /api/seller/credits` và `/api/seller/credit-ledger` để hiển thị số dư/lịch sử thực.
Không tự trừ credit trên FE khi request timeout.

## Khởi chạy và kiểm thử backend

Khởi động lại backend theo cách đang dùng, với `FLYWAY_ENABLED=true` để Flyway áp dụng V19/V20.
V19 tạo schema/quyền định giá; V20 bổ sung audit constraints/trigger và chính sách gắn quyền.
V19 giữ nguyên checksum `1319288901` đã áp dụng; không chạy repair hoặc xóa lịch sử Flyway.
Chỉ Hibernate update không thay thế được các migration này.
Model Ollama/Gemini và Cloudinary dùng cấu hình hiện có.
Chưa có bước tự động chuyển legacy credit. Dùng luồng mua credit hiện có để có LISTING/VALUATION.

```powershell
mvn -q '-Dtest=MainFlowDescriptionTest,OllamaAiPriceProviderTest,MainFlow1IntegrationTest,MainFlowV19MigrationTest' test
```

Test integration dùng PostgreSQL Testcontainers tách biệt và mock AI/Cloudinary. Docker phải hoạt động;
test có thể bị skip khi không có Docker. Không dùng database ứng dụng để test cạnh tranh credit.
