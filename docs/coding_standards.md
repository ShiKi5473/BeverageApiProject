# 🥤 Beverage POS & KDS 專案程式碼規範 (Coding Standards)

基於目前專案的架構 (Spring Boot + Vite/Vanilla JS)，為了維持高可讀性、降低維護成本及確保團隊協作順暢，以下制定專案專屬的程式碼規範：

---

## ☕ 1. 後端 Java (Spring Boot) 規範

### 1.1 命名慣例 (Naming Conventions)
* **Packages**: 採用全小寫，依照模組劃分，例如 `tw.niels.beverage_api_project.modules.order.controller`。
* **Classes & Interfaces**: 使用 `PascalCase` (大駝峰)。
    * Controller 必須以 `Controller` 結尾 (例：`OrderController`)。
    * Service 必須以 `Service` 結尾 (例：`OrderService`)。
    * Repository 必須以 `Repository` 結尾 (例：`OrderRepository`)。
    * DTO 必須以 `Dto` 結尾，並區分 Request/Response (例：`CreateOrderRequestDto`, `OrderResponseDto`)。
    * Entity 不加任何後綴 (例：`Order`, `User`)。
* **Methods & Variables**: 使用 `camelCase` (小趨峰)。宣告需具備語意化 (例：`processPayment`, `updateOrderStatus`)。
* **Constants**: 使用 `CONSTANT_CASE` (全大寫加底線)，且修飾子為 `public static final` (例：`ApiPaths.API_V1`)。

### 1.2 架構與分層規則
採用 **Domain-Driven Design (DDD) 概念的模組化單體 (Modular Monolith)** 架構：
1. **Controller Layer**:
    * 只負責 HTTP 請求的接收、參數校驗 (`@Valid`)、權限控管 (`@PreAuthorize`) 及呼叫 Service/Facade。
    * ⚠️ **嚴禁**在 Controller 撰寫業務邏輯 (Business Logic)。
2. **Facade Layer**:
    * 當跨多個 Domain 服務 (例如：建立訂單需同時扣庫存、算折抵點數) 時，統一在 Facade 層 (`OrderProcessFacade`) 協調，避免 Service 互相依賴產生 Cyclic Dependency。
3. **Service Layer**:
    * 實現核心業務邏輯與狀態機流轉 (`OrderStateFactory`)。
    * 對資料進行讀寫時加上適當的 `@Transactional` (唯讀時使用 `@Transactional(readOnly = true)`)。
4. **Repository Layer**:
    * 與 DB 溝通，除了 JPA 自帶的方法外，自定義查詢盡量利用 Spring Data JPA 方法命名推導，或使用 `@Query`。

### 1.3 RESTful API 設計規範
* **路徑命名**：使用小寫、複數名詞，單詞間以連字號 `-` 分隔 (Kebab-case)。
    * ✅ GOOD: `GET /api/v1/orders/{orderId}`
    * ❌ BAD: `GET /api/v1/getOrder/1`
* **HTTP Methods**:
    * `GET`: 查詢資料
    * `POST`: 建立新資料、或是執行複雜動作 (如結帳 `/pos-checkout`)
    * `PUT`: 完整更新資源
    * `PATCH`: 局部更新資源 (如更新訂單狀態 `/{orderId}/status`)
    * `DELETE`: 刪除資源
* **Response 格式**: 若操作成功回傳適用的 HTTP 狀態碼 (如 200, 201) 並以 DTO 封裝；若失敗則透過全域例外處理器 (`GlobalExceptionHandler`)回傳統一格式的錯誤訊息 (包含 `code`, `message`)。

### 1.4 註解與註記
* 所有 Controller API 必須加上 Swagger/OpenAPI 註解 (`@Operation`, `@Tag`) 說明功能。
* 修改或刪除資料的 API 必須考量加上 `@Idempotent` 或 `@Audit` 標記。

---

## 🌐 2. 前端 JavaScript 規範

### 2.1 命名慣例
* **Variables & Functions**: 使用 `camelCase` (小趨峰)。
    * 函數命名建議以動詞開頭 (例：`fetchOrders`, `handleAddToCart`, `renderProducts`)。
* **Files**: 
    * 原則上使用 `kebab-case.js` (小寫與連字號) 或 `snake_case.js` (如：`inventory_audit.js`)。
    * 若為 UI Component class，可以使用 `PascalCase.js` (如：`ProductCard.js`)。

### 2.2 模組化與架構
* **API 封裝**: 
    * 所有與後端的 AJAX/Fetch 請求集中在專屬的檔案中 (例如：`api.js`, `auth.js`)。
    * 元件/頁面腳本 (`pos.js`, `checkout.js`) 不應直接撰寫 Fetch，必須 `import` API 函式呼叫。
* **組件化 (Component-based)**:
    * 將可重用的 UI 區塊 (如 `Navbar`, `CartItem`, `ProductCard`) 抽出獨立的 JS 檔案。透過 `createElement` 或 Template Literals 組合 DOM，保持主程式邏輯簡潔。

### 2.3 狀態與事件處理
* 避免全域變數污染：盡量在 `DOMContentLoaded` (或以 ES Module) Scope 內宣告變數。
* **事件代理 (Event Delegation)**：對於動態生成的多個子元素 (如購物車列表中的刪除按鈕)，應將 Event Listener 綁定在其父層容器 (如 `cartItemsContainer`)，利用 `event.target.closest()` 判斷觸發元素。
* **例外處理**：API 呼叫必包覆於 `try...catch` 區塊，並於 Catch 內做合適的錯誤提示 (`alert` 或 Toast)。

### 2.4 WebSocket / SSE
* 若切換為 SSE 作為即時通知，務必處理好斷線重連 (`EventSource.onerror`)，且處理推播訊息的函式獨立拆分 (如 `handlePosSseMessage`)，保持邏輯清晰。

---

## 🗄️ 3. 資料庫規範

* **命名慣例 (PostgreSQL)**:
    * Tables 與 Columns 一律使用 `snake_case` (小寫加底線)，例如 `order_line_item`, `brand_id`。
* **約束與索引**:
    * FK (Foreign Keys) 必須明確宣告。
    * 針對常查詢的欄位 (如 `brand_id`, `store_id`, `status`) 建立適當的 Index。
* **軟刪除 (Soft Delete)**: 重要營運資料 (商品、訂單) 原則上不直接 `DELETE`，建議加上 `is_deleted` 布林值或獨立狀態標記作為封存。
