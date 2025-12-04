# 🥤 Beverage POS & KDS System (多租戶飲料店點餐與廚房顯示系統)

這是一個專為手搖飲料店設計的企業級 **多租戶 (Multi-Tenant) POS 系統**。
專案採用前後端分離架構，後端基於 Spring Boot 實作 **模組化單體 (Modular Monolith)**，前端使用原生 JavaScript (Vite)。

系統整合了複雜的商品客製化（甜度冰塊）、會員點數機制、即時廚房顯示系統 (KDS)，並採用 **多語言持久化 (Polyglot Persistence)** 架構以應對高併發與大數據量的審計需求。

---

## 🚀 專案亮點與核心功能

### 1. 核心業務 (Core Business)
* **多租戶架構**：單一系統支援多個品牌 (Brand)，資料透過 `BrandContextHolder` 與 JWT 自動隔離。
* **高度客製化商品**：透過 `OptionGroup` 與 `ProductOption` 實現複雜的飲料客製化（如：半糖、少冰、加椰果）。
* **狀態模式訂單管理**：使用 **State Pattern** 管理訂單生命週期 (`PENDING` -> `PREPARING` -> `READY` -> `CLOSED`)，確保業務流轉嚴謹。
* **混合式庫存架構 (Hybrid DAO)**：
    * **讀取**：使用 JPA 處理複雜關聯。
    * **寫入**：使用 **JDBC Batch Update** 處理高併發庫存扣減 (FIFO)，大幅降低資料庫鎖定時間。

### 2. 即時互動與非同步 (Real-time & Async)
* **事件驅動 KDS**：訂單狀態變更時發布 Domain Event，透過 **RabbitMQ** 廣播，並利用 **SSE (Server-Sent Events)** 推送至廚房螢幕，無需輪詢。
* **非同步審計日誌**：關鍵操作 (如手動扣庫存、修改權限) 透過 AOP 攔截，並以 `@Async` 非同步寫入 **MongoDB**，實現操作軌跡全記錄。

### 3. 可靠性與效能 (Reliability & Performance)
* **分散式鎖**：使用 **ShedLock** 確保排程任務 (如日結報表) 在叢集環境中單一執行。
* **資料一致性**：庫存扣減採用 `PESSIMISTIC_WRITE` 悲觀鎖，經 **K6** 壓力測試驗證，在高併發搶購場景下無超賣。
* **檔案分片上傳**：整合 **MinIO** 物件儲存，支援大檔案分片上傳與斷點續傳。

---

## 🛠️ 技術棧 (Tech Stack)

### Backend (後端)
* **Language**: Java 21
* **Framework**: Spring Boot 3.x
* **Databases (Polyglot Persistence)**:
    * **PostgreSQL**: 核心關聯資料 (關聯查詢強)
    * **MongoDB**: 審計日誌 (Audit Log) (寫入吞吐量高、結構鬆散)
    * **Redis**: 快取、Session、分散式鎖、訂單流水號生成
* **Message Queue**: RabbitMQ (Fanout Exchange 廣播模式)
* **Object Storage**: MinIO (S3 Compatible)
* **Security**: Spring Security + JWT (雙層認證：平台管理員 vs 租戶員工)
* **Testing**: JUnit 5, Mockito, **Testcontainers**, **K6** (Load Testing)

### Frontend (前端)
* **Build Tool**: Vite
* **Core**: Vanilla JavaScript (ES Modules)
* **UI Components**: Google Material Web Components (MWC)
* **Charts**: Apache ECharts (報表視覺化)

---

## 📂 系統架構圖 (簡易)

```mermaid
graph TD
    Client[Client (POS/KDS)] <--> LB[Load Balancer]
    LB <--> App[Spring Boot Application]
    
    subgraph Data Layer
    App --> PG[(PostgreSQL)]
    App --> Mongo[(MongoDB - Audit)]
    App --> Redis[(Redis - Cache/Lock)]
    App --> MinIO[(MinIO - Files)]
    end
    
    subgraph Messaging
    App --> RMQ[RabbitMQ]
    RMQ --> App
    end
🚀 快速開始 (Quick Start)
前置需求
Docker & Docker Compose

Java 21 (若要本機執行)

啟動步驟
啟動基礎設施 (資料庫、訊息佇列、儲存服務)：

Bash

docker-compose up -d
啟動後端應用：

Bash

./mvnw spring-boot:run
系統啟動時，DataSeeder 會自動初始化測試用的品牌、分店、商品與庫存資料。

啟動前端：

Bash

cd frontend
npm install
npm run dev
預設測試帳號
品牌管理員: 0911111111 / password123

平台超級管理員: admin / admin123

🧪 測試與驗證
執行單元與整合測試
本專案使用 Testcontainers 啟動真實的 DB 環境進行測試：

Bash

./mvnw verify
執行 K6 壓力測試
驗證庫存併發扣減的正確性：

Bash

k6 run tests/k6/scenarios/inventory_stress.js
📝 開發藍圖 (Roadmap)
詳細開發進度請參閱 BLUEPRINT_2025_UPGRADE.md。

[x] Phase 1: 核心重構 (Facade, TSID, Hybrid DAO)

[x] Phase 2: 業務擴充 (促銷引擎, RBAC)

[x] Phase 3: 非同步與效能 (RabbitMQ, MinIO, MongoDB Audit, K6)

[ ] Phase 4: 即時互動 (WebSocket 線上揪團) - Next Step

[ ] Phase 5: 微服務拆分