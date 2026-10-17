# CLAUDE.md — BeverageApiProject 後端

> 這份文件提供給 AI 助理（Claude）的專案指引，包含架構說明、命名規範與常見工作流程。
> 進行任何修改前，請先閱讀本文件。

---

## 1. 專案概述

多租戶飲料店 POS / KDS 後端 API 系統，支援多品牌（Brand）資料隔離。
核心功能：POS 點餐結帳、廚房顯示系統 (KDS)、庫存管理（週期性盤點+配方扣減）、會員積點、促銷折扣、報表。

**技術堆疊**：Java 21、Spring Boot 3.x、PostgreSQL、Flyway、RabbitMQ、MongoDB（稽核日誌）、S3 相容物件儲存（檔案；以 MinIO Java SDK 存取，本機 compose 與整合測試使用 RustFS `rustfs/rustfs:1.0.1`，因 MinIO 已停止公開提供 Docker 映像）、Redis（快取）

---

## 2. 常用指令

```bash
# 編譯（必須使用 Wrapper，勿直接呼叫 mvn）
./mvnw compile

# 執行測試
./mvnw test

# 打包
./mvnw clean package -DskipTests

# 本機啟動（需先確認 .env 或 application-local.properties 存在）
./mvnw spring-boot:run
```

**⚠️ 禁止事項**
- 不可直接執行 `mvn`，一律使用 `./mvnw`
- 執行破壞性指令（`docker-compose down -v`、刪除檔案）前必須先取得確認
- 不可直接修改舊版 Flyway 腳本（`V*__*.sql`）

---

## 3. 專案架構

### 3.1 整體結構

```
src/main/java/tw/niels/beverage_api_project/
├── common/              # 跨 BC 共用（VO、Exception、AOP、Helper）
├── config/              # Spring 設定（Security、Cache、MQ 等）
├── security/            # JWT 驗證、CustomUserDetailsService
└── modules/             # 各 Bounded Context（共 14 個）
    ├── order/           # 核心領域 ★★★
    ├── product/         # 核心領域 ★★★
    ├── promotion/       # 核心領域 ★★★
    ├── inventory/       # 支撐領域 ★★
    ├── member/          # 支撐領域 ★★
    ├── brand/           # 支撐領域 ★★
    ├── store/           # 支撐領域 ★★
    ├── user/            # 通用領域 ★
    ├── kds/             # 通用領域 ★
    ├── report/          # 通用領域 ★
    ├── audit/           # 通用領域 ★
    ├── auth/            # 通用領域 ★
    ├── platform/        # 通用領域 ★
    └── file/            # 通用領域 ★
```

### 3.2 每個模組的分層（以 `order` 為例）

```
modules/order/
├── application/
│   ├── command/         # Command 物件（CQRS 寫端）
│   └── handler/         # Command/Query Handler（Application Layer）
├── consumer/            # RabbitMQ 消費者
├── controller/          # REST Controller（僅 HTTP 轉換）
├── domain/
│   ├── model/           # Aggregate、Entity、Value Object（純 Java）
│   ├── repository/      # Domain Repository 介面
│   └── service/         # Domain Service
├── dto/                 # Request/Response DTO
├── entity/              # JPA Entity（後綴 Entity）
├── enums/               # 枚舉
├── event/               # 領域事件
├── infrastructure/
│   ├── mapper/          # JPA Entity ↔ Domain Model 轉換
│   └── persistence/
│       └── repository/  # Domain Repository 介面的 JPA 實現
├── repository/          # Spring Data JPA Repository 介面（後綴 JpaRepository）
├── service/             # 少數仍保留的 Application/Domain 輔助服務（如 OrderNumberService）
└── vo/                  # Value Object（舊架構，逐步移至 domain/model）
```

### 3.3 請求流程

```
HTTP Request
  → Controller（參數驗證、權限檢查）
  → Command/Query 物件
  → Handler（Application Layer，協調 Domain + Infrastructure）
  → Domain Model（業務邏輯）
  → Repository（持久化，透過 RepositoryImpl）
  → JpaRepository（Spring Data JPA）
  → DB
```

---

## 4. 命名規範

### 4.1 類別命名（關鍵規則）

