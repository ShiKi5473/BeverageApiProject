import http from 'k6/http';
import { check, sleep } from 'k6';
import { login } from '../utils/auth.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// --- 設定測試參數 ---
export const options = {
    // 定義測試階段 (Stages)
    stages: [
        { duration: '30s', target: 10 }, // 暖身：30秒內爬升到 10 個虛擬使用者 (VUs)
        { duration: '1m', target: 50 },  // 負載：維持 50 個 VUs 跑 1 分鐘 (模擬尖峰時段)
        { duration: '30s', target: 0 },  // 冷卻：30秒內降回 0
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'], // 95% 的請求必須在 500ms 內完成
        http_req_failed: ['rate<0.01'],   // 錯誤率必須低於 1%
    },
};

const BASE_URL = 'http://localhost:8080/api/v1';

// --- 初始化 (Setup) ---
// 在測試開始前執行一次，用於獲取 Token
export function setup() {
    // 假設我們用 DataSeeder 建立的預設帳號，或您資料庫中已有的店員帳號
    // 請根據實際環境修改
    const brandId = 1;
    const username = '0911111111';
    const password = 'password123';

    const token = login(brandId, username, password);
    return { token };
}

// --- 虛擬使用者邏輯 (VU Code) ---
export default function (data) {
    const token = data.token;
    if (!token) return;

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
        },
    };

    // 1. 讀取商品列表 (GET /products/pos)
    // 這一步驗證 Redis 快取是否生效
    const productsRes = http.get(`${BASE_URL}/brands/products/pos`, params);

    check(productsRes, {
        'get products status is 200': (r) => r.status === 200,
        'products loaded': (r) => r.json().length > 0,
    });

    // 模擬店員思考與操作時間
    sleep(1);

    // 2. 執行 POS 結帳 (POST /orders/pos-checkout)
    // 這一步驗證 Idempotency 機制
    const idempotencyKey = uuidv4(); // 為每個請求生成唯一 Key

    const checkoutPayload = JSON.stringify({
        paymentMethod: 'CASH',
        pointsToUse: 0,
        items: [
            {
                productId: 1, // 請確保資料庫有此商品 ID
                quantity: 1,
                notes: 'K6 Load Test',
                optionIds: [1, 3]
            }
        ]
    });

    const checkoutParams = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
            'Idempotency-Key': idempotencyKey, // 加入冪等性 Key
        },
    };

    const checkoutRes = http.post(`${BASE_URL}/orders/pos-checkout`, checkoutPayload, checkoutParams);

    check(checkoutRes, {
        'checkout status is 201': (r) => r.status === 201,
        'order created': (r) => r.json('orderNumber') !== undefined,
    });

    // 3. 重複發送相同的請求 (測試冪等性攔截)
    // 理論上應該被後端攔截並回傳 200 (或是我們定義的錯誤)，但因為 IdempotencyAspect 實作是拋出異常，
    // 這裡可以預期 400 或相關錯誤，視您的 IdempotencyAspect 實作細節而定。
    const retryRes = http.post(`${BASE_URL}/orders/pos-checkout`, checkoutPayload, checkoutParams);
    check(retryRes, {
        'retry blocked or handled': (r) => r.status === 400 || r.status === 409,
    });


    sleep(2);
}