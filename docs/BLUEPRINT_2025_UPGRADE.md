# 🚀 2025 飲料店 POS 系統升級藍圖 (System Upgrade Blueprint v3.1)

## 1. 概述
本計畫旨在將現有 POS 系統從單體架構逐步演進為高可用、高併發且具備可觀測性的分散式系統。升級路徑涵蓋核心業務功能的完善（促銷、員工管理）、技術底層的非同步化改造（RabbitMQ、MinIO）、資料存取層效能優化（Hybrid DAO）、可觀測性建設（Zipkin），以及最終的微服務治理。

---

## 2. 階段一：核心邏輯優化 (Core Refactoring)
*目標：解決現有程式碼債務，確保交易一致性與資料存取效能，為後續擴充打好地基。*

### 2.1 引入 Facade 模式重構 OrderService
* **現況**：`OrderService` 職責過重 (God Class)，包含建立、庫存扣減、點數折抵與金流邏輯。
* **執行計畫**：
    - [ ] 建立 `OrderProcessFacade` 作為統一入口，負責協調各服務。
    - [ ] 拆分獨立服務：`InventoryService` (純庫存操作)、`PaymentService` (金流串接)、`MemberService` (點數計算)。
    - [ ] `OrderService` 僅保留 `Order` Entity 的 CRUD 操作。

### 2.2 強化 TSID 生成安全性
* **現況**：`nodeId` 依賴靜態環境變數，容器水平擴展時易發生 ID 衝突。
* **執行計畫**：
    - [ ] 實作動態 Node ID 分配機制 (利用 Redis `INCR` 或 IP 位址運算)。
    - [ ] 確保每個應用實例啟動時獲取唯一的 Worker ID (0-1023)。

### 2.3 優化庫存扣減併發控制
* **現況**：直接鎖定多筆 `InventoryBatch` (FIFO)，高併發下易發生 Deadlock。
* **執行計畫**：
    - [ ] 在 `InventoryItem` 新增 `total_quantity` 欄位進行快速預扣 (列級鎖)。
    - [ ] 將 FIFO 批次扣減與效期管理移至背景非同步處理 (RabbitMQ)，解決高併發效能瓶頸。

### 2.4 統一異常訊息 (I18n)
* **執行計畫**：
    - [ ] 引入 `MessageSource`。
    - [ ] 將 Exception Message 代碼化 (e.g., `error.stock.insufficient`)，支援多語系回應。

### 2.5 混合式資料存取架構 (Hybrid Data Access Architecture) 🌟 (新加入)
* **目標**：解決 JPA 在高併發批次更新與複雜報表查詢時的效能瓶頸，同時保留 ORM 在處理複雜關聯上的優勢。
* **策略**：**CQRS (讀寫分離) 概念混合模式**。
    * **Command (寫入/複雜關聯)**：保留 **Spring Data JPA**，處理 `Order` -> `OrderItem` 等複雜物件圖。
    * **Query (報表/列表) & Bulk Update (批次扣減)**：引入 **DAO 層 (JdbcTemplate)**，繞過 Hibernate Context 直接執行高效能 SQL。
* **執行計畫**：
    - [ ] **重構 `InventoryService`**：新增 `InventoryBatchDAO`，使用 JDBC 執行 `UPDATE ... WHERE id IN (...)` 取代迴圈 JPA Save，減少 DB Round-trip。
    - [ ] **重構 `ReportRepository`**：將 `DailyStoreStatsRepository` 的複雜 JPQL 遷移至 DAO 使用 Native SQL 實作，利用資料庫特定優化。
    - [ ] **架構規範**：採用 Spring Data Custom Repository Pattern (`OrderRepositoryCustom`) 封裝 DAO 實作，對上層 Service 隱藏底層差異。

---

## 3. 階段二：業務功能擴充 (Business Expansion)
*目標：完善商業營運所需的行銷與管理功能。*

### 3.1 促銷活動系統 (Promotion Engine)
* **功能需求**：
    - [ ] 支援多種策略：滿額折抵、百分比折扣、買 X 送 Y、組合優惠。
    - [ ] 實作 `PromotionStrategy` 設計模式，確保折扣計算邏輯的可擴充性。
    - [ ] **效能優化**：在 `@Cacheable` 層快取有效活動列表，減少資料庫查詢。

### 3.2 員工與權限管理 (Staff Management)
* **功能需求**：
    - [ ] 完善 `RBAC` (Role-Based Access Control) 模型。
    - [ ] 實作「店長 (Manager)」與「品牌管理員 (Brand Admin)」的分層權限控管。
    - [ ] 支援員工跨店調度與停權機制 (Soft Delete)。

---

## 4. ⚡ 階段三：非同步架構、效能與可觀測性 (Async, Performance & Observability)
*目標：解耦系統組件，提升大檔案處理能力，並建立對非同步流程的監控能力。*

### 4.1 可觀測性 (Observability) 🌟
* **技術棧**：Micrometer Tracing + Zipkin + OpenTelemetry。
* **執行計畫**：
    - [ ] 為所有 HTTP 請求、DB 查詢與 RabbitMQ 訊息注入 `Trace ID` 與 `Span ID`。
    - [ ] 實現全鏈路追蹤 (Distributed Tracing)，解決非同步架構下「訊息丟失」或「效能瓶頸」難以除錯的問題。

