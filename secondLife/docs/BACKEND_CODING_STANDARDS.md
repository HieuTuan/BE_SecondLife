# Quy chuẩn viết code Backend SecondLife

Tài liệu dùng khi viết mới, sửa lỗi, refactor và review backend. Nội dung tổng hợp các quyết định đã thống nhất trong quá trình chỉnh sửa dự án.

**Nguyên tắc chính:** code dễ đọc, ít trùng lặp, đúng trách nhiệm và giữ nguyên hành vi API khi refactor. Không đổi nghiệp vụ chỉ để code ngắn hơn hoặc test chuyển sang trạng thái đạt.

## 1. Phạm vi và mức độ áp dụng

- **Bắt buộc:** cấu trúc thư mục, Service + ServiceImpl, DTO record, mapper class dùng MapStruct, repository interface dùng JpaRepository, cách import và quy tắc bảo toàn hành vi.
- **Hướng dẫn thiết kế:** SOLID, giới hạn transaction, tối ưu query và tổ chức kiểm thử. Áp dụng theo luồng thực tế; không dùng tài liệu này làm lý do tự ý thay đổi nghiệp vụ hiện có.
- Code mới và phần được sửa phải theo chuẩn. Không sửa hàng loạt file ngoài phạm vi công việc chỉ để đồng nhất hình thức.
- Nếu yêu cầu công việc mâu thuẫn với tài liệu, chỉ rõ điểm khác và thống nhất trước thay đổi có ảnh hưởng đến contract, phân quyền hoặc nghiệp vụ.
- Các mẫu Java bên dưới minh họa cách tổ chức; không thay thế toàn bộ interface hoặc class đang có.

Nguồn xác định phiên bản Java, Spring Boot và thư viện là [pom.xml](../pom.xml). Không tự nâng phiên bản hoặc thêm dependency khi thư viện hiện có đã đáp ứng.

## 2. Cấu trúc thư mục đã chốt

Tổ chức theo layer trên toàn backend, với package gốc `com.secondlife.secondlife`:

```text
secondLife/
├── pom.xml
├── docs/
└── src/
    ├── main/
    │   ├── java/com/secondlife/secondlife/
    │   │   ├── SecondLifeApplication.java
    │   │   ├── controller/
    │   │   ├── dto/
    │   │   │   ├── request/
    │   │   │   └── response/
    │   │   ├── entity/
    │   │   ├── repository/
    │   │   ├── mapper/
    │   │   ├── service/
    │   │   │   └── impl/
    │   │   ├── integration/
    │   │   ├── scheduler/
    │   │   ├── seeder/
    │   │   ├── config/
    │   │   ├── security/
    │   │   ├── exception/
    │   │   ├── enums/
    │   │   └── common/
    │   │       └── validation/
    │   └── resources/
    │       └── db/migration/
    └── test/
        ├── java/com/secondlife/secondlife/
        └── resources/
```

- Không tạo lại package gốc theo tính năng như `catalog/controller`, `catalog/service`, `order/repository`.
- Trong `dto/request`, `dto/response`, `repository` và `mapper`, đặt các file trực tiếp theo cấu trúc hiện tại; không tự thêm tầng thư mục theo tính năng.
- Service interface đặt trực tiếp trong `service/`; implementation đặt trong `service/impl/`.
- `integration/` có thể chia theo hệ thống ngoài: `ai/`, `auth/`, `storage/`, `shipping/`, `payment/`, `ekyc/`…
- Package phải khớp vị trí file. Test đặt trong package tương ứng với phần code được kiểm tra.

## 3. Trách nhiệm của từng layer

