# 🚀 2025 飲料店 POS 系統升級藍圖 (System Upgrade Blueprint v3.2)

## 1. 概述
本計畫旨在將現有 POS 系統從單體架構逐步演進為高可用、高併發且具備可觀測性的分散式系統。升級路徑涵蓋核心業務功能的完善、技術底層的非同步化改造（RabbitMQ、MinIO）、資料存取層效能優化（Hybrid DAO），以及最終的微服務治理。

---

## 2. 階段一：核心邏輯優化 (Core Refactoring)
*目標：解決現有程式碼債務，確保交易一致性與資料存取效能，為後續擴充打好地基。*

### 2.1 引入 Facade 模式重構 OrderService
* **現況**：`OrderService` 職責過重，包含建立、庫存扣減、點數折抵與金流邏輯。
* **執行計畫**：
    - [x] 建立 `OrderProcessFacade` 作為統一入口，負責協調各服務。
    - [ ] 拆分獨立服務：
        - `MemberService`: [x] 已完成 (實作為 MemberPointService)
        - `InventoryService`: [ ] 已建立 Service 但 Facade 尚未串接扣庫存邏輯
        - `PaymentService`: [ ] 尚未建立 (目前直接使用 Repository)
    - [x] `OrderService` 僅保留 `Order` Entity 的 CRUD 操作。

### 2.2 強化 TSID 生成安全性
* **現況**：`nodeId` 依賴靜態環境變數，容器水平擴展時易發生 ID 衝突。
* **執行計畫**：
    - [x] 實作動態 Node ID 分配機制 (利用 Redis `INCR` 或 IP 位址運算)。
    - [x] 確保每個應用實例啟動時獲取唯一的 Worker ID (0-1023)。

### 2.3 優化庫存扣減併發控制 (Critical Fix) 🌟
* **現況**：目前鎖定 `InventoryItem` (Brand 層級)，導致全品牌分店搶同一把鎖，高併發下會造成「多店雪崩」。
* **執行計畫**：
    - [ ] **架構修正**：將庫存數量維護下放至分店層級，新增 `store_inventory` 表或調整鎖定策略。
    - [x] 在 `InventoryItem` 新增 `total_quantity` 欄位進行快速預扣 (列級鎖)。
    - [ ] 將 FIFO 批次扣減與效期管理移至背景非同步處理 (RabbitMQ)，解決高併發效能瓶頸。

### 2.4 統一異常訊息 (I18n)
* **執行計畫**：
    - [x] 引入 `MessageSource`。
    - [x] 將 Exception Message 代碼化 (e.g., `error.stock.insufficient`)，支援多語系回應。

### 2.5 混合式資料存取架構 (Hybrid DAO)
* **策略**：**CQRS 混合模式**。寫入用 JPA，批次更新與報表查詢用 JDBC。
* **執行計畫**：
    - [x] **重構 `InventoryService`**：使用 `InventoryBatchDAO` (JdbcTemplate) 進行批次扣減。
    - [x] **重構 `ReportRepository`**：使用 Native SQL 優化報表統計。

---

## 3. 階段二：業務功能擴充 (Business Expansion)
*目標：完善商業營運所需的行銷與管理功能。*

### 3.1 促銷活動系統 (Promotion Engine)
* **功能需求**：
    - [x] 支援多種策略：滿額折抵、百分比折扣、買 X 送 Y、組合優惠。
    - [x] 實作 `PromotionStrategy` 設計模式。
    - [x] **效能優化**：使用 `@Cacheable` 快取有效活動列表。

### 3.2 員工與權限管理 (Staff Management)
* **功能需求**：
    - [x] 完善 `RBAC` 模型 (Brand Admin / Manager / Staff)。
    - [x] 支援員工跨店調度與停權機制。

---

## 4. ⚡ 階段三：非同步架構、效能與可觀測性 (Async, Performance & Observability)
*目標：解耦系統組件，引入「削峰填谷」機制以應對秒殺流量。*

### 4.1 可觀測性 (Observability)
* **執行計畫**：
    - [x] 整合 Micrometer Tracing + Zipkin + OpenTelemetry。
    - [x] 為 HTTP、DB、RabbitMQ 注入 `Trace ID` 實現全鏈路追蹤。

