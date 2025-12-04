# K6 效能測試腳本

此目錄包含針對 Beverage API Project 的負載測試腳本。

## 前置需求

1. 安裝 [K6](https://k6.io/docs/get-started/installation/)。
2. 確保後端 API (`localhost:8080`) 與 Redis 已啟動。
3. 確保資料庫中有測試用的店員帳號 (預設腳本使用 `0988888888` / `password123`) 與商品 (ID: 1)。

## 執行測試

在專案根目錄下執行：

```bash
# 執行 POS 點餐流程測試
k6 run tests/k6/scenarios/pos_ordering_flow.js