| Layer | Trách nhiệm | Giới hạn |
|---|---|---|
| `controller` | Nhận request, validate đầu vào, phân quyền endpoint, lấy principal, gọi service và trả HTTP response | Không inject repository, EntityManager hoặc ServiceImpl; không xử lý nghiệp vụ trong controller |
| `dto/request` | Dữ liệu đầu vào và validation hình dạng/giới hạn dữ liệu | Không dùng JPA entity làm field hoặc request body |
| `dto/response` | Contract dữ liệu trả cho FE | Không chứa entity hoặc quan hệ LAZY của JPA |
| `service` | Interface nghiệp vụ | Không chứa implementation nghiệp vụ |
| `service/impl` | Kiểm tra nghiệp vụ, ownership, điều phối repository/integration, transaction | Không dựng HTTP response hoặc phụ thuộc controller |
| `repository` | Truy cập và ghi dữ liệu, query, projection cần thiết | Không gửi email, gọi AI/carrier hoặc điều phối workflow |
| `mapper` | Chuyển entity/dữ liệu đã có thành DTO bằng MapStruct | Không truy vấn repository, kiểm tra quyền hoặc gọi dịch vụ ngoài |
| `integration` | Giao tiếp với hệ thống ngoài, timeout và chuyển đổi dữ liệu provider | Không đặt quy tắc nghiệp vụ SecondLife vào HTTP client của provider |
| `scheduler` | Chọn công việc đến hạn, điều phối theo batch và gọi service | Không nhân bản nghiệp vụ đang có trong service |
| `seeder` | Điều phối dữ liệu khởi tạo theo cấu hình | Không thay thế migration quản lý schema |
| `config` | Cấu hình và khai báo bean | Không dùng làm nơi chứa nghiệp vụ |
| `security` | Xác thực, principal, filter/interceptor và xử lý lỗi bảo mật | Không đặt validation mật khẩu dùng chung tại đây |
| `common` | Kiểu dữ liệu/hàm dùng chung có phạm vi rõ ràng | Không biến thành nơi gom mọi helper không biết đặt ở đâu |

## 4. DTO request/response phải dùng record

DTO API mới trong `dto/request/` và `dto/response/` dùng `record`. Entity JPA vẫn là class.

```java
package com.secondlife.secondlife.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record AiChatRequest(
        UUID sessionId,
        UUID postId,
        @NotBlank @Size(max = 4000) String message
) {
}
```

Ví dụ trên rút gọn các field để minh họa; khi chuyển DTO thực tế phải giữ đầy đủ field hiện có.

- Truy cập component bằng `request.message()`, không gọi `getMessage()` hoặc setter.
- Không dùng `@Data`, `@Setter`, `@NoArgsConstructor` để biến record thành DTO mutable.
- Chỉ giữ `@Builder` hoặc constructor bổ sung nếu có caller/contract thực tế cần tương thích.
- Cập nhật toàn bộ caller, mapper, serialization/deserialization và multipart binding khi chuyển class sang record.
- Không đổi tên JSON field, kiểu, định dạng ngày, giá trị mặc định, cách biểu diễn `null` hoặc validation ngoài phạm vi được yêu cầu.
- Record không tự làm collection bên trong bất biến. Chỉ copy/đóng gói collection khi đúng yêu cầu; lưu ý `List.copyOf`/`Set.copyOf` không chấp nhận phần tử `null`.
- Không tự trim, đổi hoa/thường hoặc chuẩn hóa dữ liệu trong constructor nếu việc đó làm thay đổi contract đang có.

## 5. Validation và import

### 5.1. Import ở đầu file

```java
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional(propagation = Propagation.REQUIRES_NEW)
public void execute() {
    // ...
}
```

```java
@Size(max = 4000)
String message
```

- Dùng `@Size`, `List<MultipartFile>`, `Propagation.REQUIRES_NEW`, `LockModeType.PESSIMISTIC_WRITE` với import tương ứng.
- Không viết `@jakarta.validation.constraints.Size(...)`, `java.util.List<...>` hoặc tên package đầy đủ trong thân code khi có thể import rõ ràng.
- Không static-import enum như `REQUIRES_NEW`; dùng `Propagation.REQUIRES_NEW`.
- Code mới dùng import tường minh, không wildcard; xóa import thừa và trùng. Static import assertion trong test được phép.
- Sắp xếp import theo nhóm thống nhất với file: project, thư viện/framework, Java; theo alphabet trong từng nhóm.
- Ngoại lệ tên trùng: Java không hỗ trợ import alias. Nếu cần cả hai `ApiResponse`, import kiểu của project và có thể ghi đầy đủ tên annotation Swagger. Không đổi contract chỉ để tránh trùng tên.
- Tên kiểu đầy đủ bắt buộc trong JPQL constructor expression không phải import thừa. Không sửa chuỗi JPQL như sửa tên kiểu Java trong code.

