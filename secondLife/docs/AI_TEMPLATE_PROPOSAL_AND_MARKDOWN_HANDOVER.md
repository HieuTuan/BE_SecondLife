# Tài Liệu Bàn Giao Kỹ Thuật (BE & FE): Quy Trình AI Đề Xuất Template Duyệt Bởi Admin, Tối Ưu Tốc Độ Sinh Mô Tả AI & Hỗ Trợ Markdown (`.md`)

> **Ngày cập nhật:** 29/10/2026  
> **Nhánh Backend (`BE_SecondLife`):** `feature/ai-template-proposal-and-markdown-desc`  
> **Nhánh Frontend (`SEP490_FE_SecondLife`):** `feature/ai-template-proposal-and-markdown-desc`  
> **Đối tượng áp dụng:** Toàn bộ thành viên nhóm **Backend (BE)** và **Frontend (FE)**

---

## 1. Tổng Quan Các Vấn Đề Giải Quyết & Thay Đổi Chính

Đợt cập nhật này giải quyết trọn vẹn 5 nhóm yêu cầu nghiệp vụ và hiệu năng cốt lõi trên luồng Đăng tin thông minh bằng AI và Quản trị Danh mục:

1. **AI sinh Template câu hỏi tạm thời & Gửi Admin phê duyệt / chỉnh sửa thủ công trước khi xuống DB + Câu hỏi thông số kỹ thuật chuyên biệt:**
   - Khi người bán chọn mặt hàng chưa có bộ câu hỏi gợi ý trong DB, AI tự động sinh bộ câu hỏi tạm thời để người bán sử dụng ngay không bị gián đoạn, đồng thời đẩy bản đề xuất (`PENDING`) sang trang Quản trị để Admin xem xét, chỉnh sửa thủ công rồi mới duyệt lưu chính thức xuống DB.
   - Giữ đầy đủ **6 nhóm câu hỏi nền tảng** về tình trạng đồ cũ (ngoại hình/độ mới, thời gian mua & bảo hành, chức năng & lỗi, lịch sử sửa chữa, phụ kiện đi kèm, lý do bán) và **bắt buộc bổ sung 2–4 câu hỏi thông số kỹ thuật đặc thù theo từng mặt hàng** (ví dụ: TV hỏi kích thước bao nhiêu inch, độ phân giải 4K/Full HD, tấm nền, hệ điều hành; Điện thoại/Laptop hỏi RAM/ROM/CPU/Pin; Tủ lạnh/Máy giặt hỏi dung tích lít/khối lượng giặt/Inverter...).
2. **Xử lý triệt để lỗi load rất lâu (~22 phút) ở Bước 2 ("Tạo mô tả bằng AI"):**
   - Sửa lỗi cấu hình `.env` bị trôi về `http://127.0.0.1:11434` (thay vì gọi **Ollama Cloud Direct API** `https://ollama.com` qua `OLLAMA_API_KEY` như thiết kế), đồng thời loại bỏ cơ chế retry ngầm 10 lần với exponential backoff lên tới 300s; bổ sung tự động chuẩn hóa tên model Cloud (`gemma4:31b-cloud` -> `gemma4:31b`), fast-fail và cooldown 60s chuyển mượt sang Google Gemini khi một trong hai dịch vụ Cloud AI gặp sự cố hoặc chưa điền key.
   - Tách rời hoàn toàn các lệnh gọi AI bên ngoài khỏi `@Transactional` của Database để tránh giữ kết nối HikariCP (đặc biệt quan trọng trên Supabase Pooler), kèm cơ chế tự động hoàn (refund) lượt `AI_CHAT` credit nếu cuộc gọi AI thất bại.
3. **Hỗ trợ định dạng Markdown (`.md`) cho Ô Mô tả chi tiết sản phẩm:**
   - Bổ sung trình soạn thảo Markdown có thanh công cụ định dạng nhanh và 3 chế độ xem (`Soạn thảo (.md)` / `Xem hiển thị` / `Song song`).
   - Hiển thị Markdown an toàn (chống XSS 100%, không dùng `dangerouslySetInnerHTML`) đồng bộ trên Modal chi tiết sản phẩm, Modal so sánh sản phẩm và Không gian duyệt bài của Staff.
