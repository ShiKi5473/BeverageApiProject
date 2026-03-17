# 專案簡介 (Project Mission)
這是一個飲料店的後端 API 系統 (BeverageApiProject)，包含了 POS 系統、KDS (廚房排單系統)、庫存管理 (Inventory)、訂單處理 (Order)、促銷與會員點數 (Promotion/Member) 以及報表 (Report) 等核心模組。

# 技術堆疊 (Tech Stack)
- 語言與框架：Java 21, Spring Boot 3.x
- 構建工具：Maven
- 資料庫與版本控制：PostgreSQL / MySQL, Flyway
- 容器化：Docker, Docker Compose
- 前端 (部分呈現)：HTML, CSS, 原生 JavaScript

# AI 開發與協作規範 (Agent Rules)
1. 架構原則：嚴格遵守現有的專案結構，依循 `Controller` -> `Service` / `Facade` -> `Repository` 的分層架構進行開發。
2. 資料庫修改：如果任務需要修改資料庫欄位，**絕對不可**直接修改歷史的 `V*__*.sql` 檔案，必須在 `src/main/resources/db/migration/` 目錄下建立新的 Flyway 遷移腳本（Migration Script）。
3. 指令執行限制：
   - 專案建置與測試請一律使用專案內建的 Wrapper 指令，例如：`./mvnw clean install`。
   - 執行任何破壞性指令（如 `docker-compose down -v` 或刪除檔案）前，必須先設定為「Request Review (請求審查)」並徵求我的同意。
4. 程式碼風格：遵循 Java 標準命名規範（CamelCase），所有新的 API 端點都必須加上適當的 JavaDoc 與權限驗證邏輯（如 `@Audit` 等註解）。
5. 思考與除錯 (Deep Think)：遇到 Error 時，請先自主讀取日誌 (Logs) 並進行自我修正，若連續失敗兩次，再中斷任務向我回報。