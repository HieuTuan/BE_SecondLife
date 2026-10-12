# Hướng dẫn FE: đăng ký SELLER và xác thực eKYC

Đối chiếu với code BE ngày 12/10/2026. Tài liệu hướng dẫn màn hình, thứ tự API, JSON và thao tác của người đăng ký / ADMIN.

## 1. Quy ước chung

- Base URL khi test local: **http://localhost:8080**. FE cấu hình base URL theo môi trường.
- API nghiệp vụ dùng **/api/v1/...**. Nhóm Authentication giữ nguyên **/api/auth/...**.
- Luồng người đăng ký dùng access token của tài khoản BUYER. Các thao tác ADMIN dùng token ADMIN.
- Gửi header **Authorization: Bearer <accessToken>**. JSON dùng Content-Type application/json; FormData để trình duyệt tự đặt Content-Type và multipart boundary.
- Đọc dữ liệu trong **data** của response. HTTP 200/201 hoặc success = true chỉ thể hiện thao tác được xử lý; không có nghĩa eKYC đạt hoặc đã có role SELLER.
- Kết luận dựa vào **data.status**, kết hợp ekycStatus, riskStatus, reasonCodeDescription và rejectionReason.
- ID trong URL thao tác hồ sơ là **data.id của hồ sơ xác thực**, không phải userId.
- JSON mẫu đúng cấu trúc; thay email, số giấy tờ, mã GHN và URL ảnh bằng dữ liệu thật.

Permission người đăng ký: SELLER_VERIFICATION_READ_SELF để đọc; SELLER_VERIFICATION_SUBMIT để lưu onboarding, xác nhận email và nộp hồ sơ; MEDIA_UPLOAD_SELF để upload ảnh. ADMIN cần role ADMIN cùng permission được ghi tại phần 7.

## 2. Màn hình và thứ tự gọi API

~~~mermaid
flowchart TD
    A[Mở trang đăng ký seller] --> B[Đọc profile, onboarding và hồ sơ hiện tại]
    B --> C[Nhập shop và chọn địa chỉ GHN]
    C --> D[Lưu thông tin shop]
    D --> E[Gửi OTP và xác nhận email]
    E --> F[Chụp và upload ba ảnh]
    F --> G[Nộp hồ sơ xác thực]
    G --> H{status từ BE}
    H -->|APPROVED| I[Cập nhật quyền và mở chức năng SELLER]
    H -->|RESUBMIT_REQUIRED| J[Thay ảnh và nộp lại cùng hồ sơ]
    J --> H
    H -->|NEEDS_REVIEW| K[Chờ ADMIN kiểm duyệt]
    H -->|SUBMITTED hoặc EKYC_PENDING| L[Đọc lại trạng thái, không tạo hồ sơ mới]
    H -->|REJECTED| M[Hiển thị lý do và cho tạo hồ sơ mới]
    M --> F
~~~

Đây là luồng lần đầu. Khi người dùng quay lại hoặc refresh trang, FE phải đọc hồ sơ đã lưu để đi đúng nhánh; không mặc định mở lại form tạo mới.

### Đăng nhập

~~~http
POST /api/auth/login
~~~

~~~json
{
  "email": "buyer@example.com",
  "password": "MAT_KHAU_CUA_TAI_KHOAN"
}
~~~

Lấy data.accessToken, sau đó có thể gọi đồng thời:

~~~http
GET /api/v1/users/me
GET /api/v1/seller-onboarding/me
GET /api/v1/seller-verifications/me
~~~

- Profile cung cấp roles và permissions để cập nhật state FE.
- Onboarding cung cấp shopName, pickupAddress, email, phone, provinceId, wardId, emailVerified, canStartEkyc, nextStep, emailVerifiedAt.
- Hồ sơ hiện tại có **data = null** khi chưa nộp hồ sơ; không coi đây là lỗi.
- nextStep = SHOP_INFORMATION: mở form shop / địa chỉ.
- nextStep = EMAIL_VERIFICATION: mở bước OTP.
- nextStep = EKYC và canStartEkyc = true: onboarding đã hoàn tất, nhưng vẫn phải kiểm tra trạng thái hồ sơ trước khi cho tạo mới.
- Nếu đã có role SELLER, không mở form tạo hồ sơ mới.

## 3. Thông tin shop, địa chỉ GHN và email

### 3.1. Chọn tỉnh / phường

~~~http
GET /api/v1/shipping/provinces
GET /api/v1/shipping/wards?provinceId=<PROVINCE_ID>
~~~

