# Kết quả kiểm chứng tối ưu Identity

Ngày kiểm tra: **27/10/2026**. Chỉ sửa backend, giữ cấu trúc layer và các FK. Không commit/push hoặc áp role lên database ứng dụng.

## Kết quả cuối

- **198 test thuộc 21 class liên quan: 0 failure, 0 error, 0 skipped.** Đây không phải toàn bộ test suite của dự án.
- `mvn -DskipTests package`: **BUILD SUCCESS**.
- `git diff --check`: đạt; migration V1–V62 không thay đổi.
- PostgreSQL test riêng chỉ lắng nghe loopback, đã dừng sau khi kiểm tra. Không lấy cấu hình DB ứng dụng làm fixture.

| Test class | Test | Fail | Error | Skip |
| --- | ---: | ---: | ---: | ---: |
| AccessTokenAuthenticationTest | 17 | 0 | 0 | 0 |
| AdminRbacServiceTest | 10 | 0 | 0 | 0 |
| AdminUserRoleServiceTest | 3 | 0 | 0 | 0 |
| AuthControllerTest | 31 | 0 | 0 | 0 |
| AuthServiceTest | 14 | 0 | 0 | 0 |
| AuthenticationCacheIntegrationTest | 6 | 0 | 0 | 0 |
| GoogleAuthServiceImplTest | 4 | 0 | 0 | 0 |
| IdentityAccessServiceTest | 3 | 0 | 0 | 0 |
| JwtTokenProviderTest | 9 | 0 | 0 | 0 |
| ListingAccessServiceImplTest | 10 | 0 | 0 | 0 |
| ModuleBoundariesTest | 4 | 0 | 0 | 0 |
| PackageArchitectureTest | 6 | 0 | 0 | 0 |
| ProfileGenderServiceTest | 3 | 0 | 0 | 0 |
| RbacAuthorizationTest | 7 | 0 | 0 | 0 |
| RuntimeDatabasePrivilegesIntegrationTest | 4 | 0 | 0 | 0 |
| SecureLoginApiTest | 32 | 0 | 0 | 0 |
| TokenCleanupSchedulerTest | 2 | 0 | 0 | 0 |
| TokenServiceTest | 13 | 0 | 0 | 0 |
| UserForeignKeyIndexesMigrationTest | 5 | 0 | 0 | 0 |
| UserPrincipalCacheTest | 7 | 0 | 0 | 0 |
| UserServiceTest | 8 | 0 | 0 | 0 |

## Bằng chứng chính

- Trước sửa: ba ca mới tái hiện việc token vẫn xác thực được sau khi user bị khóa, đổi tokenVersion hoặc xóa trong khi cache còn ấm.
- Sau sửa: cả HTTP và WebSocket CONNECT từ chối đúng các trường hợp đó; DB lỗi không làm fallback sang principal cũ.
- PostgreSQL/JPA: cache lạnh đúng hai query; cache ấm đúng một query chứa revision nhưng không fetch profile/permission collections.
- Hai cache độc lập phát hiện password/account/permission changes; rollback giữ snapshot và cache đã commit.
- Cache kiểm tra TTL, LRU/capacity, generation fence và afterCommit/rollback.
- V63 kiểm tra V62→V63, giữ page/count/dữ liệu fixture, tránh index tương đương, audit index composite/partial/expression/INCLUDE/invalid. Ba query lịch sử dùng index và không Sort trên workload 100.000 order + 100.000 negotiation.
- Script psql thực sự chạy trong schema/role test riêng: DML nghiệp vụ được phép; user DELETE/TRUNCATE, schema DDL và Flyway history writes bị từ chối. Kiểm tra chạy lại script, ownership/membership và table mới chưa được cấp quyền.
- Test module phát hiện hai vi phạm trước khi thêm seam Identity→Listing, sau đó đạt cùng các nhánh quyết định Listing cũ.
- Review độc lập chỉ đọc không ghi nhận lỗi cần sửa trong thay đổi đã xem.

## Giới hạn phạm vi

Execution plan là workload tổng hợp, không phải số đo latency production. SQL privilege test không thay thế việc kiểm tra login, RLS, schema validation và cấu hình deployment thực tế. Profile runtime cần được bật theo hướng dẫn; các quyền ở database đang chạy chưa thay đổi. Kiểm tra JWT tại CONNECT không tự ngắt session WebSocket đang mở.

Xem [hướng dẫn tổng hợp](IDENTITY_OPTIMIZATION_GUIDE.md) và [triển khai role DB](DATABASE_RUNTIME_ROLE_GUIDE.md). Log red/green, package và JSON chi tiết được lưu trong thư mục QA local `identity-optimization-20261027`.