### 5.2. Vị trí validation dùng chung

`StrongPassword` và `StrongPasswordValidator` đặt trong `common/validation/`.

- DTO import annotation validation dùng chung; không nhân bản regex mật khẩu ở nhiều request.
- `@Pattern` phù hợp cho định dạng đơn giản tại field. Constraint tùy biến dùng lại nhiều nơi phải có trách nhiệm rõ ràng.
- Không thay đổi regex, thông báo hoặc điều kiện hợp lệ của mật khẩu khi chỉ được yêu cầu chuyển package/tối ưu import.
- Validation đầu vào không thay thế kiểm tra ownership, trạng thái và nghiệp vụ trong service.

## 6. Service + ServiceImpl

Giữ cặp interface và implementation cho service nghiệp vụ:

```text
service/CatalogService.java
service/impl/CatalogServiceImpl.java
```

- Implementation là class thông thường, `implements` interface, được quản lý bởi Spring.
- Controller inject `CatalogService`, không inject `CatalogServiceImpl`.
- Dùng constructor injection và field `final`; `@RequiredArgsConstructor` được phép.
- Không tự `new ServiceImpl(...)` trong đường chạy Spring để né dependency injection hoặc proxy transaction.
- Không tạo thêm interface/factory/strategy cho helper nhỏ khi chưa có nhu cầu thực tế. Quy tắc Service + ServiceImpl không bắt mọi helper, filter hay mapper phải có cặp interface/impl.
- Logic dùng chung phải được gom ở đúng service/helper; không copy giữa controller, scheduler, HTTP filter và WebSocket interceptor.
- Tách service khi có trách nhiệm nghiệp vụ độc lập; không chia mỗi method thành một service mới.
- Khi sửa hàm dùng chung, rà soát tất cả caller và các nhánh lỗi liên quan.

## 7. Mapper là class thông thường, dùng MapStruct

Mapper mà service inject là **concrete class**, không phải record hoặc abstract class. Giữ mô hình đang dùng: class `@Component` ủy quyền cho nested interface `Delegate` để MapStruct sinh implementation.

```java
package com.secondlife.secondlife.mapper;

import com.secondlife.secondlife.config.MapStructConfiguration;
import com.secondlife.secondlife.dto.response.CategoryResponse;
import com.secondlife.secondlife.entity.Category;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {
    private final Delegate delegate;

    public CategoryMapper(Delegate delegate) {
        this.delegate = delegate;
    }

    public CategoryResponse toResponse(Category category) {
        return delegate.toResponse(category);
    }

    @Mapper(config = MapStructConfiguration.class)
    public interface Delegate {
        CategoryResponse toResponse(Category category);
    }
}
```

Ví dụ minh họa hình thức; tái sử dụng `CatalogMapper` hiện có thay vì tạo mapper mới cho cùng mapping.

- Tái sử dụng `config/MapStructConfiguration`: Spring component model, constructor injection, báo lỗi field chưa map và cấu hình builder đang có.
- Nested `Delegate` là cơ chế để MapStruct sinh code; không phải service interface hoặc custom repository.
- Dùng `@Mapping` khi tên field khác nhau; chỉ viết helper cho phép biến đổi cần thiết mà mapping khai báo chưa diễn đạt rõ.
- Không dùng `ReportingPolicy.IGNORE` hoặc ignore field hàng loạt để làm build đạt khi chưa kiểm tra dữ liệu bị bỏ.
- Mapper không gọi DB. Service chuẩn bị đủ dữ liệu và fetch plan trước khi map để tránh query ẩn.
- Không chỉnh sửa file do MapStruct sinh trong `target/`.
- `MapStructConfiguration` hiện là abstract configuration class trong `config/`; điều này không mâu thuẫn với yêu cầu mapper concrete class trong `mapper/`.