4. **Chuẩn hóa văn phong AI không dùng Emoji & Làm sạch ký tự thừa:**
   - Chỉnh sửa toàn bộ System Prompt sinh mô tả/viết lại mô tả tuyệt đối không sử dụng biểu tượng cảm xúc (emoji), kết hợp lớp hậu xử lý lọc sạch ký tự pictographic/emoji ở Backend.
   - Khắc phục lỗi hiển thị dư dấu ngoặc vuông `[...]` quanh câu hỏi gợi ý trên cả Backend và Frontend.
5. **Ổn định khởi động Spring Boot DevTools & Đồng bộ JPA Repository:**
   - Tinh chỉnh chu kỳ `poll-interval` / `quiet-period` của `spring-boot-devtools` tránh lỗi thiếu bean khi IDE đang biên dịch dở; chuẩn hóa tên hàm truy vấn `Category_Id` cho các Repository kiểm định trên Spring Boot 4.

---

## 2. Luồng Kiến Trúc Nghiệp Vụ Mới (AI Template Proposal Workflow)

```
[Seller chọn Danh mục + Mặt hàng ở Bước 1]
                 │
                 ▼
  ┌──────────────────────────────┐      Có sẵn
  │ Kiểm tra Template chính thức ├───────────────────────────────┐
  │ (category_question_templates)│                               │
  └──────────────┬───────────────┘                               │
                 │ Chưa có                                       │
                 ▼                                               │
  ┌──────────────────────────────┐      Đã có bản PENDING        │
  │ Kiểm tra Đề xuất đang chờ    ├───────────────────────┐       │
  │ (template_proposals PENDING) │                       │       │
  └──────────────┬───────────────┘                       │       │
                 │ Chưa có                               │       │
                 ▼                                       │       │
  ┌──────────────────────────────┐                       │       │
  │ AI sinh Template tạm thời    │                       │       │
  │ (Gemini -> Ollama -> Fallback│                       │       │
  │  chuyên biệt theo mặt hàng)  │                       │       │
  └──────────────┬───────────────┘                       │       │
                 │                                       │       │
     ┌───────────┴───────────┐                           │       │
     ▼                       ▼                           ▼       ▼
[Lưu Proposal PENDING]  [Gửi Notification cho Admin]  [Trả câu hỏi gợi ý ngay cho Seller ở Bước 2]
     │
     ▼
[Admin mở tab Danh mục & AI (/admin?subtab=catalog-ai)]
     ├── Chỉnh sửa câu hỏi / gợi ý nhanh (Visual Builder hoặc Raw Text)
     ├── Bấm "Duyệt & Lưu DB" ──► Upsert vào `category_question_templates` (ACTIVE)
     └── Bấm "Từ chối"        ──► Đánh dấu `REJECTED` kèm lý do (không ghi vào bảng chính)
```

---

## 3. Chi Tiết Thay Đổi & Hướng Xử Lý Bên Backend (`BE_SecondLife`)

### 3.1. Database Migration & Phân quyền Runtime (`V65`)
- **File mới:** `secondLife/src/main/resources/db/migration/V65__ai_template_proposals_and_unique_constraints.sql`
  - Tạo bảng `category_question_template_proposals` lưu trữ các template do AI sinh tự động chờ Admin duyệt (`status`: `PENDING`, `APPROVED`, `REJECTED`).
  - Bổ sung partial unique index `uq_pending_template_proposal_scope` đảm bảo mỗi cặp `(category_id, item_id)` chỉ có tối đa **1 bản đề xuất `PENDING`** tại một thời điểm (chống nhân bản đề xuất khi nhiều người bán cùng đăng một mặt hàng mới).
  - Bổ sung unique index cho bảng `category_question_templates` ở cả cấp danh mục (`item_id IS NULL`) và cấp mặt hàng (`item_id IS NOT NULL`).
- **File cập nhật:** `secondLife/scripts/database/provision_runtime_role.sql`
  - Thêm bảng `category_question_template_proposals` vào danh sách `app_tables` được cấp quyền cho DB runtime role.

