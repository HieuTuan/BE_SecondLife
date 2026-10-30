# Hướng dẫn FE web/mobile: validation ảnh đăng bán

Cập nhật: 28/10/2026. Đã đối chiếu controller, DTO, service, validator, error handler của BE và các điểm tích hợp trong FE web hiện tại.

Tài liệu này hướng dẫn phần **ảnh khi tạo tin đăng**. API chính là `POST /api/v1/posts/init`; không thay thế luồng upload video hoặc bằng chứng tranh chấp.

## 1. Quy tắc FE cần hiển thị và kiểm tra

| Nội dung | Mốc đang áp dụng | Cách xử lý trên FE |
|---|---|---|
| Số ảnh cho một tin mới | **3–6 ảnh** | Có thể chọn dần 1–2 ảnh nhưng chưa được gửi; chặn ảnh thứ 7 và thông báo rõ |
| Dung lượng mỗi ảnh | **Tối đa 10 MiB = 10.485.760 byte** | So sánh `file.size` theo byte; đúng mốc tối đa vẫn hợp lệ |
| Định dạng | JPEG/JPG, PNG, WebP **tĩnh** | Đuôi file, MIME và bytes thực phải khớp; `accept` chỉ hỗ trợ chọn file |
| Cạnh ngắn tối thiểu | **600 px** | `Math.min(width, height) >= 600`; 800×599 không đạt, 800×600 đạt |
| Kích thước tối đa | **16.000 px mỗi cạnh** | Kiểm tra cả width và height |
| Tổng pixel tối đa | **25.000.000 pixel** | Kiểm tra `width * height`, kể cả file nén rất nhỏ |
| File hỏng/rỗng | Không chấp nhận | Báo lỗi ngay ảnh tương ứng |
| Ảnh động | Không chấp nhận | Bao gồm GIF, APNG và WebP động; đổi đuôi không biến thành ảnh tĩnh |
| Ảnh trùng trong cùng bộ | Không chấp nhận | BE so sánh nội dung sau chuẩn hóa; đổi tên hoặc sửa metadata vẫn có thể bị chặn |
| Hướng ảnh | Theo EXIF | Preview và file gửi phải cùng hướng; không xoay pixel hai lần |
| Metadata nguồn | BE bỏ sau xử lý | Không phụ thuộc vào GPS/EXIF của ảnh đã lưu |

Thông báo gợi ý bên dưới vùng chọn ảnh:

> Chọn 3–6 ảnh sản phẩm thực tế. Hỗ trợ JPG, PNG, WebP tĩnh; tối đa 10 MiB/ảnh. Cạnh ngắn tối thiểu 600 px. Ảnh đầu tiên là ảnh đại diện.

Đây là các mốc cấu hình đang dùng để tích hợp/test, không phải hằng số không thể thay đổi. Khi BE đổi policy, FE phải đổi cấu hình tương ứng; hiện chưa có API đọc policy ảnh.

## 2. Những chỗ FE web hiện tại cần sửa

Các đường dẫn dưới đây tính từ repo `SEP490_FE_SecondLife`. Số dòng chỉ để tìm nhanh; tìm theo tên hàm nếu file đã thay đổi.