## 8. Repository là interface extends JpaRepository

```java
package com.secondlife.secondlife.repository;

import com.secondlife.secondlife.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CategoryRepository extends JpaRepository<Category, UUID> {
}
```

- Repository persistence là interface kế thừa `JpaRepository<Entity, Id>`.
- Không tạo repository class thông thường hoặc `RepositoryImpl` riêng để thực hiện lại CRUD/query Spring Data đã hỗ trợ.
- Một repository chính cho mỗi entity; gom các query của cùng entity về repository đó khi cùng trách nhiệm.
- Không gom repository của entity khác nhau vào một interface lớn chỉ để giảm số interface.
- Ưu tiên derived query cho truy vấn đơn giản; dùng `@Query` khi cần diễn đạt điều kiện rõ hơn.
- Native SQL được phép khi cần đặc tính PostgreSQL hoặc JPQL không phù hợp; giữ tham số bind, không nối input người dùng vào SQL.
- Projection interface/record phục vụ query không phải repository trùng lặp. Chỉ thêm khi giảm dữ liệu tải và giúp đọc code rõ hơn.
- Giữ các hỗ trợ refresh/lock dùng chung hiện có khi workflow cần. Không tự thay bằng class repository riêng hoặc bỏ refresh để giảm query.
- Với projection, kiểm tra mapping thực tế bằng integration test. Tránh JPQL constructor trỏ vào nested type bằng tên `$` chỉ để vượt lỗi IDE.

## 9. Query và hiệu năng API

- Không gọi repository cho từng phần tử trong vòng lặp/map nếu có thể lấy theo tập ID mà giữ đúng kết quả.
- Lấy dữ liệu một lần, nhóm bằng `Map`, sau đó map DTO theo **thứ tự danh sách gốc**. Không dùng thứ tự kết quả `IN (...)` làm thứ tự response.
- Khi gom query, giữ nguyên xử lý dữ liệu thiếu, `null`, fallback, lỗi `NotFound`, filter và kiểm tra quyền.
- Trang rỗng không cần query bổ sung với danh sách ID rỗng.
- Tải quan hệ đơn cần thiết bằng fetch join/`@EntityGraph` có phạm vi rõ ràng; không bật EAGER toàn bộ entity để chữa N+1.
- Với collection và phân trang: phân trang entity/ID tại DB trước, rồi tải collection theo ID của trang. Không fetch collection trong query phân trang gây phân trang trong bộ nhớ.
- Mapper truy cập field nào thì phải kiểm tra entity/fetch plan tương ứng, kể cả `user.profile`, ảnh và role.
- Không bỏ `COUNT`, đổi `Page` sang `Slice`, thêm cursor, cache hay đổi response chỉ vì muốn giảm query khi chưa thống nhất contract.
- Cache không được làm chậm hiệu lực thu hồi quyền, token hoặc trạng thái tài khoản.
- Không thêm index theo phỏng đoán. Đối chiếu index hiện có và execution plan trên dữ liệu phù hợp; tính cả chi phí ghi.
- Đo SQL/request, dữ liệu tải và latency khi cần. Số query giảm chưa đủ để cam kết API nhanh hơn một tỷ lệ cụ thể.

## 10. Transaction, lock và dữ liệu tiền/credit