### 3.2. Dịch vụ Sinh & Phê duyệt Template Câu hỏi (`CategoryTemplateService` & `AdminCatalogController`)
- **Các file chính:**
  - `entity/CategoryQuestionTemplateProposal.java`, `enums/TemplateProposalStatus.java`
  - `repository/CategoryQuestionTemplateProposalRepository.java`, `mapper/CategoryQuestionTemplateProposalMapper.java`
  - `dto/request/{ApproveTemplateProposalRequest,RejectTemplateProposalRequest,UpdateTemplateProposalRequest}.java`
  - `dto/response/{CategoryQuestionTemplateProposalResponse,ResolvedQuestionTemplateResponse}.java`
  - `service/CategoryTemplateService.java`, `service/impl/CategoryTemplateServiceImpl.java`
  - `service/impl/CatalogManagementServiceImpl.java`, `controller/AdminCatalogController.java`
- **Hướng xử lý kỹ thuật:**
  1. **Chiến lược ưu tiên Model AI:** Đổi thứ tự gọi AI khi sinh template sang **Google Gemini trước -> Ollama dự phòng -> Bộ câu hỏi kỹ thuật định sẵn theo từ khóa mặt hàng (Deterministic Spec Fallback)**, đảm bảo 100% trường hợp đều trả về câu hỏi chất lượng cao cho người dùng.
  2. **Bảo toàn 6 câu hỏi gốc + Bổ sung thông số kỹ thuật chuyên biệt:** Prompt AI và bộ chuẩn hóa hậu xử lý ép buộc giữ đủ 6 khía cạnh kiểm tra đồ cũ và bổ sung 2–4 câu hỏi thông số kỹ thuật riêng theo tên mặt hàng, đồng thời tự động bóc sạch ký tự ngoặc vuông `[...]` nếu LLM sinh thừa quanh nhãn câu hỏi.
  3. **Các API mới cho Admin (`/api/v1/admin/catalog`):**
     - `GET /templates/proposals?status=PENDING`: Lấy danh sách đề xuất template AI theo trạng thái.
     - `GET /templates/proposals/pending-count`: Đếm số lượng đề xuất `PENDING` hiển thị badge.
     - `PUT /templates/proposals/{proposalId}`: Admin chỉnh sửa nội dung template đề xuất trước khi duyệt.
     - `POST /templates/proposals/{proposalId}/approve`: Phê duyệt (nhận kèm nội dung chỉnh sửa cuối cùng nếu có) và lưu chính thức xuống `category_question_templates`.
     - `POST /templates/proposals/{proposalId}/reject`: Từ chối đề xuất kèm `reviewNote`.
     - `POST /templates/generate-preview`: Sinh bản xem trước câu hỏi bằng AI cho Admin chỉnh sửa trên UI mà **chưa ghi xuống DB**.

### 3.3. Tối ưu Tốc độ "Tạo Mô Tả Bằng AI" & Quản lý Transaction/Credit
- **Các file chính:**
  - `config/OllamaConfig.java`
  - `service/impl/AiChatServiceImpl.java`
  - `service/impl/PostServiceImpl.java`
  - `.env.example`
- **Nguyên nhân gốc trước khi sửa:**
  - Hệ thống được thiết kế dùng **Ollama Cloud Direct API** (`https://ollama.com` kèm header `Authorization: Bearer <OLLAMA_API_KEY>`, gọi trực tiếp qua HTTPS giống Gemini mà không cần cài Ollama dưới máy). Tuy nhiên sau đợt chuẩn hóa tên biến môi trường (`OLLAMA_BASE_URL` -> `SPRING_AI_OLLAMA_BASE_URL`), file `.env` bị trôi cấu hình về `SPRING_AI_OLLAMA_BASE_URL=http://127.0.0.1:11434`, `OLLAMA_API_KEY=` bị để trống, và tên model để `gemma4:31b-cloud` (hậu tố `-cloud` chỉ dành cho daemon local; trên API trực tiếp `https://ollama.com` tên model chuẩn là `gemma4:31b`). Khi kết hợp với `RetryTemplate` cũ (`maxAttempts=10`, `maxInterval=300000ms`), mỗi lần gọi bị treo retry `ConnectException` tổng cộng **~22 phút** trước khi sang Google Gemini.
  - Các hàm `finalizeChatAndDescription`, `regenerateChatAndDescription`, `suggestTitle`, `estimatePrice` trong `PostServiceImpl` đặt `@Transactional` bao trùm cả thời gian chờ mạng gọi AI, gây nghẽn HikariCP connection pool.
  - Tin nhắn hệ thống kiểm duyệt ảnh ở Bước 1 (~2.500 ký tự) bị nạp lại vào ngữ cảnh lịch sử chat ở Bước 2 làm phình prompt không cần thiết.