| File / vị trí đã đối chiếu | Tình trạng hiện tại | Việc cần làm |
|---|---|---|
| `src/pages/CreateListingView.tsx` — `handleFileChange`, khoảng dòng 1497 | Cắt danh sách về đủ 6 rồi tạo preview ngay | Validate từng file, báo file nào bị loại; không âm thầm bỏ file thứ 7 |
| Cùng file — `prepareImageBlobs`, khoảng dòng 1547 | Có thể dùng file chưa đủ 3; có nhánh tải lại URL preview | Với init mới, lấy đúng các `File` đã kiểm tra. Phân biệt ảnh local và URL ảnh của draft có sẵn |
| Cùng file — các nhánh khởi tạo, khoảng dòng 1584 và 1621 | Điều kiện tối thiểu còn là **1 ảnh** | Sửa cả manual và AI về 3–6 ảnh hợp lệ; không chỉ đổi dòng chữ UI |
| Cùng file — badge và nút khoảng dòng 2616, 2735, 2757 | Trạng thái hợp lệ/nút vẫn dùng mốc 1 ảnh | Dùng chung policy và điều kiện gửi ở mục 3 |
| Cùng file — `handleRemovePhoto` | Hai mảng file và preview được cập nhật theo index riêng | Dùng danh sách chung có ID ổn định; thu hồi object URL khi bỏ ảnh |
| `src/services/postService.ts` — `initPost`, khoảng dòng 203–220 | Blob/data URL bị đặt tên `.jpg` dù bytes có thể là PNG/WebP | Tạo `File` với tên và MIME đúng bytes đã encode; không gắn đuôi JPG cho mọi Blob |
| Cùng hàm — catch khoảng dòng 236 | Có fallback gửi JSON khi 415 hoặc message chứa JSON | Bỏ fallback này với endpoint hiện tại: `/posts/init` chỉ nhận multipart có ảnh |
| Cùng file — type `PostInitResponse` | `sessionId` đang là `string` | Đổi thành `string \| null`; các field AI cũng có thể null khi manual |
| `src/services/apiClient.ts` — `request` / `ApiError` | Đã có xử lý FormData và giữ `status`, `errors`, `path` | Tái sử dụng; UI cần đọc `ApiError.errors`, không chỉ toast message gộp |

Tài liệu không sửa code FE. Bảng trên là các điểm cần triển khai ở FE sau khi nhận hướng dẫn.

## 3. Luồng giao diện và trạng thái nút

1. Người dùng chọn/chụp ảnh. Hiển thị trạng thái **Đang kiểm tra ảnh…** cho từng file.
2. Kiểm tra định dạng hỗ trợ, file rỗng, dung lượng, khả năng đọc và kích thước. Xử lý từng ảnh hoặc giới hạn ít tác vụ cùng lúc để tránh tốn RAM trên mobile.
3. Hiển thị thumbnail cùng lỗi riêng. Cho xóa, thay và đổi thứ tự; ảnh đầu có nhãn **Ảnh đại diện**.
4. Chỉ cho tạo tin mới khi có danh mục hợp lệ, đủ 3–6 ảnh, tất cả ảnh đã kiểm tra xong và không có lỗi.
5. Khi bấm gửi, chụp snapshot danh sách và thứ tự ảnh. Khóa cả hai nút manual/AI, thêm/xóa/sắp xếp ảnh trong lúc request đang chạy.
6. Thành công: lưu `postId` và chuyển sang bước hiện có. Init thành công mới là tạo **draft**, chưa phải đăng bán thành công.
7. Thất bại: giữ file, preview, danh mục, hãng và model; mở lại thao tác phù hợp. Không xóa toàn bộ form.

Nên thay hai mảng file/preview bằng một nguồn dữ liệu chung:

```ts
type ListingPhoto = {
  id: string; // ID local ổn định, không dùng index làm React key.
  file: File;
  previewUrl: string;
  state: "checking" | "ready" | "invalid";
  messages: string[];
};

const LISTING_IMAGE_POLICY = {
  minCount: 3,
  maxCount: 6,
  maxBytes: 10 * 1024 * 1024,
  minShortSide: 600,
  maxDimension: 16_000,
  maxPixels: 25_000_000,
} as const;

function canInitListing(
  categoryId: string | undefined,
  photos: readonly ListingPhoto[],
  busy: boolean,
): boolean {
  return Boolean(categoryId)
    && !busy
    && photos.length >= LISTING_IMAGE_POLICY.minCount
    && photos.length <= LISTING_IMAGE_POLICY.maxCount
    && photos.every(photo => photo.state === "ready");
}
```

Đây là điều kiện cho **init tin mới**. Khi đã có `postId` hoặc khôi phục draft, tiếp tục dùng draft đó; không gọi lại init chỉ vì preview đang là URL từ server. API cập nhật draft hiện tại không được tài liệu này mở rộng thành API thay gallery.