- Đặt transaction theo một đơn vị nghiệp vụ cần nguyên tử, thường tại service implementation; query chỉ đọc phù hợp dùng `readOnly = true`.
- Không đổi propagation, isolation, thứ tự lock, refresh hoặc thời điểm flush trong một đợt tối ưu đọc nếu chưa chứng minh tương đương.
- Không xóa `saveAndFlush()` hàng loạt: thời điểm flush có thể liên quan constraint, idempotency và xử lý lỗi.
- Thiết kế mới ưu tiên transaction ngắn; tránh giữ lock/connection trong lúc chờ AI, storage, SMTP hoặc hãng vận chuyển.
- Với workflow hiện có, tách lời gọi ngoài khỏi transaction là thay đổi cần phân tích riêng: trạng thái trung gian, kiểm tra lại dữ liệu, idempotency, rollback/compensation và request đồng thời.
- Không gọi nội bộ method cùng bean rồi mặc định `@Transactional` của method đó đã đi qua Spring proxy. Dùng ranh giới bean hoặc cơ chế transaction tường minh phù hợp khi cần.
- Tiền và tỷ lệ tính phí dùng `BigDecimal` với quy tắc scale/rounding nghiệp vụ; không dùng `double` để tính tiền.
- Giữ tính nguyên tử giữa trừ tiền, cấp/trừ credit và ledger khi nghiệp vụ yêu cầu. Retry/callback không được ghi tiền hoặc cấp quyền lợi hai lần cho cùng giao dịch.
- Không thay đổi idempotency key, tiền tệ, số tiền, trạng thái hoặc loại ledger entry trong refactor hình thức.

## 11. Security HTTP và WebSocket

```text
security/
├── CurrentUserProvider.java
├── authentication/
│   └── AccessTokenAuthenticator.java
├── jwt/
│   ├── JwtTokenProvider.java
│   └── JwtAuthenticationFilter.java
├── handler/
│   ├── RestAuthenticationEntryPoint.java
│   └── RestAccessDeniedHandler.java
├── filter/
├── userdetails/
└── websocket/
    └── WebSocketAuthChannelInterceptor.java
```

- HTTP và WebSocket CONNECT dùng chung `AccessTokenAuthenticator`; không nhân bản logic xác thực.
- Mỗi lượt xác thực parse JWT một lần, kiểm tra chữ ký/thời hạn/loại token và lấy thông tin user hiện tại từ DB.
- Kiểm tra `tokenVersion` và trạng thái `ACTIVE` nhất quán cho cả hai transport; quyền phải lấy từ dữ liệu tin cậy phía server.
- HTTP giữ principal `CustomUserDetails`; tên principal WebSocket giữ UUID string để `convertAndSendToUser` định tuyến đúng.
- Kiểm tra tại CONNECT không có nghĩa session WebSocket đang mở tự bị ngắt ngay khi quyền hoặc trạng thái thay đổi. Yêu cầu thu hồi session tức thời cần thiết kế riêng.
- Lấy user ID qua `CurrentUserProvider.resolveUserId()`. Không thêm `resolveAdminId()` chỉ để trả cùng ID; tên hàm lấy ID không chứng minh user có quyền ADMIN.
- Phân quyền endpoint bằng `@PreAuthorize`/security rule phù hợp; ownership và điều kiện nghiệp vụ vẫn phải được kiểm tra tại service.
- Quyền và role có ý nghĩa khác nhau. Không nới điều kiện phân quyền hoặc mặc định gán role để làm test đạt.
- Đóng gói tập authorities bằng `Set.copyOf(...)` như implementation hiện tại; không trả collection mutable để caller sửa quyền.
- Giữ contract 401/403, public route và private subscription hiện tại khi chỉ được yêu cầu refactor xác thực.

## 12. Contract API, exception và logging

