🥤 Beverage POS & KDS System (多租戶飲料店點餐與廚房顯示系統)

  這是一個專為手搖飲料店設計的 多租戶 (Multi-Tenant) POS 系統。專案採用前後端分離架構，後端基於 Spring Boot，前端使用原生 JavaScript (配合 Vite 打包)。系統整合了訂單管理、複雜的商品選項（如甜度冰塊）、會員點數機制，以及基於 Redis 與 SSE 的即時廚房顯示系統 (KDS)。

🚀 專案特色
  
  多租戶架構 (Multi-Tenancy)：

    支援單一系統管理多個品牌 (Brand) 與其下屬分店 (Store)。

    透過 JwtAuthenticationFilter 解析 Token 中的 brandId，實現租戶資料隔離。

  複雜商品選項：

    支援飲料店特有的客製化需求（如：半糖、少冰、加料），透過 OptionGroup 與 ProductOption 靈活配置。

  狀態模式訂單管理 (State Pattern)：

    使用設計模式管理訂單生命週期 (PENDING -> PREPARING -> READY_FOR_PICKUP -> CLOSED / CANCELLED)，確保業務邏輯嚴謹且易於維護。

  即時 KDS (廚房顯示系統)：

    利用 Redis Pub/Sub 與 Server-Sent Events (SSE) 實現即時通訊。

    前台點餐後，透過事件驅動機制，後廚螢幕自動跳出新訂單。

  會員與點數機制：

    內建會員系統，支援消費累積點數與結帳折抵。

    使用悲觀鎖 (PESSIMISTIC_WRITE) 處理並發請求，確保點數扣抵的資料一致性。

  安全性：

    基於 Spring Security 與 JWT 的雙層認證機制（平台管理員 vs. 品牌員工）。

🛠️ 技術棧 (Tech Stack)
  Backend (後端)
    Language: Java 21
    
    Framework: Spring Boot 3.x
    
    Database: PostgreSQL (資料持久化)
    
    Cache & Messaging: Redis (用於生成訂單流水號、KDS 事件廣播)
    
    Security: Spring Security, JWT (JSON Web Token)
    
    Real-time: Server-Sent Events (SSE)
    
    Architecture: MVC, Layered Architecture, DDD concepts (Domain Events)

  Frontend (前端)
    Build Tool: Vite
    
    Core: Vanilla JavaScript (ES Modules)
    
    UI Components: Google Material Web Components (MWC)
    
    Styling: CSS3 (Grid/Flexbox)

📂 系統架構設計亮點
  訂單狀態機 (Order State Machine)：

    定義了 OrderState 介面，針對不同狀態 (PendingState, PreparingState, HeldState 等) 實作具體的行為（如 processPayment, complete, cancel）。

    避免了巨型的 if-else 判斷，提高程式碼的可讀性與擴充性。

  KDS 事件驅動 (Event-Driven KDS)：

    當訂單狀態改變時，發布 OrderStateChangedEvent。

    KdsService 監聽事件，根據狀態選擇對應的策略 (KdsEventStrategy) 生成訊息，並透過 Redis 發送廣播。

    前端透過 SSE 訂閱特定 Store 的頻道，實現無刷新更新。

  資料一致性與防護：

    使用 PESSIMISTIC_WRITE 鎖 (select for update) 防止會員點數操作的 Race Condition。

    Redis INCR 原子操作生成每日不重複的訂單流水號，並設定過期時間以節省記憶體。

    實作簡易的 XSS 防護，過濾使用者輸入的備註欄位。