Khi bỏ/thay ảnh, gọi `URL.revokeObjectURL()` cho object URL không còn dùng; dọn các object URL còn lại khi unmount. Không revoke URL đang hiển thị, không revoke URL HTTP của server. Sau khi xử lý file picker, reset giá trị input để có thể chọn lại cùng file vừa sửa.

## 4. Kiểm tra trước khi gửi: FE và BE phân công thế nào

| FE kiểm tra để phản hồi sớm | BE quyết định cuối cùng |
|---|---|
| Số file, file rỗng, byte, đuôi/MIME khai báo | Chữ ký định dạng thực, MIME/đuôi có khớp bytes không |
| Khả năng hiển thị/đọc kích thước ảnh | Giải mã đầy đủ, kiểm tra ảnh hỏng hoặc bị cắt cụt |
| Cạnh ngắn, cạnh tối đa, tổng pixel | Kiểm tra lại các ngưỡng trước khi decode bitmap |
| Phát hiện trùng bytes nếu client có sẵn khả năng | So sánh nội dung ảnh sau chuẩn hóa trong bộ ảnh |
| Cảnh báo định dạng không hỗ trợ | Chặn GIF/APNG/WebP động và định dạng ngoài whitelist |

Không cần tự xây lại toàn bộ parser/decoder của BE trong FE. Trình duyệt đọc được thumbnail không chứng minh file hợp lệ hoặc ảnh chỉ có một frame. FE vẫn phải xử lý lỗi BE trả về.

Không upscale ảnh 599 px lên 600 px chỉ để vượt kiểm tra; yêu cầu ảnh gốc rõ hơn. File nhỏ theo byte vẫn có thể quá lớn theo pixel. Trên mobile, tránh decode nhiều ảnh camera kích thước lớn cùng lúc.

## 5. Chuẩn hóa ảnh trên web/mobile

### 5.1 Mặc định nên gửi ảnh gốc hợp lệ

Nếu ảnh gốc đáp ứng policy, gửi trực tiếp `File`. BE đã xử lý EXIF, resize giữ tỷ lệ và encode lại không mang metadata nguồn.

Client có thể resize/nén để tiết kiệm băng thông. Đây là bước tùy chọn; phải kiểm tra **file cuối sẽ gửi**, không chỉ file trước xử lý.

### 5.2 Nếu có resize/nén trên client

- Giữ tỷ lệ, ưu tiên cạnh dài khoảng 2.560 px nhưng phải giữ cạnh ngắn >=600 px. Với ảnh quá dài, ép cạnh dài về 2.560 có thể làm cạnh ngắn không đạt; cần tính cả hai điều kiện hoặc yêu cầu ảnh có khung phù hợp hơn.
- Không tự crop mất góc sản phẩm, nhãn, serial hoặc phụ kiện. Không ép ảnh thành hình vuông chỉ để tạo thumbnail.
- Nếu giữ alpha, dùng định dạng giữ được alpha; không mặc định chuyển mọi ảnh trong suốt sang JPEG nền đen.
- Nếu SDK/browser đã áp EXIF vào pixel, output phải bỏ/reset orientation. Không vừa xoay pixel vừa giữ EXIF cũ để BE xoay lần hai.
- Nếu gửi file gốc chưa đổi pixel, giữ orientation gốc để BE xử lý. Hướng xoay 90 độ không đổi điều kiện cạnh ngắn hoặc tổng pixel.
- Quality khoảng 0,9 chỉ là gợi ý ban đầu; đo lại `size` sau encode. Nó không đảm bảo mọi ảnh luôn dưới 10 MiB.
- File nén nhỏ có thể nở khi BE chuẩn hóa, ví dụ ảnh có alpha chuyển thành PNG; vẫn phải xử lý HTTP 413 từ server.

### 5.3 HEIC/HEIF, AVIF và ảnh động

HEIC/HEIF thường gặp ở iPhone không nằm trong whitelist của endpoint này. AVIF/TIFF/SVG/GIF cũng không được nhận. Client cần chuyển đổi **thật** sang JPEG/PNG/WebP tĩnh bằng công cụ nền tảng được hỗ trợ, hoặc yêu cầu người dùng chọn định dạng khác.