- Giữ endpoint, HTTP method, status, tên field và hình dạng response khi refactor.
- Tái sử dụng `ApiResponse` và `PageResponse` theo contract của endpoint. Endpoint đang trả DTO/list trực tiếp không tự chuyển sang wrapper hàng loạt.
- Không trả entity JPA trực tiếp; không để lỗi lazy loading xuất hiện ở bước serialize response.
- Dùng exception nghiệp vụ và global handler hiện có. Không nuốt lỗi rồi trả kết quả thành công hoặc dữ liệu rỗng giả.
- Không đổi 200/400/401/403/404/409 để khớp một assertion cũ trước khi đọc request và contract thực tế.
- Dùng logger của project; log đủ ngữ cảnh để chẩn đoán, tránh `System.out.println` trong production.
- Không log token, password, secret, giấy tờ định danh hoặc payload nhạy cảm đầy đủ.
- `afterCommit` bảo đảm thứ tự sau commit, không tự làm tác vụ chạy nền. `@Async` chỉ có tác dụng khi cơ chế async thực sự được bật và lời gọi đi qua proxy.
- Nếu chuyển thông báo sang nền, đánh giá executor, dữ liệu truyền sang task, retry và độ bền gửi; không mặc định rằng chạy nền đồng nghĩa gửi thành công.

## 13. Áp dụng SOLID mà không tăng số lớp vô ích

| Nguyên lý | Cách áp dụng trong dự án |
|---|---|
| SRP | Controller xử lý HTTP, service xử lý nghiệp vụ, repository xử lý persistence, mapper chuyển dữ liệu |
| OCP | Dùng điểm mở rộng có nhu cầu thật, ví dụ integration provider; không tạo abstraction cho tình huống chưa có |
| LSP | Implementation giữ đúng contract của interface, cả kết quả và lỗi; không trả dữ liệu giả để che thiếu hỗ trợ |
| ISP | Interface theo trách nhiệm, tránh service/repository chứa nghiệp vụ không liên quan; không tách mỗi query thành một interface |
| DIP | Controller phụ thuộc service interface, service dùng abstraction tích hợp phù hợp; dùng constructor injection |

Ưu tiên code rõ ràng, thư viện có sẵn và helper dùng chung. Không tối ưu số dòng bằng biểu thức khó đọc, chuỗi ternary lồng nhau hoặc đổi tên thiếu ý nghĩa.

## 14. Kiểm thử và xác minh thay đổi

- Đọc luồng và caller trước khi sửa. Với bug/hiệu năng, lưu bằng chứng ban đầu và viết kiểm thử có thể phát hiện đúng vấn đề khi phù hợp.
- Với thay đổi logic, viết test cho hành vi, lỗi, side effect và điều kiện biên có ý nghĩa; không chỉ xác nhận cấu trúc implementation.
- Với tối ưu query, dùng integration test trên PostgreSQL, kiểm tra **cả dữ liệu trả về và số query**. Bao phủ thứ tự, phân trang, dữ liệu thiếu và quyền truy cập liên quan.
- Với tiền, credit, lock và callback, kiểm tra retry, rollback và đồng thời khi có liên quan; mock-only không chứng minh được hành vi database.
- Test phải gửi request hợp lệ nếu mục tiêu là kiểm tra ownership/quyền. Request lỗi validation có thể dừng trước kiểm tra nghiệp vụ.
- Fixture phải theo contract và chính sách đang dùng: đủ field bắt buộc, giá credit, số dư ví, role/permission và trạng thái cần thiết.
- Test migration riêng một phiên bản phải giới hạn đúng `target`; test latest phải kiểm tra nâng cấp và bảo toàn dữ liệu. Không sửa migration đã áp hoặc repair lịch sử chỉ để assertion đạt.
- Khi test lỗi, phân loại: regression do thay đổi mới, lỗi có sẵn, fixture cũ hoặc yêu cầu nghiệp vụ chưa thống nhất. Đọc response/stack trace; không chỉ đổi expected value.
- Chạy build/test phù hợp với phạm vi thay đổi. Import-only hoặc tài liệu không cần thêm test mô phỏng implementation; thay đổi query/nghiệp vụ cần kiểm chứng tương ứng.
- Không tuyên bố toàn bộ test đạt khi chỉ chạy một nhóm. Báo rõ lệnh/phạm vi đã chạy, số đạt/lỗi/skipped, lỗi mới và lỗi nền còn tồn tại.
- Đối chiếu diff cuối cùng; không ghi đè thay đổi có sẵn của người khác. Không tự commit, push hoặc triển khai khi chưa được yêu cầu.