UI có dropdown tỉnh/thành và phường/xã. Lấy **_id của tỉnh** làm provinceId, **_id của phường** làm wardId. Khi đổi tỉnh, xóa phường đã chọn và tải lại danh sách phường của tỉnh mới.

Onboarding hiện dùng địa chỉ GHN mới: **provinceId + wardId**. Không gửi districtId, wardCode hoặc các field validation của schema địa chỉ cũ trong request onboarding.

### 3.2. Lưu shop / địa chỉ

~~~http
PUT /api/v1/seller-onboarding/me
~~~

~~~json
{
  "shopName": "Shop SecondLife",
  "pickupAddress": {
    "name": "Nguyễn Văn A",
    "phone": "0912345678",
    "address": "123 Nguyễn Huệ",
    "provinceId": 123,
    "wardId": 456
  },
  "email": "shop@example.com",
  "phone": "0912345678"
}
~~~

**123 và 456 chỉ minh họa kiểu dữ liệu**: thay bằng _id thực tế của tỉnh và phường lấy từ GHN. Phường phải thuộc tỉnh đã chọn.

| Field | Ý nghĩa / validation |
|---|---|
| shopName | Bắt buộc, tối đa 30 ký tự |
| pickupAddress.name | Người liên hệ giao hàng cho GHN; bắt buộc, tối đa 100 ký tự |
| pickupAddress.phone | Bắt buộc, 9–15 chữ số, được có dấu + ở đầu |
| pickupAddress.address | Địa chỉ chi tiết số nhà / tên đường; bắt buộc, tối đa 500 ký tự |
| pickupAddress.provinceId, wardId | Số nguyên dương, đúng dữ liệu và quan hệ tỉnh/phường GHN |
| email | Email shop nhận OTP; đúng định dạng, tối đa 255 ký tự |
| phone | Điện thoại shop; bắt buộc, 9–15 chữ số, được có dấu + ở đầu |

BE trim tên shop, trim/lowercase email. Khi thay đổi thông tin onboarding, email phải được xác nhận lại. BE chặn thay đổi khi tài khoản đã là SELLER hoặc có hồ sơ đang hoạt động; FE khóa form chỉnh sửa trong những trạng thái đó.

### 3.3. Gửi OTP tới email shop đã lưu

~~~http
POST /api/v1/seller-onboarding/email/send-code
~~~

Không có body. Không gửi email khác trong request này.

Response data gồm **email, expiresAt, resendAvailableAt**. FE hiển thị email nhận mã và thời gian còn lại, chỉ cho gửi lại từ resendAvailableAt. Mã OTP được gửi qua email, không có trong response API. Không hardcode thời hạn/cooldown từ tài liệu cũ.

### 3.4. Xác nhận OTP

~~~http
POST /api/v1/seller-onboarding/email/verify
~~~

~~~json
{
  "otp": "123456"
}
~~~

Thay bằng mã thực nhận. OTP là chuỗi đúng 6 chữ số; giữ số 0 đầu.

Chỉ chuyển sang eKYC khi **data.emailVerified = true** và **data.canStartEkyc = true**. BE đồng thời lưu địa chỉ lấy hàng phục vụ vận chuyển. Không bắt người dùng nhập lại địa chỉ lấy hàng trước mỗi lần bán hàng.

## 4. Chụp ảnh và nộp hồ sơ

### 4.1. Form eKYC

UI gồm loại giấy tờ, số giấy tờ, ảnh mặt trước, ảnh mặt sau và selfie.

- VNPT hiện hỗ trợ luồng CITIZEN_ID và PASSPORT trong code; BUSINESS_LICENSE có trong enum nhưng provider VNPT hiện trả UNSUPPORTED_DOCUMENT.
- Với VNPT, gửi đủ ba ảnh JPEG/PNG thật. API media có thể hỗ trợ WEBP nhưng upload sang VNPT hiện chỉ chấp nhận JPEG/PNG.
- Hướng dẫn chụp rõ giấy tờ; selfie trực tiếp, đủ sáng, một khuôn mặt, không filter, không chụp lại màn hình. Hướng dẫn chụp không bảo đảm eKYC sẽ đạt.
- Không tự xác định eKYC đạt từ một điểm số trên FE.

### 4.2. Upload từng ảnh

~~~http
POST /api/v1/media/upload
Content-Type: multipart/form-data
~~~

