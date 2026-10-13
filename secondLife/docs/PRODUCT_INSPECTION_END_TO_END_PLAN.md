# KẾ HOẠCH TRIỂN KHAI TOÀN DIỆN: LUỒNG KIỂM ĐỊNH SẢN PHẨM (PRODUCT INSPECTION SYSTEM)
**Dự án**: SecondLife Marketplace (Capstone Project - Đồ án tốt nghiệp FPT)  
**Tác giả**: DinhPhat1401 & Antigravity  
**Ngày cập nhật**: 13/10/2026  
**Trạng thái**: Bản kế hoạch đặc tả kỹ thuật và kiến trúc chính thức  

---

## MỤC LỤC
1. [TỔNG QUAN & ĐẶC TẢ KIẾN TRÚC](#1-tổng-quan--đặc-tả-kiến-trúc)
2. [MA TRẬN VAI TRÒ & PHÂN QUYỀN (RBAC / PBAC)](#2-ma-trận-vai-trò--phân-quyền-rbac--pbac)
3. [QUY TẮC NGHIỆP VỤ & CÔNG THỨC TÍNH TOÁN CHI TIẾT](#3-quy-tắc-nghiệp-vụ--công-thức-tính-toán-chi-tiết)
4. [LUỒNG TRẠNG THÁI VÀ QUY TRÌNH THỰC THI (STATE MACHINE)](#4-luồng-trạng-thái-và-quy-trình-thực-thi-state-machine)
5. [THIẾT KẾ CƠ SỞ DỮ LIỆU (DATABASE SCHEMA MIGRATION)](#5-thiết-kế-cơ-sở-dữ-liệu-database-schema-migration)
6. [ĐẶC TẢ HỆ THỐNG RESTFUL APIS](#6-đặc-tả-hệ-thống-restful-apis)
7. [KIẾN TRÚC GIAO DIỆN & TÍCH HỢP FRONTEND](#7-kiến-trúc-giao-diện--tích-hợp-frontend)
8. [LỘ TRÌNH TRIỂN KHAI TỪNG BƯỚC (STEP-BY-STEP ROADMAP)](#8-lộ-trình-triển-khai-từng-bước-step-by-step-roadmap)

---

## 1. TỔNG QUAN & ĐẶC TẢ KIẾN TRÚC

### 1.1. Bối cảnh đồ án
Hệ thống sàn giao dịch đồ cũ SecondLife tích hợp giải pháp kiểm định độ uy tín và chất lượng của sản phẩm thông qua **Đơn vị/Trung tâm kiểm định thứ 3 (Third-party Inspection Center)**. Vì đây là đồ án tốt nghiệp, toàn bộ các mắt xích của trung tâm kiểm định được xây dựng độc lập bên trong hệ thống với đầy đủ quy trình nghiệp vụ chuyên nghiệp (Portal quản lý riêng, phân công KTV, chấm checklist điện tử, quay video đối chiếu, tính cước di chuyển bậc thang và luồng tranh chấp ba bên).

### 1.2. Quyết định kiến trúc về Role (RBAC)
**Quyết định**: **KHÔNG bổ sung thêm role "Staff trung tâm kiểm định"**. Hệ thống duy trì 2 role chuẩn mực cho bên kiểm định:
1. **`INSPECTION_CENTER` (Quản lý / Điều phối viên trung tâm kiểm định)**:
   - Đảm nhiệm nghiệp vụ quản trị và điều phối (Dispatching): Tiếp nhận đơn, phân công Kỹ thuật viên, quản lý tài khoản KTV, quản lý biểu mẫu checklist theo danh mục, đề xuất điều chỉnh bảng giá bậc thang.
2. **`INSPECTOR` (Kỹ thuật viên kiểm định)**:
   - Đảm nhiệm nghiệp vụ kỹ thuật thực địa/lab: Xem lịch hẹn, kiểm tra thiết bị theo checklist, quay video bằng chứng, công bố biên bản kết quả Đạt (`PASSED`) hoặc Không đạt (`FAILED`).

**Lợi ích kiến trúc**:
- Ngăn chặn **Role Explosion** (quá nhiều vai trò làm rối rắm JWT Token, RBAC filter và phân luồng UI).
- Phân định rành mạch trách nhiệm: **Quản lý/Điều phối (`INSPECTION_CENTER`)** tách biệt với **Kỹ thuật viên thực thi (`INSPECTOR`)**.
- Các quyền nội bộ được kiểm soát thông qua Permission Code (PBAC) như `INSPECTION_ORDER_ASSIGN`, `INSPECTION_STAFF_MANAGE`, `INSPECTION_CHECKLIST_MANAGE`, `INSPECTION_FEE_MANAGE`.

---

## 2. MA TRẬN VAI TRÒ & PHÂN QUYỀN (RBAC / PBAC)

| Vai trò (Role) | Mã quyền (Permissions) | Trách nhiệm chính trong luồng kiểm định |
| :--- | :--- | :--- |
| **BUYER** | `ORDER_CREATE`, `INSPECTION_REQUEST`, `ESCROW_PAY` | Yêu cầu mua kèm kiểm định; thương lượng phương thức (`AT_HOME` / `AT_CENTER`) và tỷ lệ chia phí; nộp phí kiểm định 6h; xác nhận mua hàng và thanh toán tiền hàng chặng cuối sau khi kiểm định Pass; khiếu nại nếu hàng lỗi. |
| **SELLER** | `INSPECTION_RESPOND`, `APPOINTMENT_MANAGE`, `SHIPMENT_DISPATCH` | Đồng ý / từ chối kiểm định; thương lượng phương thức và chia phí; nộp phí kiểm định 6h; phối hợp hẹn lịch KTV đến nhà (nếu Tại nhà) hoặc đóng gói gửi hàng đến trung tâm (nếu Tại trung tâm); nhận lại hàng nếu Fail. |
| **INSPECTION_CENTER** | `INSPECTION_ORDER_READ_ANY`, `INSPECTION_ORDER_ASSIGN`, `INSPECTION_STAFF_MANAGE`, `INSPECTION_CHECKLIST_MANAGE`, `INSPECTION_FEE_PROPOSE` | Tiếp nhận đơn kiểm định; phân công Inspector; quản lý tài khoản Inspector (thêm/sửa/khóa); CRUD bảng tiêu chí Checklist theo danh mục sản phẩm; đề xuất bảng giá kiểm định & bậc thang di chuyển. |
| **INSPECTOR** | `INSPECTION_ORDER_READ_SELF`, `INSPECTION_REPORT_SUBMIT` | Xem danh sách đơn được phân công; hẹn lịch với người bán; thao tác chấm checklist; upload video bằng chứng (khui hộp + quá trình test máy); submit kết luận kiểm định. |
| **ADMIN (Sàn)** | `INSPECTION_CENTER_ACCOUNT_MANAGE`, `INSPECTION_FEE_APPROVE`, `INSPECTION_FEE_ADJUST` | Quản lý tài khoản Admin trung tâm kiểm định; phê duyệt hoặc từ chối đề xuất bảng giá / bậc thang di chuyển; chủ động điều chỉnh mức phí khi cần thiết. |
| **STAFF (Sàn)** | `DISPUTE_ARBITRATE`, `ESCROW_RELEASE`, `ESCROW_REFUND` | Trọng tài giải quyết tranh chấp (Unhappy case): Đối chiếu Video đóng gói (Seller) vs Video kiểm định (Center) vs Video mở hộp (Buyer) để phán quyết hoàn tiền hay giải ngân. |

---

## 3. QUY TẮC NGHIỆP VỤ & CÔNG THỨC TÍNH TOÁN CHI TIẾT

### 3.1. Cảnh báo pháp lý & kỹ thuật: "Tháo máy kiểm định - Mất trạng thái zin"
- Việc kiểm định thiết bị điện tử, gia dụng bắt buộc phải tháo mở ốc máy để đánh giá bo mạch, tụ điện, pin, linh kiện bên trong có bị thay thế/sửa chữa hay không.
- **Quy tắc xác nhận 2 chiều (Dual-consent)**:
  * **Buyer Consent**: Người mua xác nhận hiểu rằng sản phẩm sẽ mất tem niêm phong xuất xưởng của nhà sản xuất (mất trạng thái "zin" gốc) nhưng sẽ được dán Tem bảo đảm & Biên bản kiểm định độc quyền SecondLife.
  * **Seller Consent**: Người bán xác nhận đồng ý cho kỹ thuật viên mở máy. Nếu kết quả kiểm định Đạt (`PASSED`) mà Người mua tự ý bỏ cọc/không thanh toán nốt tiền hàng, Người bán không được khiếu nại sàn về việc mất trạng thái zin.

### 3.2. Công thức tính chi phí kiểm định 2 gói dịch vụ

#### Gói 1: Kiểm định tại trung tâm (`AT_CENTER`)
Áp dụng cho các sản phẩm nhỏ gọn, đồ điện tử xách tay, điện thoại, máy ảnh:
$$\text{Tổng phí AT\_CENTER} = \text{Phí kiểm định cơ sở} + \text{Phí vận chuyển chiều đi (Seller } \to \text{ Center)} + \text{Phí vận chuyển chiều về dự phòng (Center } \to \text{ Seller)}$$
- *Khoản phí chiều về dự phòng (Return reserve)*: Thu trước từ đầu. Nếu sản phẩm kiểm định Bị trượt (`FAILED`) hoặc Người mua từ chối mua sau khi kiểm định, Trung tâm sẽ sử dụng khoản phí này để chuyển hoàn hàng về cho Người bán ngay lập tức mà không cần thu thêm tiền của ai.
- Nếu Người mua mua hàng thành công: Khoản phí vận chuyển giao đến nhà người mua sẽ được cấn trừ/bù trừ với khoản phí chiều về này (thừa hoàn ví, thiếu nộp thêm).

#### Gói 2: Kiểm định tại nhà (`AT_HOME`) - Giải pháp đồ cồng kềnh
Áp dụng cho đồ nội thất, tủ lạnh, máy giặt, dàn âm thanh, giường tủ... **Không bao giờ bị ẩn dù khoảng cách xa**.
$$\text{Tổng phí AT\_HOME} = \text{Phí kiểm định cơ sở} + \text{Phí di chuyển 2 chiều của KTV}$$
Trong đó, **Phí di chuyển 2 chiều** được tính bằng:
$$\text{Phí di chuyển 2 chiều} = \text{Phí di chuyển 1 chiều} \times 2$$

#### Bảng đơn giá lũy tiến bậc thang di chuyển (Do Admin trung tâm đề xuất & Admin sàn duyệt):

| Bậc (Tier) | Quãng đường | Đơn giá / km | Ý nghĩa & Logic tính toán |
| :---: | :---: | :---: | :--- |
| **Bậc 1** | $0 \to 5 \text{ km}$ đầu | **2.000 đ/km** | Đoạn cự ly gần: $d_1 = \min(D, 5) \times 2.000$ |
| **Bậc 2** | Trên $5 \to 10 \text{ km}$ | **2.500 đ/km** | Đoạn $5 \to 10\text{km}$: $d_2 = \min(\max(D - 5, 0), 5) \times 2.500$ |
| **Bậc 3** | Trên $10 \to 15 \text{ km}$ | **3.000 đ/km** | Đoạn $10 \to 15\text{km}$: $d_3 = \min(\max(D - 10, 0), 5) \times 3.000$ |
| **Bậc 4** | Trên $15 \to 20 \text{ km}$ | **3.500 đ/km** | Đoạn $15 \to 20\text{km}$: $d_4 = \min(\max(D - 15, 0), 5) \times 3.500$ |
| **Bậc 5** | Trên $20 \to 30 \text{ km}$ | **4.000 đ/km** | Đoạn $20 \to 30\text{km}$: $d_5 = \min(\max(D - 20, 0), 10) \times 4.000$ |
| **Bậc 6** | Trên $30 \text{ km}$ trở lên (Mở) | **4.500 đ/km** | Cự ly dài liên quận/ngoại ô: $d_6 = \max(D - 30, 0) \times 4.500$ |

- **Tính khoảng cách $D$ (km)**: Dựa trên kinh độ/vĩ độ của Người bán và Trung tâm kiểm định qua thuật toán Haversine $\times 1.25$ (hệ số uốn lượn giao thông đường bộ).
- **Ví dụ tính toán $D = 35 \text{ km}$**:
  * 1 chiều: $(5 \times 2.000) + (5 \times 2.500) + (5 \times 3.000) + (5 \times 3.500) + (10 \times 4.000) + (5 \times 4.500) = 10.000 + 12.500 + 15.000 + 17.500 + 40.000 + 22.500 = 117.500 \text{ đ}$.
  * 2 chiều: $117.500 \times 2 = \mathbf{235.000 \text{ đ}}$.
  * Phí cơ sở: $100.000 \text{ đ} \implies \text{Tổng chi phí kiểm định tại nhà} = \mathbf{335.000 \text{ đ}}$.

### 3.3. Phương án chia sẻ chi phí (Fee Split Negotiation)
Buyer và Seller thỏa thuận 1 trong 3 phương án:
1. `BUYER_PAYS`: Người mua thanh toán 100% chi phí kiểm định.
2. `SELLER_PAYS`: Người bán thanh toán 100% chi phí kiểm định.
3. `SPLIT_50_50`: Mỗi bên chịu 50% chi phí kiểm định ($\text{Phí} / 2$).

### 3.4. Ràng buộc Thanh toán & Ký quỹ 6 giờ (6-Hour Payment Escrow Window)
- Khi thỏa thuận được chấp thuận, hệ thống thiết lập `payment_deadline = Instant.now() + 6 HOURS`.
- Mỗi bên phải thanh toán phần phí của mình từ Ví SecondLife vào tài khoản Escrow của hệ thống.
- **Quy tắc hết hạn (Auto-Refund on Timeout)**:
  * Nếu sau 6 giờ mà 1 hoặc cả 2 bên chưa nộp đủ tiền:
    + Đơn tự động hủy chuyển sang `PAYMENT_EXPIRED`.
    + Hệ thống tự động hoàn trả 100% tiền đã nộp cho bên đã thanh toán trước về ví SecondLife.
    + Không phát sinh bất kỳ khoản phạt nào.

---

## 4. LUỒNG TRẠNG THÁI VÀ QUY TRÌNH THỰC THI (STATE MACHINE)

### 4.1. Sơ đồ trạng thái Đơn Kiểm định (Inspection Order Life Cycle)

```mermaid
graph TD
    A[Yêu cầu kiểm định mới] --> B[Chờ Buyer & Seller ký quỹ phí - 6h]
    B -->|Quá 6h không đủ tiền| C[HỦY & Tự động hoàn tiền đã nộp]
    B -->|Đã nộp đủ cả 2 bên| D[CONFIRMED_PENDING_DISPATCH]
    
    D -->|Center Admin phân công KTV| E[DISPATCHED / ASSIGNED]
    
    E -->|Gói Tại Nhà: KTV & Seller chốt lịch| F[SCHEDULED]
    E -->|Gói Tại Trung Tâm: Seller gửi hàng| G[SHIPPING_TO_CENTER]
    
    G -->|Trung tâm nhận hàng & khui kiện| H[INSPECTING]
    F -->|KTV đến nhà Seller| H
    
    H -->|Kiểm định KHÔNG ĐẠT| I[FAILED]
    H -->|Kiểm định ĐẠT CHUẨN| J[PASSED]
    
    I -->|Gói Tại Nhà| K[Đơn kết thúc - Hàng giữ tại nhà Seller]
    I -->|Gói Tại Trung Tâm| L[Kích hoạt đơn ship hoàn trả Seller từ quỹ dự phòng]
    
    J --> M[Chờ Buyer thanh toán tiền hàng & ship cuối - 6h]
    M -->|Quá 6h Buyer không trả tiền hàng| N[Hủy đơn mua - Hoàn hàng về Seller]
    M -->|Buyer thanh toán đủ| O[DELIVERING_TO_BUYER]
    
    O -->|Giao thành công| P[Xác nhận nhận hàng / Quá 24h|
    P --> Q[COMPLETED - Giải ngân tiền hàng cho Seller]
    O -->|Buyer khiếu nại trong 24h| R[DISPUTED - Staff sàn vào phân xử 3 bên]
```

### 4.2. Luồng chi tiết: Gói Tại Nhà (`AT_HOME`)
1. **Lên lịch hẹn**: Inspector và Seller trao đổi và chốt mốc thời gian trên hệ thống. Buyer có thể theo dõi thời gian này.
2. **Kiểm tra thực địa**: Inspector đến địa chỉ người bán đúng giờ, bật camera quay video toàn cảnh và tiến hành kiểm tra từng mục checklist.
3. **Công bố kết quả**: Inspector hoàn thiện checklist, đính kèm video và bấm Submit.
4. **Quyết toán**: Nếu Passed, Buyer có 6 giờ để thanh toán tiền hàng và tiền ship từ nhà người bán đến nhà người mua. Người bán sau đó bàn giao hàng cho shipper.

### 4.3. Luồng chi tiết: Gói Tại Trung Tâm (`AT_CENTER`)
1. **Gửi hàng đến trung tâm**: Seller bàn giao hàng cho Lalamove/đơn vị vận chuyển đến địa chỉ Trung tâm. Cả 3 bên (Buyer, Seller, Center) theo dõi tracking `SELLER_TO_CENTER`.
2. **Khui kiện & Kiểm định**: Khi hàng tới Hub, Inspector quay video khui kiện (Unboxing video) kiểm tra ngoại quan kiện hàng, sau đó đưa lên bàn kỹ thuật test theo checklist và quay video kiểm định.
3. **Công bố kết quả**: Nộp báo cáo kết quả kèm 2 video (Unboxing + Inspection).
4. **Quyết toán**:
   - Nếu Passed: Buyer xác nhận mua và thanh toán tiền hàng + bù trừ cước ship từ Trung tâm về nhà Buyer trong vòng 6 giờ.
   - Nếu Failed hoặc Buyer quá hạn 6 giờ: Hệ thống kích hoạt đơn ship từ Trung tâm hoàn trả lại cho Seller (sử dụng cước chiều về dự phòng đã nộp ban đầu).

### 4.4. Cơ chế Phân xử Tranh chấp 3 bên (Dispute Arbitration)
Khi Buyer nhận hàng và khiếu nại (Unhappy Case), Staff sàn SecondLife có thẩm quyền đối chiếu **3 nguồn video độc lập**:
1. `Seller Packaging Video`: Video lúc người bán đóng gói gửi đi.
2. `Center Inspection Video`: Video khui kiện và video test của Kỹ thuật viên tại Trung tâm.
3. `Buyer Unboxing Video`: Video người mua quay lúc nhận hàng từ shipper.
- Nếu lỗi do người bán gửi sai hàng $\to$ Phạt người bán, hoàn tiền người mua.
- Nếu lỗi do trung tâm kiểm định làm hư hại hoặc đánh giá sai $\to$ Trung tâm đền bù.
- Nếu lỗi do bên vận chuyển làm vỡ $\to$ Truy cứu đơn vị vận chuyển theo bảo hiểm đơn hàng.

---

## 5. THIẾT KẾ CƠ SỞ DỮ LIỆU (DATABASE SCHEMA MIGRATION)

### File migration: `V40__comprehensive_product_inspection_system.sql`

```sql
-- 1. Bảng bậc thang tính phí di chuyển KTV kiểm định tại nhà
CREATE TABLE IF NOT EXISTS inspection_travel_tiers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tier_number INT NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    from_km NUMERIC(8,2) NOT NULL,
    to_km NUMERIC(8,2), -- NULL nghĩa là vô cực (trên X km)
    price_per_km NUMERIC(18,2) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, INACTIVE
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Seed sẵn 6 bậc chuẩn do user đề xuất
INSERT INTO inspection_travel_tiers (tier_number, name, from_km, to_km, price_per_km, status) VALUES
(1, '0-5 km đầu', 0.0, 5.0, 2000.00, 'ACTIVE'),
(2, 'Trên 5-10 km', 5.0, 10.0, 2500.00, 'ACTIVE'),
(3, 'Trên 10-15 km', 10.0, 15.0, 3000.00, 'ACTIVE'),
(4, 'Trên 15-20 km', 15.0, 20.0, 3500.00, 'ACTIVE'),
(5, 'Trên 20-30 km', 20.0, 30.0, 4000.00, 'ACTIVE'),
(6, 'Trên 30 km', 30.0, NULL, 4500.00, 'ACTIVE')
ON CONFLICT (tier_number) DO NOTHING;

-- 2. Bảng đề xuất biểu phí & duyệt biểu phí giữa Trung tâm và Admin Sàn
CREATE TABLE IF NOT EXISTS inspection_fee_proposals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID REFERENCES categories(id),
    base_inspection_fee NUMERIC(18,2) NOT NULL,
    tiers_json TEXT NOT NULL, -- Snapshot JSON các bậc di chuyển đề xuất
    proposed_by UUID NOT NULL REFERENCES users(id),
    reviewed_by UUID REFERENCES users(id),
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_APPROVAL', -- PENDING_APPROVAL, APPROVED, REJECTED
    reject_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    reviewed_at TIMESTAMP WITH TIME ZONE
);

-- 3. Bảng Template Checklist Kiểm Định
CREATE TABLE IF NOT EXISTS inspection_checklist_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_id UUID REFERENCES categories(id),
    title VARCHAR(200) NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 4. Bảng Tiêu chí chi tiết trong Checklist
CREATE TABLE IF NOT EXISTS inspection_checklist_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES inspection_checklist_templates(id) ON DELETE CASCADE,
    item_title VARCHAR(255) NOT NULL,
    criteria_description TEXT,
    is_mandatory BOOLEAN DEFAULT TRUE,
    order_index INT DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- 5. Mở rộng bảng inspection_orders
ALTER TABLE inspection_orders 
    ADD COLUMN IF NOT EXISTS order_type VARCHAR(30) DEFAULT 'AT_CENTER', -- AT_HOME, AT_CENTER
    ADD COLUMN IF NOT EXISTS fee_payer VARCHAR(30) DEFAULT 'BUYER_PAYS', -- BUYER_PAYS, SELLER_PAYS, SPLIT_50_50
    ADD COLUMN IF NOT EXISTS buyer_fee_share NUMERIC(18,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS seller_fee_share NUMERIC(18,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS travel_distance_km NUMERIC(8,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS travel_fee NUMERIC(18,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS return_shipping_reserve_fee NUMERIC(18,2) DEFAULT 0,
    ADD COLUMN IF NOT EXISTS payment_deadline TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS buyer_paid_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS seller_paid_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS appointment_scheduled_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS checklist_result_json TEXT,
    ADD COLUMN IF NOT EXISTS video_proof_url TEXT,
    ADD COLUMN IF NOT EXISTS unboxing_video_url TEXT,
    ADD COLUMN IF NOT EXISTS final_payment_deadline TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS final_payment_paid_at TIMESTAMP WITH TIME ZONE;

-- 6. Cập nhật quyền PBAC
INSERT INTO permissions (id, code, name, description) VALUES
    (gen_random_uuid(), 'INSPECTION_CHECKLIST_MANAGE', 'Manage Inspection Checklist', 'Create, update and delete inspection checklists'),
    (gen_random_uuid(), 'INSPECTION_FEE_MANAGE', 'Manage Inspection Fees', 'Propose or review inspection fee schedules')
ON CONFLICT (code) DO NOTHING;

-- Gán quyền cho role
INSERT INTO role_permissions (id, role_id, permission_id)
SELECT gen_random_uuid(), r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE (r.code = 'INSPECTION_CENTER' AND p.code IN ('INSPECTION_CHECKLIST_MANAGE', 'INSPECTION_FEE_MANAGE'))
   OR (r.code = 'ADMIN' AND p.code IN ('INSPECTION_CHECKLIST_MANAGE', 'INSPECTION_FEE_MANAGE'))
ON CONFLICT DO NOTHING;
```

---

## 6. ĐẶC TẢ HỆ THỐNG RESTFUL APIS

### 6.1. Nhóm API Báo giá & Yêu cầu Kiểm định (Buyer & Seller)

1. `POST /api/v1/inspection/quote`
   - **Mục đích**: Báo giá chi tiết đồng thời cả 2 gói (`AT_HOME` và `AT_CENTER`) cho bài đăng.
   - **Body**: `{ "postId": "uuid", "buyerAddress": { "latitude": 10.77, "longitude": 106.70 } }`
   - **Response**: Trả về `baseFee`, `distanceKm`, `travelFeeOneWay`, `travelFeeRoundTrip` (áp dụng bảng 6 bậc thang), `returnShippingReserveFee`, tổng phí từng gói và mức chia tiền gợi ý.
2. `POST /api/v1/inspection/requests`
   - **Mục đích**: Người mua tạo yêu cầu mua hàng có kiểm định.
   - **Body**: `{ "postId": "uuid", "orderType": "AT_HOME", "feePayer": "SPLIT_50_50", "buyerConsentZinLoss": true }`
3. `POST /api/v1/inspection/requests/{orderId}/respond`
   - **Mục đích**: Người bán phản hồi yêu cầu kiểm định.
   - **Body**: `{ "action": "ACCEPT" | "REJECT" | "COUNTER", "counterFeePayer": "BUYER_PAYS", "sellerConsentZinLoss": true }`
4. `POST /api/v1/inspection/orders/{orderId}/pay-fee`
   - **Mục đích**: Người mua hoặc Người bán nộp tiền ký quỹ phí kiểm định (trong vòng 6h).
   - **Logic**: Trừ tiền ví SecondLife của người gọi, nạp vào Escrow. Khi cả 2 bên hoàn tất $\to$ Chuyển trạng thái đơn sang `CONFIRMED_PENDING_DISPATCH`.

### 6.2. Nhóm API Quản lý & Điều phối (Center Manager - `INSPECTION_CENTER`)

1. `GET /api/v1/inspector-center/orders`
   - **Mục đích**: Xem danh sách đơn kiểm định (lọc theo `status`, `orderType`, tìm kiếm).
2. `POST /api/v1/inspector-center/orders/{orderId}/assign?inspectorId={uuid}`
   - **Mục đích**: Phân công Inspector cho đơn.
3. `GET /api/v1/inspector-center/checklists` & `POST /api/v1/inspector-center/checklists`
   - **Mục đích**: Quản lý bộ checklist theo danh mục (CRUD).
4. `POST /api/v1/inspector-center/travel-tiers/propose`
   - **Mục đích**: Đề xuất điều chỉnh bảng giá bậc thang di chuyển KTV $\to$ gửi Admin sàn duyệt.
5. `GET /api/v1/inspector-center/staff` & `POST /api/v1/inspector-center/staff`
   - **Mục đích**: Quản lý danh sách tài khoản Kỹ thuật viên (Chỉ tạo role `INSPECTOR`).

### 6.3. Nhóm API Kỹ thuật viên thực thi (`INSPECTOR`)

1. `GET /api/v1/inspector/my-orders`
   - **Mục đích**: Xem các đơn kiểm định được phân công cho mình.
2. `POST /api/v1/inspector/orders/{orderId}/schedule`
   - **Mục đích**: Đặt lịch hẹn ngày giờ đến nhà người bán (`AT_HOME`).
3. `GET /api/v1/inspector/orders/{orderId}/checklist`
   - **Mục đích**: Nạp biểu mẫu checklist tiêu chí kỹ thuật cần kiểm tra của sản phẩm này.
4. `POST /api/v1/inspector/orders/{orderId}/submit-report`
   - **Body**: `{ "status": "PASSED" | "FAILED", "note": "...", "videoProofUrl": "...", "unboxingVideoUrl": "...", "checklistResults": [ { "itemId": "uuid", "isPassed": true, "inspectorNote": "Màn hình zin, pin 92%" } ] }`

### 6.4. Nhóm API Quản trị Hệ thống Sàn (`ADMIN`)

1. `POST /api/v1/admin/inspection-centers`
   - **Mục đích**: Khởi tạo tài khoản quản lý Trung tâm kiểm định (`INSPECTION_CENTER`).
2. `POST /api/v1/admin/inspection-fee-proposals/{proposalId}/review`
   - **Body**: `{ "approved": true, "reason": "Chấp thuận bảng giá bậc thang mới" }`
3. `PUT /api/v1/admin/inspection/travel-tiers`
   - **Mục đích**: Admin sàn chủ động cấu hình trực tiếp bảng giá 6 bậc thang.

---

## 7. KIẾN TRÚC GIAO DIỆN & TÍCH HỢP FRONTEND

1. **Pop-up Cảnh báo tháo máy mất zin**:
   - Tích hợp tại [CheckoutModal.tsx](file:///P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/components/modals/CheckoutModal.tsx) và [ChatModal.tsx](file:///P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/components/modals/ChatModal.tsx).
   - Checkbox bắt buộc: *"Tôi đã hiểu và đồng ý rằng sản phẩm sẽ được tháo máy để kiểm định chất lượng bên trong."*
2. **Khối Báo giá chi tiết gói Tại nhà (Lũy tiến 6 bậc)**:
   - Hiển thị bảng diễn giải khoảng cách: Ví dụ: `35 km = (5km x 2.000) + (5km x 2.500) + ... + x 2 chiều = 235.000 đ`.
   - Minh bạch chi phí giúp người mua và người bán an tâm chọn phương án.
3. **Thanh đếm ngược Ký quỹ 6 giờ (Countdown Timer)**:
   - Hiển thị trên màn hình chi tiết đơn hàng / Chat modal: `"Thời gian còn lại để nộp phí kiểm định: 04:32:15"`.
4. **Portal Trung tâm Kiểm định ([InspectorPortalView.tsx](file:///P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/pages/InspectorPortalView.tsx))**:
   - Tab **Điều phối (Center Dispatch)**: Danh sách đơn chờ, nút chọn KTV phân công.
   - Tab **Checklist Studio**: Tạo/sửa tiêu chí kiểm tra theo ngành hàng (Đồ điện tử, Nội thất, Gia dụng...).
   - Tab **Bảng giá Bậc thang (Tier Pricing Tab)**: Hiển thị bảng 6 bậc, cho phép chỉnh sửa và bấm "Gửi đề xuất tới Admin sàn".
5. **Trạm Kỹ thuật viên ([ProductInspectionStationView.tsx](file:///P:/FPT/Capstone/FE_SecondLife/SEP490_FE_SecondLife/src/pages/ProductInspectionStationView.tsx))**:
   - Form chấm điểm checklist trực quan (Pass / Fail từng tiêu chí).
   - Nút quay / upload video bằng chứng qua Cloudinary.
   - Nút Submit biên bản kiểm định.

---

## 8. LỘ TRÌNH TRIỂN KHAI TỪNG BƯỚC (STEP-BY-STEP ROADMAP)

### Bước 1: Hạ tầng Database & Entity (Backend)
- [ ] Viết Flyway migration `V40__comprehensive_product_inspection_system.sql`.
- [ ] Tạo các Entity: `InspectionTravelTier`, `InspectionFeeProposal`, `InspectionChecklistTemplate`, `InspectionChecklistItem`.
- [ ] Cập nhật Entity `InspectionOrder` với các trường nghiệp vụ mới.
- [ ] Tạo các Repository tương ứng trong package `com.secondlife.secondlife.repository`.

### Bước 2: Core Services & Công thức Toán học (Backend)
- [ ] Xây dựng `InspectionTravelFeeCalculator`: Hàm tính cước lũy tiến 6 bậc chuẩn xác theo khoảng cách Haversine và nhân đôi 2 chiều.
- [ ] Xây dựng `InspectionPricingService`: Tính phí cho cả `AT_HOME` và `AT_CENTER`.
- [ ] Xây dựng `InspectionEscrowScheduler`: Scheduler quét các đơn quá hạn 6 giờ chưa đủ 2 bên thanh toán $\to$ tự động hủy và hoàn tiền ví.
- [ ] Xây dựng `InspectionChecklistService` & `InspectionExecutionService`.

### Bước 3: REST Controllers & Bảo mật Phân quyền (Backend)
- [ ] Cập nhật `InspectionController` & `InspectionCenterController`.
- [ ] Viết `InspectionFeeAdminController` cho Admin sàn phê duyệt bảng giá.
- [ ] Viết Unit test & Integration test cho công thức tính phí bậc thang và luồng ký quỹ 6 giờ.

### Bước 4: Tích hợp Giao diện (Frontend)
- [ ] Bổ sung các hàm gọi API vào `inspectorService.ts`.
- [ ] Cập nhật modal Checkout/Chat hiển thị chi tiết phí 6 bậc và cảnh báo mất zin.
- [ ] Hoàn thiện giao diện Checklist Studio và Dispatching trên `InspectorPortalView.tsx`.
- [ ] Hoàn thiện giao diện chấm checklist trên `ProductInspectionStationView.tsx`.

### Bước 5: Kiểm thử Toàn trình & Demo Simulation
- [ ] Chạy seed data kịch bản kiểm định hoàn chỉnh (1 đơn Tại nhà, 1 đơn Tại trung tâm).
- [ ] Chạy thử nghiệm Happy case (Kiểm định Pass $\to$ Buyer trả nốt tiền hàng $\to$ Giao hàng $\to$ Giải ngân).
- [ ] Chạy thử nghiệm Unhappy case (Kiểm định Fail $\to$ Hoàn hàng về Seller từ quỹ dự phòng).
- [ ] Chạy thử nghiệm Timeout case (Quá 6h không đóng tiền $\to$ Tự động hoàn tiền).

---
*Tài liệu này được lưu trữ tại `secondLife/docs/PRODUCT_INSPECTION_END_TO_END_PLAN.md` làm tài liệu kiến trúc chuẩn mực xuyên suốt quá trình phát triển tính năng.*