Đổi `photo.heic` thành `photo.jpg`, hoặc đổi MIME mà không encode lại, sẽ bị BE chặn. Không âm thầm lấy một frame từ ảnh động rồi thông báo đây là ảnh gốc; nếu hỗ trợ chuyển thành ảnh tĩnh thì cần có thao tác rõ ràng cho người dùng.

### 5.4 Tạo File từ Blob đã encode

Chỉ dùng helper dưới đây cho Blob đã được encoder tạo ra với MIME đúng. Helper không kiểm chứng bytes và không phải bộ chuyển định dạng.

```ts
function encodedBlobToFile(blob: Blob): File {
  const mime = blob.type.toLowerCase();
  const extensions: Record<string, string> = {
    "image/jpeg": "jpg",
    "image/png": "png",
    "image/webp": "webp",
  };
  const extension = extensions[mime];
  if (!extension) {
    throw new Error("Không xác định được định dạng ảnh đã xử lý.");
  }
  return new File(
    [blob],
    `listing-${crypto.randomUUID()}.${extension}`,
    { type: mime },
  );
}
```

Ví dụ bytes là PNG thì phải gửi `*.png` với `image/png`. Nếu Blob thiếu MIME, lấy định dạng từ encoder/SDK đã dùng; không đoán JPEG chỉ vì file sẽ mang đuôi `.jpg`.

Native app phải gửi tên file thuần, ví dụ `photo-1.jpg`. Không dùng toàn bộ `content://...` hoặc đường dẫn thiết bị làm multipart filename. BE từ chối filename có `/`, `\`, `:`, ký tự điều khiển, tên trống hoặc quá 255 ký tự.

## 6. API tạo draft và multipart request

| Thuộc tính | Giá trị |
|---|---|
| Method / endpoint | `POST /api/v1/posts/init` |
| Quyền | Bearer access token có `LISTING_CREATE_SELF` |
| Request body | `multipart/form-data` |
| Thành công | HTTP **200**, DTO trực tiếp `PostInitResponse` |

| Part name | Kiểu dữ liệu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `categoryId` | UUID dạng text | Có | Lấy danh mục thật từ `GET /api/v1/categories` |
| `itemId` | UUID dạng text | Không | Nếu gửi phải thuộc danh mục đã chọn |
| `brand` | Text | Không | Giữ theo form hiện tại |
| `model` | Text | Không | Giữ theo form hiện tại |
| `mode` | `manual` hoặc `ai` | FE nên luôn gửi | Thiếu mode thì BE mặc định AI; test kỹ thuật nên chọn manual |
| `images` | File, lặp lại 3–6 part | Có | Mỗi file dùng cùng tên `images`, theo thứ tự đã chọn |

Không gửi `images` là URL, JSON/base64, đường dẫn local, hoặc JSON array. Không dùng `images[]`/`file` thay cho tên part `images` ở contract này. Không gọi API media trước rồi đưa URL vào init.

### 6.1 Mã tham khảo cho service FE web hiện tại

Tái sử dụng `request` trong `src/services/apiClient.ts`. Client hiện thêm prefix `/api`, nên đường dẫn bên trong service là `/v1/posts/init`; không vô tình tạo `/api/api/v1/...`.

```ts
import { request } from "./apiClient";

type InitListingInput = {
  categoryId: string;
  itemId?: string;
  brand?: string;
  model?: string;
  mode: "manual" | "ai";
  images: File[];
};

type InitListingResult = {
  postId: string;
  sessionId: string | null;
  aiInitialMessage?: string | null;
  aiDescription?: string | null;
};