Gọi ba lần. FormData có field **file** là file ảnh và field **folder = seller-verifications** (tùy chọn).

~~~javascript
async function uploadImage(file, accessToken, apiOrigin) {
  const form = new FormData();
  form.append('file', file);
  form.append('folder', 'seller-verifications');

  const response = await fetch(apiOrigin + '/api/v1/media/upload', {
    method: 'POST',
    headers: { Authorization: 'Bearer ' + accessToken },
    body: form
  });
  const body = await response.json();
  if (!response.ok || !body.success) {
    throw new Error(body.message || 'Upload ảnh thất bại');
  }
  return body.data.url;
}
~~~

Response HTTP 201 có data.url, publicId, format, bytes, originalFilename. Lưu URL đúng vị trí để gắn documentFrontUrl, documentBackUrl và selfieUrl.

Không dùng blob: URL hoặc base64 trong request hồ sơ. URL ảnh phải thuộc tài khoản Cloudinary BE đã cấu hình. Không cho nộp khi còn ảnh đang upload hoặc upload thất bại.

Có thể dùng **POST /api/v1/media/upload-multiple** với FormData field files lặp lại cho từng ảnh; FE phải gắn đúng kết quả vào từng vị trí ảnh.

### 4.3. clientSession và token

FE có thể tạo:

~~~javascript
const clientSession =
  'ANDROID_Web_1.0_Device_1.0.0_secondlife_' + Date.now();
const token = crypto.randomUUID();
~~~

Hai field này là định danh phiên/request được BE gửi VNPT, không phải JWT SecondLife hoặc credential VNPT. BE chuẩn hóa clientSession theo định dạng đang sử dụng trong code. Mỗi field tối đa 255 ký tự.

Tạo mới khi nộp hồ sơ mới, giữ context cho thao tác đang thực hiện. BE lưu chúng để retry hồ sơ cũ; FE không gửi lại context trong API ADMIN retry.

### 4.4. Nộp hồ sơ

~~~http
POST /api/v1/seller-verifications
~~~

~~~json
{
  "verificationType": "CITIZEN_ID",
  "documentNumber": "012345678901",
  "documentFrontUrl": "https://res.cloudinary.com/<cloud-name>/image/upload/<front-image>.jpg",
  "documentBackUrl": "https://res.cloudinary.com/<cloud-name>/image/upload/<back-image>.jpg",
  "selfieUrl": "https://res.cloudinary.com/<cloud-name>/image/upload/<selfie-image>.jpg",
  "clientSession": "ANDROID_Web_1.0_Device_1.0.0_swagger_1791772000000",
  "token": "cb7f2c23-c966-445a-9394-6b9b85a029be"
}
~~~

Thay số giấy tờ bằng số thật khớp ảnh, URL bằng kết quả upload và định danh phiên bằng giá trị mới.

- Số giấy tờ bắt buộc, tối đa 100 ký tự; các URL tối đa 1024 ký tự.
- DTO cho phép bỏ một số field nhưng VNPT cần selfie và thông tin phiên; FE gửi đủ như mẫu.
- Tài khoản chưa có SELLER, onboarding hoàn tất và không có hồ sơ khác ở SUBMITTED, EKYC_PENDING, RESUBMIT_REQUIRED, NEEDS_REVIEW.
- BE tạo hồ sơ, chạy eKYC và risk pipeline, trả **HTTP 201** cùng trạng thái hiện tại.
- FE khóa nút trong lúc gửi; không mặc định thông báo đã trở thành SELLER.
- Nếu FE mất response hoặc timeout, GET hồ sơ hiện tại trước khi quyết định gửi lại POST. POST tạo hồ sơ không phải API polling/retry.

## 5. UI người đăng ký theo trạng thái

| status | Nội dung / thao tác UI | API tiếp theo |
|---|---|---|
| Chưa có hồ sơ, data = null | Cho đăng ký seller; hoàn tất onboarding nếu cần | Luồng phần 3–4 |
| SUBMITTED | Đã tiếp nhận; khóa tạo mới | GET /api/v1/seller-verifications/me |
| EKYC_PENDING | Đang xử lý; nếu PROVIDER_ERROR, thông báo dịch vụ xác thực gặp lỗi và hồ sơ được giữ lại | GET /api/v1/seller-verifications/me |
| NEEDS_REVIEW | Chờ quản trị viên xem xét, hiển thị lý do nếu có | GET /api/v1/seller-verifications/me |
| RESUBMIT_REQUIRED | Hiển thị lý do, form thay ảnh và nút Nộp lại | POST /api/v1/seller-verifications/{id}/resubmit |
| REJECTED | Hiển thị lý do, nút Đăng ký lại bằng hồ sơ mới | POST /api/v1/seller-verifications |
| APPROVED | Đăng ký thành công, ẩn nút tạo/nộp lại hồ sơ | GET /api/v1/users/me, cập nhật roles/permissions và mở chức năng SELLER |

