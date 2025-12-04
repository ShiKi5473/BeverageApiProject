import http from 'k6/http';
import { check, sleep } from 'k6';
import { login } from '../utils/auth.js';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
    stages: [
        { duration: '30s', target: 10 },
        { duration: '1m', target: 50 },
        { duration: '30s', target: 0 },
    ],
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
    },
};

// 【優化 1】透過環境變數設定 URL，方便在 Docker/Local 切換
// 執行時可用 k6 run -e BASE_URL=http://host.docker.internal:8080 script.js
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

export function setup() {
    const brandId = 1;
    const username = '0911111111';
    const password = 'password123';

    // 建議這裡加個 try-catch 或檢查，如果 setup 登入失敗直接報錯，不要讓後面跑空測試
    try {
        const token = login(brandId, username, password);
        if (!token) throw new Error("Login failed, no token received");
        return { token };
    } catch (e) {
        console.error("Setup failed:", e);
        throw e; // 中斷測試
    }
}

export default function (data) {
    const token = data.token;
    if (!token) return;

    // 定義共用的 Header
    const headers = {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`,
    };

    // 1. Get Products
    // 【優化 2】加入 tags: { name: '...' } 讓 Grafana 圖表能區分這個請求
    const productsRes = http.get(`${BASE_URL}/brands/products/pos`, {
        headers: headers,
        tags: { name: 'GetProducts' }
    });

    check(productsRes, {
        'get products status is 200': (r) => r.status === 200,
        'products loaded': (r) => r.json().length > 0,
    });

    sleep(1);

    // 2. POS Checkout
    const idempotencyKey = uuidv4();

    const checkoutPayload = JSON.stringify({
        paymentMethod: 'CASH',
        pointsToUse: 0,
        items: [
            {
                productId: 1,
                quantity: 1,
                notes: 'K6 Test (Half Sugar, Less Ice)',
                optionIds: [11, 21]
            }
        ]
    });

    const checkoutParams = {
        headers: {
            ...headers,
            'Idempotency-Key': idempotencyKey,
        },
        tags: { name: 'PosCheckout' } // 【優化 2】加入 Tag
    };

    const checkoutRes = http.post(`${BASE_URL}/orders/pos-checkout`, checkoutPayload, checkoutParams);

    check(checkoutRes, {
        'checkout status is 201': (r) => r.status === 201,
        'order created': (r) => r.json('orderNumber') !== undefined,
    });

    sleep(2);
}