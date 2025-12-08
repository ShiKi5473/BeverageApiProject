# 🚀 2025 飲料店 POS 系統升級藍圖 (System Upgrade Blueprint v3.3)

## 1. 概述
本計畫旨在將現有 POS 系統從單體架構逐步演進為高可用、高併發且具備可觀測性的分散式系統。升級路徑涵蓋核心業務功能的完善、技術底層的非同步化改造（RabbitMQ、MinIO）、資料存取層效能優化（Hybrid DAO），以及最終的微服務治理。

---

## 2. 階段一：核心邏輯優化 (Core Refactoring)
*目標：解決現有程式碼債務，確保交易一致性與資料存取效能，為後續擴充打好地基。*

### 2.1 引入 Facade 模式重構 OrderService
* **執行計畫**：
    - [x] 建立 `OrderProcessFacade` 作為統一入口，負責協調各服務。
    - [x] `OrderService` 僅保留 `Order` Entity 的 CRUD 操作。

### 2.2 強化 TSID 生成安全性
* **執行計畫**：
    - [x] 實作動態 Node ID 分配機制 (利用 Redis `INCR` 或 IP 位址運算)。

### 2.3 優化庫存扣減併發控制 (Critical Fix)
* **執行計畫**：
    - [x] 在 `InventoryItem` 新增 `total_quantity` 欄位進行快速預扣 (列級鎖)。
    - [x] **架構轉型**：從「FIFO 即時扣減」轉向「週期性盤點 (Periodic Review)」模式。

### 2.4 統一異常訊息 (I18n)
* **執行計畫**：
    - [x] 引入 `MessageSource`。

### 2.5 混合式資料存取架構 (Hybrid DAO)
* **執行計畫**：
    - [x] **重構 `InventoryService`**：使用 `InventoryBatchDAO` (JdbcTemplate) 進行批次處理。

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

---

## 4. 📦 階段三：庫存重構與稽核 (Inventory Audit & Refactoring) - ✅ 已完成
*目標：建立以「盤點」為核心的庫存管理機制，解決即時扣減不準確的問題。*

### 4.1 資料庫重構 (V9 Schema)
* **執行計畫**：
    - [x] 建立 `product_variants` (規格) 與 `recipes` (配方)。
    - [x] 建立 `inventory_snapshots` (快照) 與 `inventory_transactions` (稽核日誌)。
    - [x] `inventory_batches` 轉型為進貨履歷，下放 `store_id`。

### 4.2 手動盤點 API (Audit)
* **執行計畫**：
    - [x] 實作 `InventoryService.performAudit`，支援批次寫入快照與異動。
    - [x] 解決 N+1 查詢問題 (使用 Batch Fetching)。

---

## 5. 📊 階段四：耗損分析報表 (Waste Analysis) - ✅ 已完成
*目標：透過「理論 vs 實際」差異分析，協助業主控管成本。*

### 5.1 資料完整性補強 (V10 Schema)
* **執行計畫**：
    - [x] `inventory_transactions` 新增 `balance_after` 欄位，支援快速推算期初/期末庫存。

### 5.2 報表運算核心
* **執行計畫**：
    - [x] 實作 `InventoryReportService`。
    - [x] 計算公式：`實際消耗 (Opening + Restock - Closing)` vs `理論消耗 (Orders * Recipe)`。
    - [x] 實作 `GET /api/v1/reports/inventory-variance` 端點。

---

## 6. ⚡ 階段五：非同步架構、效能與可觀測性 (Async & Observability)
*目標：解耦系統組件，引入「削峰填谷」機制以應對秒殺流量。*

### 6.1 可觀測性 (Observability)
* **執行計畫**：
    - [x] 整合 Micrometer Tracing + Zipkin + OpenTelemetry。

### 6.2 檔案服務 (File Service)
* **執行計畫**：
    - [x] 整合 **MinIO** 物件儲存。
    - [x] 實作大檔案分片上傳 (Chunk Upload) 與合併 (Merge)。

### 6.3 訊息佇列與可靠性 (RabbitMQ Integration)
* **執行計畫**：
    - [x] **KDS 廣播**：使用 Fanout Exchange 實作訂單狀態跨實例同步。
    - [x] **死信佇列 (DLQ)**：配置 `dlq.exchange`。

### 6.4 線上訂單削峰填谷 (Queue-Based Load Leveling)
* **執行計畫**：
    - [x] **定義 DTO**：建立 `AsyncOrderTaskDto`。
    - [x] **生產者 (API)**：新增 `OnlineOrderController`。
    - [x] **消費者 (Worker)**：實作 `OrderMessageConsumer`。

### 6.5 效能基準測試 (Load Testing)
* **執行計畫**：
    - [x] 撰寫 K6 腳本 (`inventory_stress.js`, `pos_ordering_flow.js`, `online_ordering_async.js`)。

### 6.6 審計日誌 (Audit Logging)
* **執行計畫**：
    - [x] 使用 **MongoDB** 儲存操作軌跡。

---

## 7. 🤝 階段六：即時互動功能 (Real-time Interaction)
*目標：升級通訊協定，實作技術亮點功能。*

### 7.1 後端基礎建設 (Backend Infrastructure) - ✅ 已完成
* [x] **啟用 STOMP**：配置 `WebSocketConfig` 與端點 `/ws-kds`。
* [x] **安全性整合**：實作 `JwtAuthChannelInterceptor` 攔截 WebSocket 連線並驗證 Token。
* [x] **主動推播邏輯**：整合 `SimpMessagingTemplate` 至 `OrderMessageConsumer`，在訂單寫入成功後推播至 `/user/queue/orders`。

### 7.2 前端整合 (Frontend Integration) - 📝 待辦事項 (To-Do)
* [ ] **升級 Client**：修改 `ws-client.js` 以支援 JWT Header 帶入。
* [ ] **訂閱頻道**：實作訂閱 `/user/queue/orders` 的邏輯。
* [ ] **UI 通知**：在收到 `ORDER_CREATED` 訊息時顯示 Toast 或 Alert 通知使用者。

### 7.3 線上揪團功能 (Group Ordering)
* **邏輯設計**：
    - [ ] 實作「揪團房間」邏輯，利用 Redis Hash 儲存房間狀態。

---

## 8. 🏗️ 階段七：微服務拆分與治理 (Microservices Transformation)
* **執行計畫**：
    - [ ] 模組化單體重構 (Modular Monolith)。
    - [ ] 分散式治理 (Redisson, Resilience4j)。