async function initListingWithFiles(
  input: InitListingInput,
): Promise<InitListingResult> {
  const form = new FormData();
  form.append("categoryId", input.categoryId);
  form.append("mode", input.mode);
  if (input.itemId) form.append("itemId", input.itemId);
  if (input.brand) form.append("brand", input.brand);
  if (input.model) form.append("model", input.model);
  for (const image of input.images) {
    form.append("images", image, image.name);
  }
  return request<InitListingResult>("/v1/posts/init", {
    method: "POST",
    body: form,
    requiresAuth: true,
  });
}
```

Không tự đặt header `Content-Type: multipart/form-data` khi dùng browser FormData; trình duyệt phải tạo boundary. `apiClient.ts` hiện đã nhận biết FormData, cần giữ hành vi này. Native dùng multipart builder của HTTP client để tạo boundary và MIME từng part.

Bỏ nhánh fallback tự gửi JSON sau 415. Với BE hiện tại, JSON không tạo được draft vì thiếu các part ảnh. Không tự đổi mode sang manual nếu request AI bị lỗi.

### 6.2 Response thành công manual

```json
{
  "postId": "11111111-1111-4111-8111-111111111111",
  "sessionId": null,
  "aiInitialMessage": null,
  "aiDescription": null
}
```

Đây là response minh họa đúng cấu trúc; UUID do BE sinh. Field null có thể bị lược bỏ theo cấu hình serialize. Không tự yêu cầu `success: true` hoặc chỉ tìm `data.postId`: controller trả DTO trực tiếp. Nếu giữ lớp tương thích wrapper trong service cũ thì vẫn phải nhận được dạng trực tiếp này.

Init chưa trả gallery URL trong DTO này. Lấy thông tin draft qua `GET /api/v1/posts/{postId}/draft` theo luồng đang có khi cần ảnh đã lưu. Dùng URL BE trả; không tự dựng đuôi `.jpg` vì ảnh có thể được chuẩn hóa sang JPEG hoặc PNG. Thứ tự ảnh được giữ, ảnh đầu là ảnh đại diện.

## 7. Xử lý lỗi theo HTTP status và field

### 7.1 Lỗi ảnh trả nhiều field trong một response

```json
{
  "success": false,
  "message": "Ảnh tải lên chưa hợp lệ",
  "path": "/api/v1/posts/init",
  "errors": [
    {
      "field": "images[0]",
      "message": "Ảnh có kích thước 800 × 599 px. Cạnh ngắn phải đạt ít nhất 600 px."
    },
    {
      "field": "images[2]",
      "message": "Ảnh trùng với một ảnh trước đó. Hãy chọn góc chụp khác."
    }
  ],
  "timestamp": "2026-10-28T12:00:00"
}
```

Index bắt đầu từ **0** và tính theo danh sách file thực tế đã gửi. Ví dụ `images[2]` là ảnh thứ ba. Không parse câu chữ trong `message` để tìm index.

### 7.2 Gắn lỗi với ID ổn định

Mã dưới đây dùng dữ liệu `errors` sẵn có trong `ApiError` của FE. Gọi nó cho lỗi validation ảnh HTTP 400/413; lỗi 503 phải hiển thị trạng thái hệ thống bận, không đánh dấu ảnh invalid.

```ts
type FieldIssue = { field: string; message: string };