| 類型 | 命名規則 | 範例 |
|------|----------|------|
| JPA Entity | 後綴 `Entity` | `OrderEntity`, `UserEntity` |
| Domain Model (Aggregate/Entity) | 無後綴 | `Order`, `User` |
| Spring Data Repository | 後綴 `JpaRepository` | `OrderJpaRepository` |
| Domain Repository 介面 | 後綴 `Repository` | `OrderRepository` |
| Domain Repository 實作 | 後綴 `RepositoryImpl` | `OrderRepositoryImpl` |
| JPA ↔ Domain 轉換工具 | 後綴 `Mapper` | `OrderMapper` |
| Controller | 後綴 `Controller` | `OrderController` |
| Application Handler | 後綴 `Handler` | `PosCheckoutHandler` |
| Application Service | 後綴 `Service` | `OrderNumberService` |
| Request DTO | 後綴 `RequestDto` | `CreateOrderRequestDto` |
| Response DTO | 後綴 `ResponseDto` | `OrderResponseDto` |
| Command 物件 | 後綴 `Command` | `PosCheckoutCommand` |
| 領域事件 | 後綴 `Event` | `OrderPlacedEvent` |
| 枚舉 | 無特定後綴，語意清楚即可 | `OrderStatus`, `StaffRole` |

> **⚠️ 重要**：JPA Entity 與 Domain Model 是兩套並存的型別系統，不可混用。
> - `entity.OrderEntity` = JPA 持久化物件，對應資料庫
> - `domain.model.Order` = 純領域物件，包含業務邏輯

### 4.2 套件命名

```
tw.niels.beverage_api_project.modules.{bc_name}.{layer}
```

### 4.3 資料庫命名

- Tables / Columns：`snake_case`（小寫加底線）
- 範例：`order_items`, `brand_id`, `created_at`

---

## 5. DDD 關鍵模式

### 5.1 Aggregate 設計

- Aggregate Root 暴露工廠方法（`Order.place()`, `Order.reconstitute()`）
- 所有狀態變更必須透過 Aggregate Root 方法，不可直接 setter
- `reconstitute()` 供 Mapper 從 JPA Entity 重建 Domain Model 使用

### 5.2 Value Object

- 使用 Java record 實作（不可變）
- 重要 VO：`Money`（金額，存取用 `.amount()`）、`OrderNumber`、`ProductSnapshot`、`MemberSnapshot`

### 5.3 Domain Event

- Aggregate 內部透過 `registerEvent()` 累積事件
- Handler 在儲存後呼叫 `aggregate.pullDomainEvents().forEach(eventPublisher::publishEvent)` 發佈
- **點數扣減是「同步」的**：POS 結帳（`PosCheckoutHandler` / `ProcessPaymentHandler`）在同一個結帳交易內以 `MemberRepository.findById` 載入會員並呼叫 `member.consumePoints()`、`memberRepository.save(member)`；餘額不足轉成 `DomainException`（HTTP 422），整筆交易回滾、不建立訂單；併發衝突由 `MemberProfileEntity` 的 `@Version` 樂觀鎖保護（HTTP 409）。**不要**再把扣點放到 AFTER_COMMIT（`OrderPointsDeductedEvent` / `PointDeductionEventHandler` 已移除）。
- 累點（訂單 CLOSED）與退點（訂單 CANCELLED）等其他跨 BC 副作用仍必須用 `@TransactionalEventListener(AFTER_COMMIT)` 處理

### 5.4 CQRS Handler 模式

- 寫端：`{Action}Handler`，加 `@Transactional`，接收 Command 物件，回傳 Long（ID）
- 查端：`{Query}Handler`，加 `@Transactional(readOnly = true)`

---

## 6. 編碼標準

### 6.1 Controller 規則

```java
// ✅ 正確：Controller 只負責 HTTP 轉換，一律回傳 DTO（不回傳 JPA Entity 或裸 ID）
@PostMapping("/pos-checkout")
@Idempotent // 前端帶 Idempotency-Key；重送時重放第一次的 2xx 結果
@PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER', 'STAFF')")
@Operation(summary = "POS 快速結帳")
public ResponseEntity<OrderResponseDto> posCheckout(@Valid @RequestBody PosCheckoutRequestDto dto) {
    PosCheckoutCommand command = new PosCheckoutCommand(...);
    Long orderId = posCheckoutHandler.handle(command);
    // 讀取端（CQRS）直接由 OrderEntity 組出回應 DTO
    return new ResponseEntity<>(orderQueryHandler.handle(new GetOrderQuery(brandId, orderId)), HttpStatus.CREATED);
}

// ❌ 禁止：在 Controller 寫業務邏輯
```

### 6.2 Transaction 規則

- 讀取：`@Transactional(readOnly = true)`
- 寫入：`@Transactional`（僅在 Handler 或 Service 加，Controller 不加）
- 跨 BC 副作用：`@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`

### 6.3 REST API 規範