### 4.2 實作斷點續傳與分片上傳 (Resumable Upload)
* **場景**：商品圖片、行銷素材等大檔案上傳。
* **執行計畫**：
    - [ ] 整合 **MinIO** 作為物件儲存後端。
    - [ ] 實作 `FileService` 介面：支援 `Check` (檢查分片), `UploadChunk` (上傳分片), `Merge` (合併檔案)。
    - [ ] **垃圾回收機制 (GC)**：實作 Spring Schedule (`FileCleanupSchedule`)，每日凌晨掃描 Redis 中過期未完成的上傳任務，並呼叫 MinIO API 清理無效暫存分片。

### 4.3 引入 RabbitMQ (Message Queue)
* **執行計畫**：
    - [ ] 將「KDS 訂單通知」改為 RabbitMQ Fanout Exchange，確保不漏單。
    - [ ] 將「報表非同步計算」與「會員點數結算」改為事件驅動 (Event-Driven) 架構。
    - [ ] 設定 Dead Letter Queue (DLQ) 處理消費失敗的訊息。

### 4.4 Redis 多層級快取 & API 冪等性
* **執行計畫**：
    - [ ] 針對熱門商品列表 (`ProductController`) 加入 `@Cacheable`。
    - [ ] 利用 Redis `setIfAbsent` (NX) 實作 **Idempotency Key**，防止網路抖動造成的重複下單。

### 4.5 效能基準測試 🌟
* **工具**：**K6** (Load Testing)。
* **執行計畫**：
    - [ ] 撰寫負載測試腳本，模擬高併發點餐場景。
    - [ ] 建立 RPS (Requests Per Second) 與 Latency (P95/P99) 的效能基準線 (Baseline)。

---

## 5. 🤝 階段四：即時互動功能 (Real-time Interaction)
*目標：升級通訊協定，實作技術亮點功能。*

### 5.1 WebSocket (STOMP) 基礎建設
* **執行計畫**：
    - [ ] 配置 STOMP over WebSocket (`/ws-client`, `/ws-kds`)。
    - [ ] 實作 `ChannelInterceptor` 進行 JWT Token 的連線驗證 (Handshake 階段)。

### 5.2 線上揪團功能 (Group Ordering)
* **邏輯設計**：
    - [ ] 實作「揪團房間」邏輯，利用 Redis Hash 儲存房間狀態 (`group_cart:{roomId}`)。
    - [ ] 透過 WebSocket 廣播 `CART_UPDATED` 事件，實現多人即時購物車同步。
    - [ ] 團主結單時，將 Redis 資料轉換為 PostgreSQL 訂單。

### 5.3 Refresh Token 機制
* **執行計畫**：
    - [ ] 實作雙 Token (Access Token 短效期 + Refresh Token 長效期) 驗證流程。
    - [ ] 提升安全性（Access Token 洩漏風險低）與使用者體驗（無感續約，不用頻繁登入）。

### 5.4 未結帳訂單持久化策略 (Unfinished Order Persistence) 🌟 (新加入)
* **目標**：確保線上點餐與揪團過程中，資料在「未正式結帳前」具備持久性，不因伺服器重啟或用戶斷線而遺失，同時避免無效訂單汙染主資料庫。
  * **技術選型**：採用 **Redis** (In-Memory Data Store)。
  * **執行計畫**：
      - [ ] **分層儲存設計**：
          - **個人/揪團暫存**：使用 Redis (`Hash` 或 `JSON` 結構) 儲存購物車內容。Key 範例：`cart:{userId}` 或 `group_order:{groupId}`。
          - **正式訂單**：僅在「確認結帳」當下，將 Redis 資料轉換為 `Order` Entity 寫入 PostgreSQL。
      - [ ] **生命週期管理**：
          - 設定 Redis Key 的 **TTL (Time To Live)** (如 24 小時)，實現自動過期清理機制，無需額外編寫排程刪除垃圾資料。
      - [ ] **狀態同步**：結合 WebSocket，當 Redis 中的暫存狀態變更時，即時廣播給前端，確保多裝置/多用戶看到的購物車狀態一致。

---

## 6. 🏗️ 階段五：微服務拆分與治理 (Microservices Transformation)
*目標：在單體架構基礎上進行邊界治理，視需求進行物理拆分。*

### 6.1 模組化單體重構 (Modular Monolith) 🌟 (優先執行)
* **執行計畫**：
    - [ ] 利用 Java Package 或 Maven Multi-Module 強制隔離業務邊界。
    - [ ] 規則：`Order` 模組 **不可直接** `@Autowired` `ProductRepository`，必須透過 `ProductService` 介面或 Domain Events 進行互動。

### 6.2 實體服務拆分
* **執行計畫**：
    - [ ] 視流量與團隊規模，將 **Report (報表)** 或 **File (檔案)** 等獨立性高、資源消耗型態不同的模組，拆分為獨立的 Spring Boot 微服務。

### 6.3 分散式治理
* **執行計畫**：
    - [ ] **分散式鎖**：將資料庫悲觀鎖 (`SELECT FOR UPDATE`) 替換為 **Redisson** 分散式鎖，用於庫存扣減與檔案合併。
    - [ ] **斷路器 (Circuit Breaker)**：引入 **Resilience4j**，保護服務間的呼叫，防止單一模組故障拖垮整個系統。