- **Hướng xử lý kỹ thuật:**
  1. **Chuẩn hóa kết nối Ollama Cloud API & Fast-fail (`OllamaConfig`):**
     - Cập nhật URL chuẩn `https://ollama.com` và tự động chuẩn hóa tên model (`normalizeCloudModelName`: tự cắt hậu tố `-cloud` / `:cloud` thành `gemma4:31b` khi gọi trực tiếp `ollama.com` để tương thích ngược nếu `.env` cũ vẫn ghi `gemma4:31b-cloud`).
     - Nếu `SPRING_AI_OLLAMA_BASE_URL=https://ollama.com` nhưng máy dev chưa điền `OLLAMA_API_KEY`, hệ thống ghi log cảnh báo rõ ràng lúc khởi động và chuyển thẳng sang Google Gemini (0ms chờ) thay vì bắn request lỗi `401`.
     - Bổ sung `.predicate(throwable -> !hasUnreachableHostCause(throwable))` vào `aiRetryTemplate` để gặp `ConnectException` / `UnknownHostException` là ngắt retry ngay tức thì (~2ms).
  2. **Cooldown 60 giây & Thu gọn Context (`AiChatServiceImpl`):** Ghi nhận thời điểm Ollama lỗi kết nối để tự động bỏ qua Ollama trong 60 giây kế tiếp (`OLLAMA_FAILURE_COOLDOWN_MS`); thêm hàm `sanitizePersistedMessageForContext(...)` cắt bỏ khối hướng dẫn kiểm tra ảnh Bước 1 khỏi lịch sử hội thoại trước khi gửi lên Gemini.
  3. **Tách Transaction ngắn & Hoàn Credit tự động (`PostServiceImpl`):** Gỡ `@Transactional` trên các hàm gọi AI, chia thành Pha 1 (đọc dữ liệu & trừ credit trong `TransactionTemplate` ngắn) -> Pha 2 (gọi AI ngoài transaction DB) -> Pha 3 (lưu kết quả trong `TransactionTemplate` ngắn; nếu AI lỗi tự động gọi `creditService.refundCredit(...)`).

### 3.4. Loại bỏ Emoji trong Mô tả AI & Ổn định Khởi động
- **Loại bỏ Emoji (`AiChatServiceImpl`, `PostServiceImpl`):**
  - Cập nhật toàn bộ System Prompt sinh mô tả (`finalizeChatAndDescription`, `regenerateChatAndDescription`, `AiChatServiceImpl`) cấm tuyệt đối việc chèn emoji/icon trang trí, yêu cầu trình bày bằng Markdown chuẩn (`**Tiêu đề mục:**` và danh sách `- `).
  - Bổ sung hàm hậu xử lý `stripEmojis(...)` dùng biểu thức chính quy Unicode (`\p{Extended_Pictographic}`, Miscellaneous Symbols, Dingbats, Variation Selectors) quét sạch mọi icon còn sót trước khi lưu DB và trả về FE.
- **Chống race-condition DevTools & Chuẩn hóa Repository (`application.properties`, `Inspection*`):**
  - Thêm `spring.devtools.restart.poll-interval=${DEVTOOLS_POLL_INTERVAL:3s}` và `spring.devtools.restart.quiet-period=${DEVTOOLS_QUIET_PERIOD:1500ms}` để `spring-boot-devtools` đợi trình biên dịch ghi xong `.class` mới khởi động lại context.
  - Bổ sung `@Transient` cho `InspectionFeeProposal.getCategoryId()` và cập nhật `findByCategory_Id...` tại `InspectionChecklistTemplateRepository`, `InspectionFeeProposalRepository`, `InspectionPricingServiceImpl`.

---

## 4. Chi Tiết Thay Đổi & Hướng Xử Lý Bên Frontend (`SEP490_FE_SecondLife`)