function mapListingImageErrors(
  issues: readonly FieldIssue[],
  sentPhotoIds: readonly string[],
) {
  const byPhotoId: Record<string, string[]> = {};
  const byField: Record<string, string[]> = {};
  for (const issue of issues) {
    const match = /^images\[(\d+)\]$/.exec(issue.field);
    const photoId = match ? sentPhotoIds[Number(match[1])] : undefined;
    if (photoId) {
      (byPhotoId[photoId] ??= []).push(issue.message);
    } else {
      (byField[issue.field] ??= []).push(issue.message);
    }
  }
  return { byPhotoId, byField };
}
```

Trước khi gửi, dùng `const sentPhotos = [...photos]`, gửi `sentPhotos.map(p => p.file)` và lưu `sentPhotos.map(p => p.id)` cho đúng request đó. Không filter/sắp xếp thêm trong service. Nếu UI cho đổi thứ tự khi request đang chạy, vẫn gắn lỗi bằng snapshot; cách đơn giản hơn là khóa danh sách trong lúc gửi.

Trong catch, giữ `ApiError.status`, `ApiError.errors`, `ApiError.path`; đừng bọc lại thành `new Error(message)` làm mất danh sách field. Lỗi `categoryId`/`itemId` gắn vào form tương ứng; lỗi chung `images` đặt dưới vùng chọn ảnh.

### 7.3 Ma trận xử lý

| Kết quả | UI cần làm |
|---|---|
| `400` + `images[n]` | Báo lỗi trên từng ảnh, giữ các ảnh khác; có thể có nhiều lỗi cùng lúc |
| `400` + `images` | Báo lỗi cả bộ ảnh, ví dụ chưa đủ 3 hoặc vượt 6 |
| `400` + field khác / message nghiệp vụ | Xử lý đúng form hoặc luồng AI; không biến mọi 400 thành ảnh sai định dạng |
| `413` | Báo quá dung lượng; index có thể không có vì servlet/proxy chặn trước khi đọc hết request |
| `401` | Xử lý hết phiên/token theo auth flow hiện có, giữ form nếu có thể |
| `403` | Báo thiếu quyền hoặc hạn chế tài khoản; không báo ảnh hỏng |
| `409` | Ví dụ vượt số draft chưa hoàn tất; hướng dẫn quản lý draft đang có |
| `415` | Kiểm tra multipart và Content-Type/boundary; không fallback JSON |
| `503` | Giữ ảnh, báo tạm bận và cho người dùng thử lại sau; không tự spam retry |
| `5xx`, timeout, network error | Giữ form và trạng thái lỗi chung; không kết luận mọi lỗi mạng đều do file quá lớn |

`503` còn có thể là lỗi tạm thời từ database, không chỉ slot decode ảnh. Không phụ thuộc vào một câu message cố định để nhận biết mọi trường hợp.

HTTP 413 từ servlet có thể như sau:

```json
{
  "success": false,
  "message": "Tệp hoặc tổng dung lượng upload vượt giới hạn cho phép",
  "path": "/api/v1/posts/init",
  "errors": [
    {
      "field": "images",
      "message": "Hãy giảm dung lượng ảnh hoặc số lượng file trong một lần gửi."
    }
  ],
  "timestamp": "2026-10-28T12:00:00"
}
```

Proxy có thể trả HTML hoặc đóng kết nối cho request quá lớn. HTTP client cần có fallback đọc lỗi khi body không phải JSON; nếu chỉ thấy network error thì thông báo kết nối thất bại, không gán chắc chắn là 413.

### 7.4 Không tạo draft trùng khi retry

Endpoint init hiện không có `requestId` để bảo đảm idempotency. Khóa đồng thời cả manual và AI bằng cùng trạng thái gửi; nên có guard đồng bộ như `useRef` bên cạnh state để chặn double-click rất nhanh. Khi đã có `postId`, dùng tiếp draft đó.

Không tự lặp POST init sau timeout/mất mạng/5xx: request có thể đã tạo draft trước khi client mất response. Khi kết quả không chắc chắn, cho người dùng kiểm tra danh sách draft hiện có trước khi tạo lại. Manual vẫn áp đủ validation kỹ thuật; AI có thể trả thêm lỗi nghiệp vụ loại/hãng/credit theo luồng riêng.

## 8. Cấu hình BE và đồng bộ với FE

| Env của BE | Property | Mốc tích hợp |
|---|---|---|
| `LISTING_MIN_IMAGES` | `app.listing.min-images` | `3` |
| `LISTING_MAX_IMAGES` | `app.listing.max-images` | `6` |
| `APP_LISTING_MAX_IMAGE_BYTES` | `app.listing.max-image-bytes` | `10485760` |
| `LISTING_IMAGE_MIN_SHORT_SIDE` | `app.listing.image-validation.min-short-side` | `600` |
| `LISTING_IMAGE_NORMALIZED_MAX_SIDE` | `app.listing.image-validation.normalized-max-side` | `2560` — mục tiêu chuẩn hóa, không phải trần kích thước input |
| `APP_LISTING_IMAGE_SIMILARITY_MAX_DIMENSION` | `app.listing.image-similarity.max-dimension` | `16000` |
| `APP_LISTING_IMAGE_SIMILARITY_MAX_PIXELS` | `app.listing.image-similarity.max-pixels` | `25000000` |
| `SPRING_SERVLET_MULTIPART_MAX_FILE_SIZE` | `spring.servlet.multipart.max-file-size` | Trần servlet cho từng part file; cần phù hợp với trần nghiệp vụ |
| `SPRING_SERVLET_MULTIPART_MAX_REQUEST_SIZE` | `spring.servlet.multipart.max-request-size` | Trần tổng multipart; bản triển khai/test đang dùng 64 MiB, phải xác nhận theo môi trường |

FE gom policy ở một module cấu hình dùng chung; không để số 1/3/6/10 MiB rải rác giữa badge, picker và submit. Cập nhật đồng thời web/mobile khi BE đổi ngưỡng. Request nhiều ảnh còn có overhead multipart; không chỉ so tổng byte với giới hạn mà quên phần overhead.

## 9. Bộ test FE và tiêu chí nghiệm thu

Giải nén [bộ ảnh test thật](test-data/SecondLife_Listing_Image_Test_Kit.zip). `front`, `back`, `label` bên dưới là `valid-front.jpg`, `valid-back.png`, `valid-label.webp`. Với ca lỗi một ảnh, giữ hai ảnh hợp lệ và thay ảnh còn lại, trừ khi ghi khác.

Để kiểm tra đúng mốc byte, gửi nguyên file mẫu hoặc tắt bước nén trên client. Nếu FE có encode/nén lại, kích thước dùng để quyết định gửi là kích thước file sau bước đó.

| TC | Cách thử | Kết quả mong đợi |
|---|---|---|
| 01 | Chọn 1 hoặc 2 ảnh | Có preview nhưng chưa gửi được cả manual và AI |
| 02 | Chọn front + back + label, `mode=manual` | HTTP 200, có postId; tạo draft, chưa publish |
| 03 | Chọn đủ 6 ảnh khác nhau | Gửi được, thứ tự đúng; ảnh đầu là đại diện |
| 04 | Chọn thêm ảnh thứ 7 | FE báo vượt số lượng; không âm thầm đổi lựa chọn người dùng |
| 05 | `empty.jpg` hoặc `fake-jpeg.jpg` | Báo lỗi; nếu request tới BE thì 400 đúng field |
| 06 | `truncated.jpg` / `truncated.png` | 400; ảnh đọc thumbnail được vẫn có thể bị BE từ chối |
| 07 | `too-small-599.png` | Bị chặn vì 800×599 |
| 08 | `boundary-600.png` | Đạt ngưỡng cạnh ngắn, gửi được |
| 09 | `boundary-exact-10MiB.jpg` + back + label | Đúng mốc 10 MiB được chấp nhận; không thêm front vì đây là cùng nội dung |
| 10 | `too-large-10MiB-plus-1.jpg` | Nếu file cuối vẫn vượt giới hạn thì FE chặn; gửi nguyên file bằng công cụ khác thì BE trả 413 |
| 11 | `too-many-pixels-8000x4000.png` / `too-wide-16001x600.png` | Chặn quá pixel/cạnh dù file nhỏ |
| 12 | `png-renamed-as-jpeg.jpg` | BE trả 400 vì đuôi không khớp bytes |
| 13 | APNG `animated.png` hoặc `animated.webp` | BE trả 400; không coi ảnh hiện được là ảnh tĩnh hợp lệ |
| 14 | Front + `duplicate-front.jpg` + label | BE trả 400 ảnh trùng; chỉ đổi tên không được chấp nhận |
| 15 | Ảnh trùng nhưng chỉ đổi EXIF/tên | BE vẫn có thể chặn qua nội dung sau chuẩn hóa |
| 16 | `phone-orientation-6.jpg` hoặc bản PNG/WebP cùng tên | Preview đúng hướng; ảnh server đúng hướng, không bị xoay hai lần |
| 17 | `phone-with-gps.jpg` | Ảnh lưu không còn EXIF/GPS nguồn |
| 18 | `valid-transparent.png` | Không xuất hiện nền đen do FE ép JPEG; ảnh server giữ alpha theo kết quả xử lý |
| 19 | Native chọn HEIC thật | Chuyển đổi thật trước khi gửi hoặc báo đổi định dạng; không đổi tên giả |
| 20 | 2 ảnh lỗi trong một request | Hiển thị đủ hai lỗi theo `images[n]` |
| 21 | Đổi thứ tự sau lỗi, rồi gửi lại | Field lỗi ở request mới gắn đúng ảnh trong snapshot mới |
| 22 | Double-click manual / AI hoặc bấm hai nút liên tiếp | Chỉ một request init đang chạy |
| 23 | Nhận 503 hoặc timeout | Form/ảnh còn nguyên; không tự spam POST init |
| 24 | Token hết hạn / không có quyền | Xử lý auth/quyền, không báo sai định dạng ảnh |
| 25 | Draft có sẵn chỉ còn URL preview | Không gửi URL thay File; không tạo draft mới chỉ để đi tiếp |
| 26 | Chuyển Blob PNG/WebP qua service | Filename, MIME và bytes vẫn khớp; không bị gắn `.jpg` tùy tiện |

Nghiệm thu trên Chrome/Edge, Safari/iOS và Android theo nền tảng FE thực tế. Cần kiểm tra ít nhất ảnh camera có EXIF, ảnh thư viện, ảnh trong suốt và thiết bị RAM thấp. Các test kỹ thuật dùng manual để tránh phụ thuộc AI và credit; chỉ dùng ảnh sản phẩm thật khi kiểm tra nhận diện AI.

## 10. Phạm vi cần giữ nguyên

- Không áp quy tắc 3–6 ảnh/600 px của listing cho mọi upload, ví dụ avatar, KYC hoặc ảnh bằng chứng.
- API media có kiểm tra file an toàn riêng; API multiple còn giới hạn tổng buffer sau chuẩn hóa theo trần multipart, có thể trả lỗi field `files`. Nó không thay contract `images` của init listing.
- Không tự áp chính sách bỏ metadata/nén lại của listing cho bằng chứng tranh chấp; luồng bằng chứng có yêu cầu lưu giữ và ownership riêng.
- Chất lượng nội dung như ảnh mờ/tối, không thấy sản phẩm hoặc khác hãng không đồng nghĩa với file hỏng; phần thay đổi này không bổ sung một ngưỡng AI chất lượng ảnh mới.
- Không thay luồng credit/publish/video khi chỉ tích hợp validation ảnh.

## 11. Tài liệu và nguồn code để đối chiếu

- [Test Swagger chi tiết, JSON lỗi và 34 ca test](SWAGGER_LISTING_IMAGE_VALIDATION_TEST.md).
- [Bộ ảnh test](test-data/SecondLife_Listing_Image_Test_Kit.zip).
- [Báo cáo kiểm thử BE](LISTING_IMAGE_VALIDATION_VERIFICATION.md).
- [PostController](../src/main/java/com/secondlife/secondlife/controller/PostController.java): endpoint, quyền, kiểu multipart và DTO trực tiếp.
- [PostInitRequest](../src/main/java/com/secondlife/secondlife/dto/request/PostInitRequest.java), [PostInitResponse](../src/main/java/com/secondlife/secondlife/dto/response/PostInitResponse.java).
- [ImageUploadValidator](../src/main/java/com/secondlife/secondlife/common/validation/ImageUploadValidator.java): ngưỡng, EXIF, định dạng, chuẩn hóa và trùng ảnh.
- [PostServiceImpl](../src/main/java/com/secondlife/secondlife/service/impl/PostServiceImpl.java): thứ tự validation và hành vi manual/AI.
- [GlobalExceptionHandler](../src/main/java/com/secondlife/secondlife/exception/GlobalExceptionHandler.java): HTTP status và field errors.
- [application.properties](../src/main/resources/application.properties): mapping env/property.