```
GET    /api/v1/brands/{brandId}/orders          # 查詢列表
GET    /api/v1/brands/{brandId}/orders/{id}     # 查詢單筆
POST   /api/v1/brands/{brandId}/orders          # 建立
PATCH  /api/v1/brands/{brandId}/orders/{id}/status  # 局部更新
DELETE /api/v1/brands/{brandId}/orders/{id}     # 刪除
```

- 路徑使用小寫、複數名詞、kebab-case
- 多租戶路徑必須包含 `brandId`

### 6.4 例外處理

- 找不到資源：拋出 `ResourceNotFoundException`（→ HTTP 404）
- 業務規則違反：拋出 `DomainException`（→ HTTP 422）
- 請求參數錯誤：拋出 `BadRequestException`（→ HTTP 400）
- 全域由 `GlobalExceptionHandler` 統一轉換為 `ErrorResponse`

### 6.5 Swagger 規範

所有 Controller API 必須加上：
```java
@Operation(summary = "操作說明")
@Tag(name = "模組名稱")
```

---

## 7. 資料庫遷移（Flyway）

```
src/main/resources/db/migration/
├── V1__init_schema.sql
├── V2__add_report_tables.sql
...
├── V17__add_order_version_column.sql
├── V18__online_order_fields_and_indexes.sql   # 線上訂單 guest_ref / request_id、訂單索引
├── V19__add_payment_breakdown_to_daily_store_stats.sql  # 日結付款方式拆分欄位
├── V20__add_unspecified_total_to_daily_store_stats.sql  # 日結「未指定付款方式」金額
└── V21__add_is_active_to_categories.sql  ← 目前最新版本（下一個請用 V22 以上）
```

**規則**：
- 新增欄位或表格必須建立新的遷移腳本，命名為 `V{N+1}__{描述}.sql`
- 絕對禁止修改已存在的 `V*__*.sql` 內容
- 腳本內命名一律 `snake_case`

---

## 8. 常見工作流程

### 8.1 新增 API 端點

1. 在 `dto/` 建立 Request/Response DTO
2. 在 `application/command/` 建立 Command 物件（record）
3. 在 `application/handler/` 建立 Handler，注入 Repository，加 `@Transactional`
4. 在 `controller/` 建立或更新 Controller，加 `@PreAuthorize` 和 Swagger 註解
5. 執行 `./mvnw compile` 確認編譯通過

### 8.2 新增 JPA Entity

1. 在 `modules/{bc}/entity/` 建立 `{Name}Entity.java`（必須有 `Entity` 後綴）
2. 在 `modules/{bc}/repository/` 建立 `{Name}JpaRepository.java`
3. 如需對應 Domain Model：在 `domain/model/` 建立 Domain 物件，在 `infrastructure/mapper/` 新增映射方法

### 8.3 修改資料庫 Schema

1. 確認最新 Flyway 版本號（目前最新：V21）
2. 建立 `V22__{描述}.sql`（版本號 = 目前最新 + 1），只用 `snake_case` 命名
3. 若有新欄位，同步更新對應的 JPA Entity（`@Column`）

### 8.4 除錯編譯錯誤

```bash
./mvnw compile 2>&1 | grep -E "ERROR|errors"
```

常見問題：
- `cannot find symbol` 且型別含 `Entity`：確認是 JPA Entity（`entity.*Entity`）還是 Domain Model（`domain.model.*`），勿混用
- `cannot find symbol` 且方法名：確認 JpaRepository 的方法名是否因 batch rename 被誤改（如 `Recipes` → `RecipeEntitys`）

---

## 9. 多租戶架構注意事項

- 所有資料存取必須帶入 `brandId` 進行隔離
- JWT 解析出 `brandId` 後由 `BrandContextHolder`（ThreadLocal）傳遞
- Domain Repository 方法簽名通常為 `findXxx(Long brandId, ...)`
- Controller 從 `@AuthenticationPrincipal` 或 Path Variable 取得 `brandId`，以參數明確傳遞給 Service/Handler
- **分店隔離**：已綁定分店的員工（MANAGER／STAFF／TESTER）只能操作自己分店的訂單。訂單寫入 Command 必帶 `StoreScope`（由 `ControllerHelperService.getAccessibleStoreId()` 建立，未綁店的品牌管理員為 brand-wide），Handler 載入訂單後於交易內呼叫 `storeScope().verifyAccess(order.getStoreId())`；不符一律回 403（`AccessDeniedException`）
- TESTER 角色權限比照 STAFF：新增端點時，凡允許 `'STAFF'` 的 `@PreAuthorize` 一併加上 `'TESTER'`

### 訪客（GUEST）與線上點餐

