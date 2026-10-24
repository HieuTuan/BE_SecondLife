# Video bằng chứng: upload chunk trực tiếp Cloudinary

BE theo Service + ServiceImpl, DTO record, MapStruct, JpaRepository; dùng `com.cloudinary:cloudinary-http5:2.0.0` đã có. Loại dependency `cloudinary-http44:1.38.0` trùng SDK. Không gửi video bytes qua BE. Thay đổi chỉ bổ sung bằng chứng; điều kiện mở khiếu nại 48 giờ, quyết định và hoàn tiền giữ nguyên.

## 1. Cấu hình

Giữ CloudinaryConfig và biến `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET` hiện có. Thêm `CLOUDINARY_VIDEO_UPLOAD_PRESET` là tên preset SIGNED tạo trong Cloudinary Console. Không dùng preset unsigned. API secret chỉ nằm ở BE.

Preset cần cho phép public_id do BE cấp, không tự thêm prefix/thay public ID và không chuyển video sang delivery public. Request ký cố định `type=authenticated`, `overwrite=false`, `allowed_formats=mp4,mov,webm`. Không áp dụng incoming transformation cắt thời lượng/đổi nội dung bằng chứng. Chunked upload là cách FE gửi request, không phải một loại preset riêng. Upload Cloudinary hỗ trợ tối đa còn tùy gói tài khoản.

Mặc định BE: 250 MiB (262144000 bytes), 600 giây, chunk 6 MiB, tối đa 10 session/user/giờ. Session/signature một giờ; playback URL năm phút. TTL session không được vượt 3600 giây. Nếu preset chưa cấu hình, ứng dụng vẫn khởi động, API signature trả 503 có thông báo rõ.

Cloudinary không có giới hạn file size riêng cho từng preset: FE kiểm tra file.size để tránh upload lãng phí; BE kiểm tra bytes và duration từ Cloudinary trước khi đăng ký bằng chứng. Giới hạn này không ngăn một client cố tình gửi file quá lớn lên provider trước khi gọi complete; cần quản lý giới hạn tài khoản/chi phí và dọn asset mồ côi theo chính sách vận hành. Không có job tự xóa video tranh chấp trong thay đổi này để tránh mất bằng chứng.

Ví dụ YAML: [application-video-upload.yml](examples/application-video-upload.yml). Dự án dùng application.properties và đã bổ sung các biến `app.video-upload.*`; không cần tạo thêm application.yml cho cùng cấu hình.

CORS BE tái sử dụng SecurityConfig + CORS_ALLOWED_ORIGINS hiện có, thêm origin FE thật vào danh sách cấu hình. Không dùng `*` với credential. CORS cho request chunk là giữa browser và Cloudinary, độc lập với CORS của BE.

## 2. Xin chữ ký (BUYER hoặc SELLER)

`POST /api/video/upload-signature`, Bearer JWT, quyền `MEDIA_UPLOAD_SELF`:

```json
{"requestId":"11111111-1111-4111-8111-111111111111"}
```

Response `ApiResponse`, dữ liệu tại `data`:

```json
{
  "uploadId":"22222222-2222-4222-8222-222222222222",
  "publicId":"report-evidence/videos/22222222-2222-4222-8222-222222222222",
  "signature":"SIGNATURE_DO_BE_TRA_VE",
  "timestamp":1792850000,
  "apiKey":"PUBLIC_CLOUDINARY_API_KEY",
  "cloudName":"YOUR_CLOUD_NAME",
  "uploadUrl":"https://api.cloudinary.com/v1_1/YOUR_CLOUD_NAME/video/upload",
  "parameters":{
    "timestamp":"1792850000",
    "upload_preset":"YOUR_SIGNED_PRESET",
    "public_id":"report-evidence/videos/22222222-2222-4222-8222-222222222222",
    "type":"authenticated",
    "overwrite":"false",
    "allowed_formats":"mp4,mov,webm"
  },
  "expiresAt":"2026-10-24T16:00:00Z",
  "maxBytes":262144000,
  "chunkSizeBytes":6291456
}
```

Timestamp/UUID/expiry chỉ minh họa; dùng nguyên dữ liệu BE trả. FE không được thêm/sửa params được ký hoặc tự đặt public_id. Retry cùng requestId trả lại session/chữ ký cũ và không tốn quota mới. Session hết hạn: xin session mới với requestId mới, không tiếp tục upload vào session cũ. Khi nhận chữ ký phải giữ lại requestId/session và tiến độ trên FE.

## 3. Upload tuần tự từng chunk

Mỗi chunk POST multipart trực tiếp tới `uploadUrl`; `file` là Blob chunk, các field khác là toàn bộ `parameters`, `api_key`, `signature`. Dùng cùng `X-Unique-Upload-Id` cho mọi chunk và header `Content-Range: bytes start-end/total`; end là inclusive. Không tự đặt Content-Type multipart vì browser cần sinh boundary. Không gửi Bearer JWT SecondLife sang Cloudinary.