Khi còn chờ xử lý, có nút **Cập nhật trạng thái**. Có thể polling GET với khoảng chờ phù hợp; dừng khi rời trang, đăng xuất hoặc kết thúc chờ xử lý. Không lặp liên tục và không polling bằng POST.

Dùng reasonCodeDescription và rejectionReason để giải thích kết quả. Các điểm faceMatchScore/livenessScore/documentScore chỉ để tham khảo; FE không dùng ngưỡng 0.85 hoặc tự suy ra APPROVED. Khuôn mặt khớp cao không có nghĩa liveness đã đạt.

### Khi APPROVED

BE cấp thêm role SELLER. FE đọc lại profile để cập nhật quyền; refresh phiên hoặc đăng nhập lại nếu cơ chế quản lý phiên FE cần đồng bộ. Không tự cấp permission ở client.

## 6. Nộp lại ảnh và đăng ký lại: hai nhánh khác nhau

### 6.1. RESUBMIT_REQUIRED: nộp lại cùng hồ sơ

~~~http
POST /api/v1/seller-verifications/{verificationId}/resubmit
~~~

~~~json
{
  "documentFrontUrl": "https://res.cloudinary.com/<cloud-name>/image/upload/<new-front>.jpg",
  "documentBackUrl": "https://res.cloudinary.com/<cloud-name>/image/upload/<new-back>.jpg",
  "selfieUrl": "https://res.cloudinary.com/<cloud-name>/image/upload/<new-selfie>.jpg",
  "clientSession": "ANDROID_Web_1.0_Device_1.0.0_swagger_1791773000000",
  "token": "7c70f2b0-1f52-4e48-a5a3-552d8b853b50"
}
~~~

- Lấy verificationId từ hồ sơ hiện tại. Gửi cả URL mặt trước và mặt sau; có thể giữ URL của ảnh không cần đổi.
- Với VNPT, gửi đủ selfie và thông tin phiên.
- API giữ ID hồ sơ, tăng resubmissionCount và chạy lại pipeline.
- Không nhận documentNumber/verificationType để đổi giấy tờ qua schema resubmit.
- **Không cần ADMIN gọi request-resubmit lần nữa** khi hồ sơ đã RESUBMIT_REQUIRED.
- Giới hạn nộp lại do VNPT_EKYC_MAX_RESUBMISSIONS của BE quyết định, không hardcode luôn 3 lần trên FE. Khi BE trả NEEDS_REVIEW, đóng form nộp lại và chuyển UI sang chờ kiểm duyệt. Nếu BE trả lỗi giới hạn/trạng thái, đọc lại hồ sơ và hiển thị message.

### 6.2. REJECTED: tạo hồ sơ mới

Hồ sơ REJECTED đã kết thúc. Không gọi resubmit, retry-ekyc hoặc request-resubmit trên hồ sơ này.

User có thể upload ảnh mới rồi POST /api/v1/seller-verifications với JSON ở phần 4.4 để tạo hồ sơ mới. BE vẫn kiểm tra onboarding, role SELLER, hồ sơ đang hoạt động và các hạn chế/rủi ro danh tính; tạo hồ sơ mới không bảo đảm được duyệt.

Ví dụ LIVENESS_FAILED: hiển thị lý do, hướng dẫn chụp lại selfie và mở nhánh tạo hồ sơ mới. Không kết luận giấy tờ giả chỉ từ lỗi liveness.

### API đọc bổ sung

~~~http
GET /api/v1/seller-verifications/me/history
GET /api/v1/seller-verifications/me/status/{status}
GET /api/v1/seller-verifications/{verificationId}
~~~

Lịch sử dành cho màn hình các lần đăng ký. User chỉ đọc detail hồ sơ của mình. Dùng documentNumberMasked để hiển thị số giấy tờ đã che. Một số field có thể null hoặc eventHistory rỗng trong response thao tác; đọc lại detail khi cần thông tin cập nhật/timeline.

## 7. UI ADMIN

### 7.1. Danh sách và chi tiết