### 4.2 檔案服務 (File Service)
* **執行計畫**：
    - [x] 整合 **MinIO** 物件儲存。
    - [x] 實作大檔案分片上傳 (Chunk Upload) 與合併 (Merge)。

### 4.3 訊息佇列與可靠性 (RabbitMQ Integration)
* **執行計畫**：
    - [x] **KDS 廣播**：使用 Fanout Exchange 實作訂單狀態跨實例同步。
    - [x] **死信佇列 (DLQ)**：配置 `dlq.exchange` 與 `dlq.queue` 處理失敗訊息。
    - [x] **發送確認**：啟用 Publisher Confirm/Returns 確保訊息不丟失。

### 4.4 線上訂單削峰填谷 (Queue-Based Load Leveling) 🌟 (新增)
* **目標**：解決線上點餐瞬間高流量導致資料庫崩潰的問題。
* **架構設計**：Producer (API) -> MQ -> Consumer (Worker) -> DB。
* **執行計畫**：
    - [ ] **定義 DTO**：建立 `AsyncOrderTaskDto` 封裝請求內容與租戶資訊 (BrandId/StoreId)。
    - [ ] **生產者 (API)**：新增 `OnlineOrderController`，僅負責將請求序列化並發送至 `online.order.queue`，立即回傳 `requestId`。
    - [ ] **消費者 (Worker)**：實作 `OrderMessageConsumer`，負責：
        1. **冪等性檢查**：利用 Redis `setIfAbsent` 防止重複消費。
        2. **業務執行**：呼叫 `OrderProcessFacade` 進行實際扣庫存與存檔。
        3. **手動 ACK**：確保業務成功才移除訊息；失敗則 NACK 並視情況進入 DLQ。

### 4.5 Redis 多層級快取 & API 冪等性
* **執行計畫**：
    - [x] 針對熱門商品列表 (`ProductController`) 加入 `@Cacheable`。
    - [x] 實作 `@Idempotent` 註解與 AOP 切面，防止前端重複送單。

### 4.6 效能基準測試 (Load Testing)
* **執行計畫**：
    - [x] 撰寫 K6 腳本 (`inventory_stress.js`, `pos_ordering_flow.js`)。
    - [x] 驗證悲觀鎖與異步架構在高併發下的正確性。

### 4.7 審計日誌 (Audit Logging)
* **執行計畫**：
    - [x] 使用 **MongoDB** 儲存操作軌跡。
    - [x] 實作 `@Audit` AOP 非同步寫入日誌。

---

## 5. 🤝 階段四：即時互動功能 (Real-time Interaction)
*目標：升級通訊協定，實作技術亮點功能。*

### 5.1 WebSocket (STOMP) 基礎建設
* **執行計畫**：
    - [ ] 配置 STOMP over WebSocket (`/ws-client`, `/ws-kds`)。
    - [ ] 實作 `ChannelInterceptor` 進行 JWT Token 的連線驗證 (Handshake 階段)。

### 5.2 線上揪團功能 (Group Ordering)
* **邏輯設計**：
    - [ ] 實作「揪團房間」邏輯，利用 Redis Hash 儲存房間狀態。
    - [ ] 透過 WebSocket 廣播購物車變更事件。

### 5.3 未結帳訂單持久化 (Unfinished Order Persistence)
* **執行計畫**：
    - [ ] 利用 Redis 暫存購物車內容，設定 TTL 自動過期。
    - [ ] 結帳時才將 Redis 資料轉為 PostgreSQL 訂單。

---

## 6. 🏗️ 階段五：微服務拆分與治理 (Microservices Transformation)
*目標：在單體架構基礎上進行邊界治理，視需求進行物理拆分。*

### 6.1 模組化單體重構 (Modular Monolith)
* **執行計畫**：
    - [ ] 強制隔離業務邊界 (Package 封裝)，禁止跨模組直接 Repository 存取。

### 6.2 分散式治理
* **執行計畫**：
    - [ ] **分散式鎖**：引入 **Redisson** 取代目前的 DB 悲觀鎖或 ShedLock (視效能需求)。
    - [ ] **斷路器**：引入 **Resilience4j** 保護外部服務呼叫。