Các điểm tham chiếu hiện có:

- [PackageArchitectureTest](../src/test/java/com/secondlife/secondlife/architecture/PackageArchitectureTest.java)
- [ReadQueryIntegrationTest](../src/test/java/com/secondlife/secondlife/integration/ReadQueryIntegrationTest.java)
- [MapStructConfiguration](../src/main/java/com/secondlife/secondlife/config/MapStructConfiguration.java)

## 15. Những quyết định nghiệp vụ chưa được coi là chuẩn mặc định

Các điểm sau đã được phát hiện khi rà soát nhưng chưa được chốt/sửa chỉ bằng tài liệu này:

1. **Kiểm định:** phân biệt kiểm định trước xuất bản bài giá trị cao và kiểm định do người mua yêu cầu; không dùng chung quy tắc `FAILED -> ACTIVE` cho mọi trường hợp.
2. **Quyền bán:** thống nhất hồ sơ `APPROVED` có bắt buộc hay việc cấp role `SELLER` thủ công là cơ chế phê duyệt hợp lệ; xác định rõ ngoại lệ ADMIN nếu có.
3. **STAFF đọc hồ sơ:** thống nhất quyền tra cứu giữa endpoint danh sách, chi tiết và tra theo user; không tự mở quyền duyệt.
4. **Catalog:** thống nhất cờ seed, runner, bộ tên/seed key và cách giữ dữ liệu admin đã sửa.
5. **Credit:** code hiện có luồng mua trừ ví/cấp credit trực tiếp cùng phần callback legacy. Khi sửa phải xác định luồng cần hỗ trợ; không đổi cơ chế thanh toán hoặc chính sách tính phí AI chỉ để khớp test cũ.

Khi gặp một điểm trên trong công việc, trình bày hành vi hiện tại, ảnh hưởng và phương án trước khi thay đổi quy tắc nghiệp vụ ngoài phạm vi đã được giao.

## 16. Checklist trước khi kết thúc công việc

- [ ] Đúng phạm vi yêu cầu; giữ hành vi cũ nếu nhiệm vụ là refactor.
- [ ] File/package đúng cấu trúc layer; service nghiệp vụ có interface và implementation.
- [ ] DTO API dùng record; entity không lộ ra request/response.
- [ ] Mapper concrete class dùng MapStruct; không có truy vấn DB trong mapper.
- [ ] Repository interface dùng JpaRepository; không thêm repository trùng entity vô lý.
- [ ] Import rõ ràng; không inline tên package khi có thể import.
- [ ] Giữ filter, thứ tự, null/fallback, phân trang, status và JSON contract.
- [ ] Không đổi lock, transaction hoặc nghiệp vụ tiền/credit trong tối ưu đọc thuần túy.
- [ ] Quyền, ownership và kiểm tra token không bị bỏ hoặc nhân bản.
- [ ] Đã chạy kiểm tra phù hợp và báo đúng kết quả; không che lỗi nền.
- [ ] Diff không có thay đổi ngoài phạm vi, secret hoặc file sinh tự động không cần thiết.

## 17. Mẫu yêu cầu dùng cho người viết code/AI

> Đọc `docs/BACKEND_CODING_STANDARDS.md` và các hướng dẫn áp dụng trong repository trước khi sửa. Giữ kiến trúc theo layer, Service + ServiceImpl, DTO request/response dạng record, mapper concrete class dùng MapStruct và repository interface extends JpaRepository. Dùng import tường minh, gom logic trùng ở đúng layer. Với refactor, giữ nguyên nghiệp vụ, contract API, phân quyền và side effect. Không tự đổi transaction, lock, thanh toán, credit, trạng thái kiểm định hoặc chính sách seller ngoài yêu cầu. Kiểm chứng bằng test phù hợp; báo rõ thay đổi, kết quả kiểm tra và vấn đề còn tồn tại.
