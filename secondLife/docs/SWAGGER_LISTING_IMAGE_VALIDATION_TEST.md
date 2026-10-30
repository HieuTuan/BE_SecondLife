# Test thực tế trên Swagger: validation ảnh đăng bán

## 1. Phạm vi và bộ file

- API chính: `POST /api/v1/posts/init`, `multipart/form-data`. Endpoint và response thành công được giữ nguyên.
- Ảnh đăng bán: 3–6 ảnh; mỗi ảnh <=10 MiB (10.485.760 byte); cạnh ngắn >=600 px; mỗi cạnh <=16.000 px; tổng pixel <=25.000.000. Các giá trị thực tế có thể được cấu hình khác trong môi trường chạy.
- JPEG/PNG/WebP tĩnh; kiểm tra nội dung thực và giải mã được. Không nhận ảnh động, SVG, GIF hay HEIC trực tiếp.
- Kiểm tra toàn bộ bộ ảnh trước upload, tạo draft hoặc gọi AI. Ảnh trùng được kiểm tra sau chuẩn hóa, nên chỉ đổi tên/metadata không làm ảnh thành góc chụp mới.
- Hệ thống xoay theo EXIF, bỏ metadata nguồn, resize giữ tỷ lệ (ưu tiên cạnh dài 2.560 px và vẫn giữ cạnh ngắn tối thiểu của ảnh đăng bán). Ảnh không trong suốt được chuẩn hóa JPEG; ảnh có alpha được giữ bằng PNG. Không yêu cầu ảnh đầu ra vẫn là WebP/PNG gốc.
- Bộ mẫu là ảnh tổng hợp phục vụ kiểm tra kỹ thuật, không phải ảnh sản phẩm thật để đánh giá AI. Dùng `mode=manual` trong các ca bên dưới.

File mẫu nằm trong gói [SecondLife_Listing_Image_Test_Kit.zip](test-data/SecondLife_Listing_Image_Test_Kit.zip), thư mục `listing-images`. `manifest.json` có số byte, kích thước, định dạng, số frame, EXIF orientation và SHA-256 từng file.

Có thể tự tạo lại bằng Python có Pillow:

```powershell
python -m pip install Pillow
python .\scripts\generate_image_validation_fixtures.py
```

Lệnh tạo file trong `docs/test-data/listing-images/`, không gọi BE/Cloudinary/database. Pillow chỉ là công cụ tạo test data, không phải dependency của BE.

## 2. Chuẩn bị Swagger

1. Build/chạy lại BE sau khi nhận code. Mở `http://localhost:8080/swagger-ui/index.html`.
2. Đăng nhập tài khoản seller có quyền `LISTING_CREATE_SELF`, lấy access token còn hạn và Authorize trên Swagger. Khi Swagger dùng HTTP Bearer, chỉ dán token nếu ô nhập đã tự thêm `Bearer`.
3. Gọi `GET /api/v1/categories`, lấy UUID danh mục có thật. `itemId` có thể để trống; nếu gửi thì phải thuộc danh mục đã chọn.
4. Tài khoản phải chưa đạt số draft chưa hoàn tất tối đa (mặc định cấu hình hiện tại là 30). Một ca thành công sẽ tạo draft; không giả định đây là thao tác chỉ đọc.
5. Mở `POST /api/v1/posts/init` → **Try it out**.
6. Điền các trường sau. Với `images`, dùng **Add item** rồi chọn file cho từng phần tử; không nhập đường dẫn hoặc chuỗi `string` thay cho file.

| Trường | Giá trị test |
|---|---|
| categoryId | UUID danh mục vừa lấy |
| itemId | Để trống |
| brand | Để trống |
| model | Để trống |
| mode | `manual` |
| images | Các file theo từng test case |

Không gửi JSON base64 cho endpoint này; Swagger phải gửi multipart với nhiều part cùng tên `images`.

## 3. Ca thành công đầu tiên

Chọn đúng ba file theo thứ tự:

1. `valid-front.jpg`
2. `valid-back.png`
3. `valid-label.webp`