~~~http
GET /api/v1/admin/seller-verifications?status=NEEDS_REVIEW&page=0&size=20
GET /api/v1/admin/seller-verifications?status=EKYC_PENDING&ekycStatus=PROVIDER_ERROR&page=0&size=20
GET /api/v1/admin/seller-verifications/{id}
~~~

Yêu cầu **role ADMIN + SELLER_VERIFICATION_READ_ANY**.

API danh sách hỗ trợ filter tùy chọn status, ekycStatus, riskStatus, reasonCode. Phân trang trả data.items, page, size, totalElements, totalPages, isLast. Bỏ sort nếu dùng sắp xếp mặc định; không gửi sort=["asc"].

Trang chi tiết hiển thị người đăng ký, shop/địa chỉ nếu có, ảnh, trạng thái, lý do, các điểm eKYC/rủi ro, providerName, providerReferenceId và eventHistory.

### 7.2. Các nút và điều kiện BE

Các thao tác cần **role ADMIN + SELLER_VERIFICATION_REVIEW**.

| Nút | Điều kiện theo code hiện tại | API POST | Body |
|---|---|---|---|
| Duyệt | SUBMITTED, EKYC_PENDING hoặc NEEDS_REVIEW; BE còn kiểm tra hạn chế danh tính | /api/v1/admin/seller-verifications/{id}/approve | Không body |
| Từ chối | SUBMITTED, EKYC_PENDING hoặc NEEDS_REVIEW | /api/v1/admin/seller-verifications/{id}/reject | rejectionReason |
| Yêu cầu nộp lại | SUBMITTED, EKYC_PENDING hoặc NEEDS_REVIEW | /api/v1/admin/seller-verifications/{id}/request-resubmit | rejectionReason |
| Thử lại eKYC | Đồng thời status = EKYC_PENDING và ekycStatus = PROVIDER_ERROR | /api/v1/admin/seller-verifications/{id}/retry-ekyc | Không body |

Khóa nút lúc gửi, cập nhật trạng thái từ response rồi đọc lại detail/list. Nếu 409 do hồ sơ đã đổi trạng thái, tải lại hồ sơ để cập nhật nút hợp lệ.

Duyệt thủ công là quyết định của ADMIN; FE không tự gọi approve khi nhà cung cấp bị lỗi, dù BE cho phép thao tác ở trạng thái pending.

### 7.3. JSON từ chối hoặc yêu cầu bổ sung

~~~json
{
  "rejectionReason": "Ảnh mặt sau bị lóa. Vui lòng chụp lại rõ toàn bộ giấy tờ."
}
~~~

Tên field vẫn là **rejectionReason**, kể cả khi gọi request-resubmit. Bắt buộc không rỗng, tối đa 1000 ký tự.

Sau request-resubmit, hồ sơ chuyển RESUBMIT_REQUIRED. User đọc lại /me, mở form thay ảnh và gọi /resubmit; ADMIN không upload ảnh thay user bằng API này.

### 7.4. Retry là thao tác nghiệp vụ, không phải nút duyệt

ADMIN retry sau khi cấu hình/kết nối VNPT được xử lý hoặc dịch vụ phục hồi. BE dùng ảnh và thông tin đã lưu của hồ sơ cũ để chạy lại pipeline.

- Không tạo hồ sơ mới, không xóa hồ sơ cũ.
- HTTP 200 không có nghĩa được duyệt; kết quả có thể APPROVED, REJECTED, RESUBMIT_REQUIRED, NEEDS_REVIEW hoặc vẫn EKYC_PENDING.
- Nếu vẫn pending, user không tạo lại hồ sơ; nếu REJECTED, chuyển sang nhánh tạo mới.
- Không hiển thị nút ADMIN retry trên UI người đăng ký.
- BE có recovery tự động khi được bật bằng cấu hình. FE vẫn chỉ đọc trạng thái.

## 8. Xử lý lỗi FE