### 4.1. Giao diện Admin Duyệt & Chỉnh sửa Đề xuất Template AI
- **Các file chính:**
  - `src/services/adminCatalogService.ts`
  - `src/components/admin/CatalogAiTab.tsx`
  - `src/utils/questionParser.ts`
- **Hướng xử lý kỹ thuật:**
  - Mở rộng `adminCatalogService.ts` kết nối đầy đủ các API quản lý đề xuất (`getProposals`, `getPendingProposalCount`, `updateProposal`, `approveProposal`, `rejectProposal`, `generatePreview`).
  - Tại `CatalogAiTab.tsx`:
    - Hiển thị danh sách **"Đề xuất Template từ AI chờ duyệt"** kèm badge đếm số lượng `PENDING`, cho phép lọc theo trạng thái (`Chờ duyệt` / `Đã duyệt` / `Đã từ chối`).
    - Tích hợp **Visual Question Builder** cho phép Admin thêm/xóa/sắp xếp câu hỏi, chỉnh sửa nhanh các gợi ý mẫu (`VD: ...`), cảnh báo nếu bộ câu hỏi chưa có câu hỏi thông số kỹ thuật đặc thù, lưu nháp chỉnh sửa hoặc bấm **Duyệt & Lưu DB** / **Từ chối**.
    - Đổi nút tạo câu hỏi bằng AI trên form Template sang gọi `generatePreview` để Admin kiểm tra và tinh chỉnh trước khi bấm Lưu chính thức.
  - Tại `questionParser.ts`: Bóc sạch dấu ngoặc vuông `[...]` bao quanh nhãn câu hỏi khi parse template (hỗ trợ làm sạch cả dữ liệu nháp cũ) và bổ sung hàm nhận diện nhóm câu hỏi thông số kỹ thuật.

### 4.2. Trình Soạn Thảo & Bộ Render Markdown (`.md`) An Toàn
- **Các file chính:**
  - `src/components/common/MarkdownDescription.tsx` *(File mới)*
  - `src/pages/CreateListingView.tsx`
  - `src/components/modals/ListingDetailModal.tsx`
  - `src/components/staff/ListingComparisonModal.tsx`
  - `src/pages/StaffWorkspaceView.tsx`
- **Hướng xử lý kỹ thuật:**
  - Không cài thêm thư viện ngoài nặng nề dễ xung đột peer-dependencies trên React 19; xây dựng module nội bộ `MarkdownDescription.tsx` gồm 2 thành phần:
    1. `MarkdownDescriptionEditor`: Hỗ trợ thanh công cụ chèn nhanh (`**In đậm**`, `*In nghiêng*`, `### Tiêu đề mục`, danh sách `- `, danh sách đánh số `1. `, trích dẫn `> `) và 3 tab chuyển đổi mượt mà: **Soạn thảo (.md)**, **Xem hiển thị**, **Song song**.
    2. `MarkdownContent`: Bộ parser chuyển cú pháp Markdown (headings, bold, italic, inline code, bullet/numbered lists, blockquotes, horizontal rules, đoạn văn) trực tiếp sang cây phần tử React JSX thuần — **tuyệt đối không sử dụng `dangerouslySetInnerHTML`**, đảm bảo an toàn XSS 100% và tương thích ngược hoàn toàn với các bài đăng văn bản thuần trước đây.
  - Bổ sung timeout bảo vệ 55 giây (`AbortController`) trên luồng gọi API "Tạo mô tả bằng AI" / "Viết lại bằng AI" ở `CreateListingView.tsx` để giải phóng trạng thái loading an toàn nếu kết nối mạng gián đoạn.

---

## 5. Hướng Dẫn Cập Nhật Bắt Buộc Cho Thành Viên Nhóm BE & FE

### 5.1. Dành cho nhóm Backend (BE)
1. **Pull nhánh mới về máy:**
   ```bash
   cd BE_SecondLife
   git fetch origin
   git checkout feature/ai-template-proposal-and-markdown-desc
   git pull origin feature/ai-template-proposal-and-markdown-desc
   ```