Nhấn **Execute**. Mong đợi HTTP **200**, response có `postId`:

```json
{
  "postId": "UUID_DRAFT_MOI",
  "sessionId": null,
  "aiInitialMessage": null,
  "aiDescription": null
}
```

Các field null có thể bị lược bỏ theo cấu hình serialize. Đây là DTO trực tiếp, không tự tìm `data.postId`.

- Draft có đủ 3 URL theo thứ tự chọn; ảnh đầu là ảnh đại diện.
- Mode manual không gọi AI hoặc tiêu thụ AI credit trong bước init.
- Ảnh nằm dưới `listings/images/<UUID>` với ID do BE sinh. Tên file gốc không quyết định public ID.
- Đây mới là tạo draft, chưa phải xuất bản tin. Nếu muốn kiểm tra tiếp luồng đăng tin thì hoàn tất các bước mô tả/publish hiện có.

## 4. Ma trận test trên Swagger

Trừ trường hợp ghi khác, giữ `categoryId` hợp lệ, `mode=manual`, và chọn ba file. Cột `images[n]` dùng chỉ số bắt đầu từ 0.

| TC | File theo thứ tự | Mong đợi |
|---|---|---|
| 01 | front JPG + back PNG + label WebP như mục 3 | 200; đủ 3 ảnh |
| 02 | 6 ảnh khác nhau: front, back, label, bottom, left, right | 200; giữ thứ tự và ảnh đại diện |
| 03 | Chỉ front + back | 400; lỗi field `images`, cần 3–6 ảnh |
| 04 | Chọn 7 file | 400; lỗi `images` nếu request chưa vượt giới hạn tổng |
| 05 | Không gửi images | 400; lỗi `images` từ binding/validation |
| 06 | front + `empty.jpg` + label | 400; `images[1]`, file không được trống |
| 07 | front + `fake-jpeg.jpg` + label | 400; `images[1]`, không đọc được nội dung ảnh |
| 08 | front + `truncated.jpg` + label | 400; `images[1]`, ảnh hỏng/không giải mã được |
| 09 | front + `truncated.png` + label | 400; `images[1]` |
| 10 | front + `png-renamed-as-jpeg.jpg` + label | 400; `images[1]`, đuôi không khớp nội dung |
| 11 | front + `unsupported.svg` + label | 400; `images[1]`, chỉ hỗ trợ định dạng cho phép |
| 12 | front + `animated.gif` + label | 400; định dạng GIF không được hỗ trợ |
| 13 | front + `animated.png` + label | 400; không hỗ trợ ảnh động dù đuôi PNG |
| 14 | front + `animated.webp` + back | 400; không hỗ trợ WebP động |
| 15 | front + `too-small-599.png` + label | 400; `images[1]`, 800×599, cạnh ngắn tối thiểu 600 |
| 16 | front + `boundary-600.png` + label | 200; mốc đúng 600 được chấp nhận |
| 17 | front + `too-many-pixels-8000x4000.png` + label | 400; 32 triệu pixel vượt 25 triệu dù file rất nhỏ |
| 18 | front + `too-wide-16001x600.png` + label | 400; một cạnh vượt 16.000 px |
| 19 | `boundary-exact-10MiB.jpg` + back + label | 200; mốc byte tối đa được chấp nhận; không thêm front vì đó là cùng ảnh sau chuẩn hóa |
| 20 | front + `too-large-10MiB-plus-1.jpg` + label | 413; có thể lỗi `images` từ servlet trước khi biết index |
| 21 | `phone-orientation-6.jpg` + back + label | 200; ảnh gốc 1200×800 được hiển thị đúng chiều 800×1200; EXIF nguồn bị bỏ |
| 22 | `valid-large-4000x3000.jpg` + back + label | 200; ảnh 12 MP được thu về khoảng 2560×1920 |
| 23 | front + `duplicate-front.jpg` + label | 400; `images[1]`, đổi tên không tránh được phát hiện ảnh trùng |
| 24 | front + `valid-blank-white.jpg` + label | 200 ở validation kỹ thuật; không coi ảnh trắng là file hỏng. Chất lượng/nội dung sản phẩm là bước khác |
| 25 | `too-small-599.png` + `fake-jpeg.jpg` + label | 400; `errors` có cả `images[0]` và `images[1]` |
| 26 | Chọn ảnh HEIC thật từ iPhone cùng hai ảnh hợp lệ | 400; yêu cầu chuyển sang JPEG trước; đổi tên đuôi không chuyển được định dạng |
| 27 | 7 lần `boundary-exact-10MiB.jpg` | 413 khi tổng request vượt 64 MiB, trước kiểm tra 3–6 ảnh |
| 28 | Bỏ token hoặc dùng token hết hạn | Bị chặn xác thực; chưa chạy validation ảnh |
| 29 | Token tài khoản không có LISTING_CREATE_SELF | 403; không upload/tạo draft/tiêu thụ credit |
| 30 | front + `duplicate-front-exif.jpg` + label | 400; cùng pixel nhưng metadata khác vẫn bị coi là trùng |
| 31 | front + `truncated-no-eoi.jpg` + label, rồi thử `truncated-entropy-tail.jpg` | 400; JPEG gần hoàn chỉnh nhưng mất marker kết thúc/phần đuôi vẫn bị chặn |
| 32 | `phone-with-gps.jpg` + back + label | 200; tải ảnh kết quả về, không còn EXIF/GPS của nguồn |
| 33 | `phone-orientation-6.png` + back + label, rồi thay bằng bản `.webp` | 200; cả hai định dạng đều xoay đúng thành 800×1200 |
| 34 | `valid-transparent.png` + back + label | 200; ảnh đầu ra vẫn là PNG và giữ vùng trong suốt |