Chunk không phải cuối phải lớn hơn 5 MB; dùng nguyên chunkSizeBytes 6291456 do BE trả. Chờ từng response, không bắn tất cả chunk đồng thời. Các response trung gian `done:false` chưa phải asset đã ghép; chỉ gọi complete sau response cuối có metadata đầy đủ. Nếu `done:false` thì chưa hoàn tất; response không có `done` chỉ được tiếp tục khi public_id/secure_url/bytes/format đầy đủ và khớp video đang chọn. BE vẫn xác minh lại với provider.

Ví dụ JS dùng chung cho React/Vue nằm ở [direct-video-upload.js](examples/direct-video-upload.js). Có callback progress theo chunk đã được provider chấp nhận, retry lỗi tạm thời có giới hạn; không tự tạo uploadId mới khi chunk timeout. Đây là ví dụ FE, cần gắn cơ chế hủy request và giữ state upload theo UI của ứng dụng. Không có khả năng resume sau reload được bảo đảm chỉ bằng đoạn ví dụ này.

Tài khoản Cloudinary có thể giới hạn tổng video thấp hơn giới hạn BE. Ví dụ lỗi `Maximum is 104857600` nghĩa là tối đa100MiB trên tài khoản đó, dù chia chunk. Chọn video nhỏ hơn hoặc điều chỉnh giới hạn tài khoản; có thể đặt `VIDEO_UPLOAD_MAX_BYTES=104857600` trong .env để FE/BE báo giới hạn đồng nhất rồi restart BE. Mỗi file mới phải dùng session mới vì overwrite=false; không dùng một session để thử nhiều video khác nhau.

## 4. Xác minh và lưu metadata

Sau response cuối của Cloudinary, dùng JWT của cùng người xin chữ ký:

`POST /api/video/complete`

```json
{
  "uploadId":"22222222-2222-4222-8222-222222222222",
  "publicId":"report-evidence/videos/22222222-2222-4222-8222-222222222222",
  "videoUrl":"SECURE_URL_TU_CLOUDINARY",
  "bytes":1048576,
  "format":"mp4",
  "duration":12.5
}
```

Chỉ uploadId/publicId bắt buộc. Các field metadata còn lại có thể bỏ; nếu gửi phải khớp provider (duration chấp nhận sai số 0.1 giây). BE không fetch videoUrl FE gửi. BE dùng Admin API đã xác thực để đọc asset theo publicId đã cấp, kiểm tra resource_type video, type authenticated, kích thước khung hình có video, format, bytes, duration và asset ID/version. Thông tin lưu DB lấy từ provider.

Response data:

```json
{
  "uploadId":"22222222-2222-4222-8222-222222222222",
  "evidenceId":"22222222-2222-4222-8222-222222222222",
  "evidenceUrl":"/api/v1/reports/evidence/22222222-2222-4222-8222-222222222222",
  "publicId":"report-evidence/videos/22222222-2222-4222-8222-222222222222",
  "videoUrl":"PRIVATE_PROVIDER_URL",
  "bytes":1048576,
  "format":"mp4",
  "duration":12.5,
  "completedAt":"2026-10-24T15:10:00Z"
}
```

videoUrl là metadata private, không phải URL player công khai. Sau complete, dùng evidenceUrl như ảnh để tạo report hoặc thêm phản hồi. Đối với session đã hoàn tất, retry complete không tạo thêm video/evidence hay gọi provider lại; metadata khác bị từ chối. Upload chưa ghép/asset chưa sẵn sàng trả 502 có thể retry; expired409, sai owner403, publicId/metadata không khớp409.

Để bảo vệ quota Admin API, xác minh có giới hạn lưu DB: một lần/video/30 giây, 20 lần/user/giờ và 100 lần toàn BE/giờ (đều cấu hình được qua VIDEO_VERIFY_COOLDOWN_SECONDS/VIDEO_VERIFY_OWNER_PER_HOUR/VIDEO_VERIFY_GLOBAL_PER_HOUR). Lần xác minh thất bại vẫn tiêu thụ lượt; gặp429 cần chờ, không spam complete. Retry của video đã complete thành công không tiêu thụ lượt. Migration V58 tạo bảng cửa sổ giới hạn dùng chung cho nhiều instance BE.

## 5. Buyer gắn video vào khiếu nại

Giữ API `POST /api/v1/reports` và thêm evidenceUrl vào evidenceUrls. Ảnh và video dùng chung mảng; vẫn chỉ upload nào thuộc người tạo mới được gắn. Mỗi upload chỉ được gắn vào một report/một lần submission.

```json
{
  "requestId":"33333333-3333-4333-8333-333333333333",
  "orderId":"ORDER_UUID_THAT",
  "reasonCode":"MISLEADING_DESCRIPTION",
  "description":"Sản phẩm có hư hỏng khi mở kiện, xem video bằng chứng.",
  "evidenceUrls":["/api/v1/reports/evidence/22222222-2222-4222-8222-222222222222"]
}
```

Lưu ý upload video không gia hạn cửa sổ khiếu nại 48 giờ. Nếu gần hết hạn, buyer có thể mở report hợp lệ trước rồi bổ sung video qua responses khi report còn OPEN/IN_REVIEW.

## 6. Seller phản hồi / buyer bổ sung bằng chứng