2. **QUAN TRỌNG — Cập nhật cấu hình Ollama Cloud API & các biến timeout/retry AI trong file `.env` local:**
   Do file `.env` không đẩy lên Git (chứa API keys cá nhân), các bạn BE cần mở `BE_SecondLife/secondLife/.env` trên máy mình và kiểm tra/cập nhật lại cụm cấu hình **Ollama Cloud API** + **Timeout/Retry** (đã cập nhật mẫu trong `.env.example`), tránh trường hợp `.env` cũ bị trôi về `http://127.0.0.1:11434`:
   ```properties
   # AI Ollama Cloud Direct API (gọi trực tiếp qua HTTPS API như Gemini, không cần cài Ollama local)
   SPRING_AI_OLLAMA_BASE_URL=https://ollama.com
   SPRING_AI_OLLAMA_CHAT_MODEL=gemma4:31b
   OLLAMA_API_KEY=<điền_API_Key_Ollama_Cloud_của_bạn_từ_ollama.com/settings/keys>

   # AI Google GenAI (cập nhật các dòng timeout & retry bên dưới, giữ nguyên GOOGLE_API_KEY của bạn)
   GOOGLE_AI_CONNECT_TIMEOUT_MS=10000
   GOOGLE_AI_READ_TIMEOUT_MS=40000
   GOOGLE_AI_WRITE_TIMEOUT_MS=20000
   GOOGLE_AI_TIMEOUT_MS=45000
   GOOGLE_AI_RETRY_ATTEMPTS=2
   GOOGLE_AI_RETRY_INITIAL_DELAY_SECONDS=1.0
   GOOGLE_AI_RETRY_MAX_DELAY_SECONDS=5.0
   GOOGLE_AI_RETRY_MULTIPLIER=2
   GOOGLE_AI_RETRY_JITTER=0.2

   # AI timeout và retry chung cho Cloud API
   AI_CONNECT_TIMEOUT_MS=10000
   AI_STREAMING_CONNECT_TIMEOUT_MS=10000
   AI_READ_TIMEOUT_MS=45000
   AI_MAX_RETRIES=1
   AI_RETRY_INITIAL_INTERVAL_MS=500
   AI_RETRY_MULTIPLIER=2
   AI_RETRY_MAX_INTERVAL_MS=2000
   ```
3. **Đồng bộ IDE & Khởi động ứng dụng:**
   - Trong IntelliJ IDEA: Bấm **Stop** tiến trình cũ (nếu đang chạy), nhấn **`Ctrl + Alt + Y`** (*Reload All from Disk*), sau đó bấm **Run** lại `SecondLifeApplication`.
   - Flyway sẽ tự động chạy bản nâng cấp `V65__ai_template_proposals_and_unique_constraints.sql` khi ứng dụng khởi động (nếu DB dùng chung đã được migrate trước đó thì Flyway sẽ tự xác nhận hợp lệ).

### 5.2. Dành cho nhóm Frontend (FE)
1. **Pull nhánh mới về máy:**
   ```bash
   cd FE_SecondLife/SEP490_FE_SecondLife
   git fetch origin
   git checkout feature/ai-template-proposal-and-markdown-desc
   git pull origin feature/ai-template-proposal-and-markdown-desc
   ```
2. **Chạy môi trường phát triển:**
   - Không phát sinh thêm package mới trong `package.json` (không bắt buộc chạy lại `npm install`).
   - Chạy `npm run dev` và kết nối với Backend nhánh `feature/ai-template-proposal-and-markdown-desc` để kiểm thử đầy đủ:
     - **Vai trò Seller:** Tạo tin đăng mới với mặt hàng chưa có template -> Kiểm tra câu hỏi thông số kỹ thuật -> Bấm "Tạo mô tả bằng AI" (tốc độ phản hồi nhanh, không có emoji, hiển thị đẹp ở chế độ Markdown `.md`).
     - **Vai trò Admin:** Truy cập **Quản trị -> Danh mục & AI** (`/admin?subtab=catalog-ai`) -> Kiểm tra mục đề xuất từ AI chờ duyệt -> Chỉnh sửa thủ công câu hỏi/gợi ý -> Duyệt lưu xuống DB hoặc Từ chối.
     - **Vai trò Buyer / Staff:** Mở chi tiết tin đăng, so sánh tin đăng và trang duyệt tin của Staff để kiểm tra hiển thị định dạng Markdown.