`front/back/label` trong bảng là `valid-front.jpg`, `valid-back.png`, `valid-label.webp`.

Mỗi lần chạy ca lỗi, xác minh không có draft mới, không có AI credit mới bị trừ và không có asset mới của request trong Cloudinary. Kiểm thử tự động cũng kiểm tra các side effect này bằng các dependency giả lập.

## 5. Response lỗi mẫu

TC25 trả cấu trúc tương tự:

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
      "field": "images[1]",
      "message": "Không đọc được nội dung ảnh. Chỉ nhận ảnh JPEG, PNG hoặc WebP hợp lệ."
    }
  ],
  "timestamp": "2026-10-28T12:00:00"
}
```

Vượt giới hạn do servlet không có index cụ thể:

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

Nếu request đã qua servlet nhưng quá giới hạn nghiệp vụ riêng, 413 có thể chỉ đúng `images[index]`. Nếu reverse proxy từ chối trước BE thì body có thể thuộc proxy; phải cấu hình proxy trả lỗi phù hợp. Request rất lớn còn có thể bị proxy/Tomcat đóng kết nối khi vượt ngưỡng drain/swallow: FE cần báo vượt dung lượng khi gặp tình huống này, không tự gửi lại liên tục.

## 6. Test Content-Type giả / mobile không có MIME

Swagger thường tự đặt MIME theo tên file, nên dùng `curl.exe` trong PowerShell cho các ca cần đặt MIME cụ thể. Thay UUID, token và đường dẫn thật; không paste secret hoặc response chứa token vào tài liệu/log.

```powershell
$token = Read-Host 'Access token seller'
$categoryId = 'THAY_UUID_DANH_MUC'
$images = 'C:\DUONG_DAN\listing-images'

curl.exe -X POST 'http://localhost:8080/api/v1/posts/init' `
  -H "Authorization: Bearer $token" `
  -F "categoryId=$categoryId" `
  -F 'mode=manual' `
  -F "images=@$images\valid-front.jpg;type=image/png" `
  -F "images=@$images\valid-back.png;type=image/png" `
  -F "images=@$images\valid-label.webp;type=image/webp"