| Tình huống | Xử lý |
|---|---|
| HTTP 400 | Hiển thị message và errors[].field/message, giữ dữ liệu form để sửa |
| HTTP 401 | Refresh/đăng nhập lại theo cơ chế auth FE; không nhầm với lỗi VNPT trong hồ sơ |
| HTTP 403 | Báo không đủ quyền hoặc hồ sơ không thuộc user |
| HTTP 409 khi tạo hồ sơ | GET /me; chuyển sang hồ sơ đang hoạt động thay vì tiếp tục POST |
| HTTP 409 onboarding/OTP | Đọc message: thông tin bị khóa, email đã xác nhận hoặc chưa hết cooldown |
| HTTP 409 resubmit/retry/admin | Đọc lại hồ sơ, cập nhật thao tác hợp lệ |
| HTTP 502 khi lấy địa chỉ/upload | Báo lỗi dịch vụ; không tự tạo ID GHN/URL ảnh giả |
| HTTP 200/201 nhưng EKYC_PENDING + PROVIDER_ERROR | Hồ sơ đã tiếp nhận, eKYC chưa hoàn tất |
| HTTP 200/201 nhưng REJECTED | Hiển thị từ chối và lý do, không báo xác thực thành công |

Không catch lỗi rồi trả dữ liệu giả như đã upload/lưu/xác thực thành công. Nếu mất response POST, đọc hồ sơ trước khi gửi lại.

PROVIDER_AUTH_FAILED là lỗi BE xác thực với VNPT. User thay token trong JSON hoặc nộp nhiều hồ sơ không sửa được lỗi này; ADMIN/BE kiểm tra cấu hình rồi retry hồ sơ hợp lệ.

## 9. API VNPT trực tiếp và cấu hình BE

**POST /api/v1/ekyc/verify** chỉ xuất hiện khi provider VNPT được bật. Nó nhận FormData: frontImage, backImage, selfieImage, clientSession, token; cũng yêu cầu onboarding hoàn tất.

Đây là API kiểm tra VNPT trực tiếp, không thay thế việc lưu hồ sơ/cấp role qua /seller-verifications. Luồng đăng ký seller ở tài liệu này không cần gọi cả hai API nối tiếp để tránh chạy eKYC hai lần.

Các biến BE hiện đọc:

~~~env
VNPT_EKYC_PROVIDER=
VNPT_EKYC_API_BASE_URL=
VNPT_EKYC_SERVER_CLIENT_ID=
VNPT_EKYC_SERVER_CLIENT_SECRET=
VNPT_EKYC_TOKEN_ID=
VNPT_EKYC_TOKEN_KEY=
VNPT_EKYC_MAC_ADDRESS=
VNPT_EKYC_TIMEOUT_MS=
~~~

Credential VNPT thuộc BE, không đưa vào FE hoặc JSON đăng ký. OAuth access token được BE lấy/cache tự động. Các tên VNPT_CLIENT_ID / VNPT_CLIENT_SECRET không phải fallback trong application.properties hiện tại.

Timeout, retry, OTP, upload size và giới hạn nộp lại do cấu hình BE quyết định. FE không suy ra giá trị cố định từ tài liệu cũ.

## 10. Checklist test UI

| Case | Thao tác | Kết quả FE cần kiểm tra |
|---|---|---|
| Đăng ký lần đầu | Lưu shop/địa chỉ → OTP → upload → nộp hồ sơ | Có ID, UI theo status |
| Chưa xác nhận email | Nộp hồ sơ khi onboarding chưa xong | BE chặn, quay lại bước cần hoàn tất |
| Tạo trùng | POST khi hồ sơ đang hoạt động | 409, đọc lại /me |
| Nộp lại | RESUBMIT_REQUIRED → thay ảnh → /resubmit | Cùng ID, tăng resubmissionCount, đọc trạng thái mới |
| Lỗi nhà cung cấp | EKYC_PENDING/PROVIDER_ERROR → ADMIN retry | User không được tạo mới khi còn pending |
| Từ chối | REJECTED → ảnh mới → POST tạo mới | ID hồ sơ mới; hồ sơ cũ vẫn trong history |
| Kiểm duyệt | NEEDS_REVIEW → ADMIN thao tác | UI user cập nhật theo GET |
| Được duyệt | APPROVED → GET profile | Roles có SELLER, mở chức năng bán hàng |
| Reload/timeout FE | Reload trang hoặc mất response | Đọc dữ liệu BE trước, không POST trùng tự động |

Test nhánh UI bằng MOCK: BE cấu hình VNPT_EKYC_PROVIDER=MOCK. Số giấy tờ có hậu tố 8801/8802 giả lập ảnh mờ/lóa; 8803 cần xem xét; 9904 liveness thất bại; 7701/7702 timeout/unavailable. Các hậu tố chỉ dùng với MOCK, không thay số giấy tờ thật khi test VNPT. Nhánh APPROVED còn phụ thuộc đánh giá rủi ro, không chỉ kết quả MOCK eKYC.