- 角色除員工（`ROLE_BRAND_ADMIN|MANAGER|STAFF|TESTER`）、會員（`ROLE_MEMBER`）、平台（`ROLE_PLATFORM_ADMIN`）外，新增 `ROLE_GUEST`：由 `POST /api/v1/auth/guest`（body 含 `brandId`、`displayName`）簽發，JWT claims `type=GUEST`、`brandId`，principal 為 `GuestPrincipal`。
- `AppUserDetails` 與 `GuestPrincipal` 共用 `TenantPrincipal` 介面；取得品牌請用 `ControllerHelperService.getCurrentBrandId()`（訪客也可用），`getCurrentUserDetails()` 對訪客會拋例外。
- 訪客／會員可用的 API：唯讀的商品（`/brands/products/pos`）、分類、分店（`/stores`，僅回傳自己品牌），以及線上下單 `POST /api/v1/online-orders`（需 `Idempotency-Key`，有速率限制；非同步經 RabbitMQ 處理，初始狀態 `AWAITING_ACCEPTANCE`，由 KDS 接單後變 `PREPARING`）。以 ticketId 輪詢 `GET /api/v1/online-orders/{ticketId}`。
- 線上訂單細節：建立時套用與 POS／`/orders/preview` 相同的促銷折扣（`OrderCheckoutService.calculate`，不可折抵點數）。消費者使用 `onlineOrderListenerContainerFactory`：業務／驗證例外（`AppException`、`DomainException`）不重試，暫時性錯誤依 `spring.rabbitmq.listener.simple.retry.*` 重試；ticket 只在最終失敗時標記 FAILED（不會覆寫 CREATED），訊息再進 DLQ。發送端以 `CorrelationData(ticketId)` 處理 broker nack／退回並標記 FAILED。
- 訂單號碼 `{storeId}-{yyyyMMdd}-{seq:04d}` 由 Redis 計數；計數 key 遺失時會先以資料庫當日最大序號原子性地重新播種，不會從 1 重來。
- 所有回應中的實體 ID 一律輸出為**字串**（`ToStringSerializer`），因 TSID 超過 JS 安全整數範圍；請求端 Jackson 可把字串或數字轉成 `Long`。Controller 一律回傳 DTO，不可直接回傳 JPA Entity。
- 「今天／現在」一律注入 `java.time.Clock`（`config/TimeConfig`，時區由 `app.timezone` 決定，預設 Asia/Taipei），使用 `LocalDate.now(clock)`；不可用 `LocalDate.now()` 或 `ZoneId.systemDefault()`。Dockerfile 已設 `TZ=Asia/Taipei`。
- 稽核日誌 `AuditLogService.saveLog` 以 `@Async("auditExecutor")` 非同步寫入 MongoDB；KDS 推播使用 `kdsExecutor`（兩者定義於 `AsyncConfig`）。
- 聚合更新原則：Repository 實作對既有資料要先載入受管 Entity 再套用欄位（BaseTsidEntity 的 `isNew` 預設 true，直接 `new Entity()+setId()` 再 save 會 persist 失敗）；關聯用 `EntityManager.getReference()`。

### Profile 與機密設定

- `application.properties` **不再預設啟用 dev profile**。本機開發請在 `.env` 設定 `SPRING_PROFILES_ACTIVE=dev`（dev 會執行 `DataSeeder`，重設已知密碼的測試帳號，**絕不可用於正式環境**）；正式環境使用 `SPRING_PROFILES_ACTIVE=prod`（關閉 Swagger、取樣率 0.1、不輸出 SQL）。
- 本機 `docker compose` 的密碼可用 `.env` 的 `DB_PASSWORD`、`MINIO_ACCESS_KEY`/`MINIO_SECRET_KEY`、`MONGO_USER`/`MONGO_PASSWORD`、`POSTGRES_PORT`（預設 5473）覆寫。
- `app.jwt.secret` 沒有預設值，必須由環境變數 `JWT_SECRET`（512-bit Base64）提供，缺少時啟動失敗。
- 整合測試（`AbstractIntegrationTest`）自行啟用 `dev` profile 並提供測試專用 JWT 密鑰。

---

## 10. 參考文件

| 文件 | 位置 | 說明 |
|------|------|------|
| 戰略設計 | `../DDD-Strategic-Design.md` | 領域劃分、通用語言、上下文映射 |
| 重構計劃 | `../DDD-Migration-Plan.md` | 各 Phase 進度、待辦事項 |
| 編碼規範 | `docs/coding_standards.md` | 詳細 Java / JS / DB 規範 |
| Agent Rules | `.antigravity/rules.md` | AI 協作規則 |