```

Mong đợi **400**, field `images[0]`, MIME không khớp ảnh JPEG thật. Đổi MIME part đầu thành `application/octet-stream` thì ảnh JPEG thật được chấp nhận. BE cũng cho phép thiếu MIME khi đuôi và nội dung giải mã hợp lệ, để hỗ trợ một số native file picker. MIME cụ thể sai vẫn bị từ chối.

## 7. Kiểm tra bổ sung API media

`POST /api/v1/media/upload` dùng multipart field `file`, token có `MEDIA_UPLOAD_SELF`.

- `too-small-599.png`: **201**; API media không áp tối thiểu 600 px của listing.
- `fake-jpeg.jpg`, `animated.png`: **400**, lỗi field `file`.
- Không gửi part `file`: **400**, lỗi field `file`; không còn trả 500 cho trường hợp thiếu file.
- `phone-orientation-6.jpg`: **201**; mở URL trả về để kiểm tra chiều xoay. Có thể tải ảnh kết quả về và kiểm tra không còn EXIF/GPS nguồn.
- API multiple dùng field `files`; lỗi sẽ là `files[index]`. Ảnh hỏng ở cuối danh sách phải bị chặn trước upload ảnh đầu.
- Tổng dung lượng batch **sau chuẩn hóa** cũng phải nằm trong giới hạn multipart request đang cấu hình (hiện 64 MiB); vượt ngưỡng trả 413, field `files`, trước upload. Đây là ngân sách buffer của batch, không chỉ kiểm tra Content-Length input.
- Không dùng API media để thay thế upload ảnh của `posts/init`; endpoint đăng bài vẫn yêu cầu chính các file ảnh và áp quy tắc listing.

## 8. Kiểm thử tự động và giới hạn xác minh

- `ImageUploadValidatorTest`: ảnh thật các định dạng, byte/dimension boundaries, animation, định dạng giả, trùng ảnh, EXIF, metadata và resize.
- `ListingImageValidationTest`: chặn trước side effect; success manual; cleanup khi upload/lưu draft lỗi.
- `ImageUploadErrorContractTest`: JSON và HTTP status.
- `ImageMultipartHttpTest`: Tomcat + servlet multipart + HTTP loopback thực, kiểm tra giới hạn từng file và tổng request với ngưỡng nhỏ cho test. Test server không có DB, Cloudinary hoặc tài khoản thật.
- `MediaServiceTest`, `MediaControllerTest`, `CloudinaryImageUploadTest`: kiểm tra hồi quy luồng media/storage.
- `CloudinaryMediaImageCleanupTest`: timeout sau khi provider nhận bytes, reference không hợp lệ và cleanup đúng ID do server sinh.
- `ListingImageUploadIntegrationTest`: JWT và PostgreSQL Testcontainers thực; lưu 3 ảnh, từ chối ảnh hỏng/trùng và kiểm tra không có side effect. Cần Docker hoặc một database test riêng được chỉ định bằng `LISTING_IMAGE_TEST_JDBC_URL`; không trỏ biến này vào database dữ liệu thật.

```powershell
mvn -o '-Dtest=ImageUploadValidatorTest,ListingImageValidationTest,ImageUploadErrorContractTest,ImageMultipartHttpTest,MediaServiceTest,MediaControllerTest,CloudinaryImageUploadTest,CloudinaryMediaImageCleanupTest,ListingImageUploadIntegrationTest' test
```

Trong sandbox có thể phải nạp Mockito Java agent khi chạy test. Kết quả các lệnh kiểm thử thực tế của lần triển khai được ghi riêng trong `LISTING_IMAGE_VALIDATION_VERIFICATION.md`.

Không coi các test mock Cloudinary là đã upload thành công lên tài khoản Cloudinary thật. Các ca thành công trên Swagger ở môi trường của bạn mới xác nhận credentials, mạng và quota thực tế.

Cleanup tài nguyên Cloudinary là best effort: sự cố mạng/provider có thể khiến xóa thất bại; BE ghi log ID tài nguyên cần dọn và giữ lỗi gốc. Không xóa ảnh của draft đã lưu chỉ vì AI ở bước sau bị gián đoạn.