`POST /api/v1/reports/{reportId}/responses`, JWT của seller/reporting buyer, trả 201:

```json
{
  "requestId":"44444444-4444-4444-8444-444444444444",
  "description":"Video đóng gói trước khi bàn giao cho đơn vị vận chuyển.",
  "evidenceUrls":["/api/v1/reports/evidence/UUID_VIDEO_CUA_SELLER"]
}
```

Chỉ order report OPEN/IN_REVIEW, tối đa 10 evidence/request, description bắt buộc tối đa2000 ký tự. Role author được BE suy ra BUYER/SELLER từ quan hệ report, không lấy từ FE. Retry cùng author/report/requestId và body trả lại bản ghi cũ kể cả đã chốt; đổi nội dung409. Không có sửa/xóa response, DB cũng chặn sửa/xóa để giữ lịch sử bằng chứng.

`GET /api/v1/reports/{reportId}/responses?page=0&size=20` trả `data.items` (PageResponse), tối đa100/page, thứ tự createdAt/id tăng dần. Authorized STAFF+STAFF_FRAUD_REVIEW hoặc ADMIN+ADMIN_RBAC_MANAGE thấy cả hai bên để so sánh. Buyer/seller chỉ thấy responses của chính mình. DTO: id,reportId,authorId,authorRole,requestId,description,evidenceUrls,createdAt.

Luồng này không mở rộng quyền seller đọc GET /reports/{id} hay ảnh riêng của buyer. Seller phải có reportId từ luồng thông báo/điều phối hiện có; thay đổi này không thêm một màn danh sách tranh chấp riêng cho seller. Staff dùng GET report lấy bằng chứng ban đầu của buyer và GET responses lấy phần bổ sung/seller.

## 7. Staff xem ảnh/video

Với mỗi evidenceUrl, gọi `GET {evidenceUrl}/metadata` kèm Bearer. Response data gồm evidenceId,contentType,size,mediaType IMAGE/VIDEO,contentUrl.

- IMAGE: contentUrl là endpoint ảnh bytes hiện có; fetch bearer và tạo blob URL.
- VIDEO: contentUrl là `/api/video/{uploadId}/playback`; gọi bằng bearer rồi lấy `data.url` và gán cho player. URL provider hết hạn theo `data.expiresAt`; xin lại khi cần. Không log/cache/public hóa URL đã ký.
- GET video qua endpoint ảnh bytes cũ trả400 để tránh tải toàn bộ video vào RAM/qua BE.
- Chủ upload xem được video của mình; staff chỉ xem khi bằng chứng đã được gắn vào report. Người không liên quan403.

URL phát dùng Cloudinary privateDownload với resource_type video/type authenticated và expires_at. Request phát do browser gọi Cloudinary, không proxy qua BE. Đây là endpoint download có xác thực theo thời hạn, không phải CDN URL công khai; cần test player/tua và tính băng thông provider trên tài khoản thực tế trước khi rollout. Cloudinary ghi nhận loại URL này không cache CDN và có chi phí băng thông khác URL CDN thông thường.

## 8. Swagger và kiểm thử

1. Login buyer, Authorize JWT; POST upload-signature với UUID mới.
2. Swagger BE không tự upload chunk tới host Cloudinary. Dùng ví dụ FE/browser để gửi chunks; không gọi upload-video cũ nếu mục tiêu là direct upload.
3. POST complete trên Swagger với dữ liệu thực trả từ provider; lưu evidenceUrl.
4. Tạo report, đổi token seller, upload/complete video seller, POST responses.
5. Đổi token staff: GET report + GET responses; đọc metadata và playback.
6. Test wrong owner403, complete sai metadata409, expired409, vượt quota429, incomplete502; staff xem upload chưa gắn report403.
7. Test retry signature/complete/response không tạo thêm bản ghi, response sau chốt409, retry nguyên bản sau chốt vẫn thành công.

Unit test adapter dùng SDK signer thật và mock provider API. Integration tests dùng PostgreSQL Testcontainers, không trỏ database ứng dụng. Chưa chạy upload video/ghép chunk thật lên tài khoản Cloudinary trong kiểm thử tự động này.

## Lưu ý connection pool

Reservation quota dùng REQUIRES_NEW để lần gọi provider thất bại không rollback mất hạn mức. Cấu hình datasource pool và giới hạn concurrency phải dành connection dự phòng cho transaction này; không cho số request complete đồng thời chiếm hết pool. Kiểm thử tự động đã bao phủ hai request đồng thời, chưa phải load test production.

## Nguồn Cloudinary chính thức

- https://cloudinary.com/documentation/upload_images — chunked multipart, Content-Range, X-Unique-Upload-Id.
- https://cloudinary.com/documentation/authentication_signatures — ký các upload params, loại trừ resource_type/file/api_key/cloud_name; thời hạn chữ ký.
- https://cloudinary.com/documentation/upload_presets — signed presets và giới hạn preset.
- https://cloudinary.com/documentation/admin_api — xác minh metadata provider.
- https://cloudinary.com/documentation/control_access_to_media — authenticated assets/privateDownload và hạn truy cập.
