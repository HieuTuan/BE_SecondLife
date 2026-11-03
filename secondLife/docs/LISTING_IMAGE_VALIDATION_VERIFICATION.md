# Kiểm chứng BE: validation ảnh đăng bán

Ngày: 28/10/2026.

## Kết quả

- Bản cuối: **83 test thuộc 10 nhóm đạt, 0 fail, 0 error, 0 skipped**. Bao gồm HTTP multipart thực trên Tomcat và 3 test JWT/PostgreSQL Testcontainers.
- `mvn -o -DskipTests package`: **BUILD SUCCESS** trên bản cuối.
- Đã chạy cả project ở checkpoint trước hai nhánh hardening cuối: **1.321 test, 36 failure, 4 error, 4 skipped**. Không coi toàn bộ suite là xanh.
- Chạy lại toàn bộ 15 nhóm đang lỗi trên commit gốc `d41171e7e5ac6d521a7c4f39e76e939fc140299b` (chưa có validation mới), với cùng cấu hình test giả: **131 test, 36 failure, 4 error**. Đối chiếu chính xác tên test và loại lỗi: **0 lỗi mới, 0 lỗi chỉ có trên baseline**.
- Sau checkpoint full-suite, bổ sung bỏ qua metadata phụ nén và response 400 khi thiếu multipart part. Toàn bộ 83 test liên quan và package đã được chạy lại trên bản cuối; không chạy lại toàn bộ 1.321 test chỉ cho hai nhánh này.

## Nhóm test bản cuối

| Nhóm | Tests | Fail/Error/Skipped |
|---|---:|---|
| PackageArchitectureTest | 6 | 0 / 0 / 0 |
| ImageUploadValidatorTest | 44 | 0 / 0 / 0 |
| ImageMultipartHttpTest | 3 | 0 / 0 / 0 |
| ImageUploadErrorContractTest | 3 | 0 / 0 / 0 |
| MediaControllerTest | 5 | 0 / 0 / 0 |
| ListingImageUploadIntegrationTest | 3 | 0 / 0 / 0 |
| CloudinaryMediaImageCleanupTest | 3 | 0 / 0 / 0 |
| CloudinaryImageUploadTest | 2 | 0 / 0 / 0 |
| ListingImageValidationTest | 6 | 0 / 0 / 0 |
| MediaServiceTest | 8 | 0 / 0 / 0 |

## Lỗi nền đã tái hiện trên code gốc

| Nhóm | Fail | Error |
|---|---:|---:|
| AdminRbacIntegrationTest | 1 | 0 |
| AdminUserRolesIntegrationTest | 2 | 0 |
| AiCreditV34MigrationTest | 4 | 0 |
| CatalogIntegrationTest | 1 | 1 |
| Day03AccountSellerIntegrationTest | 1 | 0 |
| Day04SellerVerificationIntegrationTest | 1 | 0 |
| Day05CreditIntegrationTest | 1 | 2 |
| Day06CreditPurchaseIntegrationTest | 6 | 0 |
| DefaultCommissionMigrationTest | 1 | 0 |
| MainFlow1IntegrationTest | 4 | 0 |
| OrderConfirmationIntegrationTest | 0 | 1 |
| ReadQueryIntegrationTest | 2 | 0 |
| SellerReputationIntegrationTest | 9 | 0 |
| SharedBankTransferMigrationTest | 2 | 0 |
| SecondLifeApplicationTests | 1 | 0 |

Các nhóm này liên quan RBAC, dữ liệu seed, credit/pricing, điều kiện tranh chấp, uy tín, giới hạn query, migration và namespace OpenAPI. Không sửa nghiệp vụ hoặc đổi expected của chúng để che lỗi trong nhiệm vụ ảnh.

## Bằng chứng regression

- Trước sửa: 3 test chứng minh ảnh hỏng ở cuối batch vẫn có thể dẫn đến upload ảnh đầu, ảnh dưới 600px chưa bị chặn và ảnh trùng vẫn được tính.
- Trước sửa cleanup media: 2 test fail vì không xóa được asset đang thử upload / không xác minh đúng ID provider.
- Bỏ guard ngân sách batch: test dùng JPEG + WebP có input tổng <40KB nhưng output tổng >40KB thất bại do đã gọi storage. Khôi phục guard: 413/files, không gọi storage.
- Trước hardening: metadata zTXt không cần thiết vẫn bị parser giải nén; thiếu multipart part `file` trả 500. Bản cuối bỏ qua metadata phụ, vẫn đọc EXIF qua parser bounded và trả 400/file khi thiếu part.

## Phạm vi và cấu hình

- API giữ nguyên: POST /api/v1/posts/init và response thành công. Kiểm tra 3–6 ảnh, <=10MiB, cạnh ngắn >=600px, <=16000px/cạnh, <=25M pixel, nội dung JPEG/PNG/WebP thực, ảnh tĩnh, không trùng.
- Chuẩn hóa chiều xoay rồi reencode, bỏ EXIF/GPS/metadata nguồn; giữ alpha bằng PNG. Giữ hash gốc cho đối chiếu legacy và tính hash nội dung chuẩn hóa cho trùng trong batch.
- Generic media không áp minimum/count của listing; có cùng kiểm tra file an toàn và ngân sách batch sau chuẩn hóa theo max-request-size.
- max-request-size phải hữu hạn và >0; giá trị hiện tại 64MB được giữ nguyên. Hai cấu hình mới có default, không bắt sửa .env: LISTING_IMAGE_MIN_SHORT_SIDE=600 và LISTING_IMAGE_NORMALIZED_MAX_SIDE=2560.
- Giữ ngữ nghĩa AI/manual, review trùng giữa các bài, credit, trạng thái draft và API evidence riêng. Không sửa FE, dependency, entity/schema/migration hoặc .env thật.
- Bổ sung các biến seeder test giả còn thiếu để test không dựa vào .env của developer; cùng overlay được dùng ở baseline. Test credentials chỉ nằm trong src/test/resources.

## Giới hạn

- Cloudinary được mock trong test tự động. Swagger với tài khoản Cloudinary thật mới kiểm chứng được credentials, quota và kết nối môi trường đó.
- Cleanup asset là best effort. Nếu provider/mạng lỗi trong bước xóa, BE giữ lỗi gốc và log ID để đối chiếu; không tuyên bố transaction phân tán nguyên tử.
- Ảnh mờ/tối, nhãn khó đọc, đúng loại sản phẩm hay gian lận là lớp chất lượng/nghiệp vụ; không thêm ngưỡng AI hard-block mới.

## Môi trường kiểm thử

Java 21.0.10, Maven 3.9.11, SDK/thư viện đúng pom.xml hiện hữu. PostgreSQL 16 từ Testcontainers riêng; không dùng database/Cloudinary thật.
Trong môi trường Codex trên Windows, Mockito tự attach và AF_UNIX của JDK gặp lỗi. Test JVM nạp Mockito agent sẵn và dùng đường dẫn Unix socket không tồn tại để JDK fallback TCP. Chỉ áp dụng cho lệnh test, không thêm workaround này vào cấu hình chạy BE.

Xem hướng dẫn test và JSON: [SWAGGER_LISTING_IMAGE_VALIDATION_TEST.md](SWAGGER_LISTING_IMAGE_VALIDATION_TEST.md).
