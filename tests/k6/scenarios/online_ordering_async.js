import http from 'k6/http';
import { check, sleep } from 'k6';
import { login } from '../utils/auth.js';
// 使用 k6-utils 生成 UUID
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
    scenarios: {
        // 模擬「搶購」場景：流量快速攀升
        async_rush: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            preAllocatedVUs: 50,
            maxVUs: 300, // 模擬最多 300 個併發使用者
            stages: [
                { target: 50, duration: '30s' },  // 暖身階段
                { target: 200, duration: '1m' },  // 高峰期：每秒 200 個請求 (200 RPS)
                { target: 200, duration: '30s' }, // 持續高峰
                { target: 0, duration: '30s' },   // 冷卻
            ],
        },
    },
    thresholds: {
        // 核心驗證指標：
        // 因為是 Async (只丟 Queue 不寫 DB)，API 回應應該極快
        // 設定 95% 的請求必須在 100ms 內完成
        'http_req_duration{name:AsyncOrder}': ['p(95)<100'],

        // 錯誤率必須低於 1%
        'http_req_failed': ['rate<0.01'],
    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

export function setup() {
    const brandId = 1;
    // 使用測試帳號登入 (DataSeeder 預設)
    const token = login(brandId, '0911111111', 'password123');
    if (!token) throw new Error("Login failed");
    return { token };
}

export default function (data) {
    const token = data.token;

    // 準備訂單資料
    const payload = JSON.stringify({
        status: 'PENDING',
        items: [
            {
                productId: 1, // 假設商品 ID 1 存在
                quantity: 1,
                notes: 'K6 Async Stress Test',
                optionIds: []
            }
        ]
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
            // 每個請求都帶上唯一的 Idempotency-Key，模擬真實用戶行為
            'Idempotency-Key': uuidv4(),
        },
        // 為此請求加上標籤，方便在 Grafana 或 Thresholds 中區分
        tags: { name: 'AsyncOrder' }
    };

    const url = `${BASE_URL}/online-orders`;

    const res = http.post(url, payload, params);

    // 驗證回應
    // 預期：非同步架構應立即回傳 202 Accepted，而非 201 Created
    check(res, {
        'status is 202 (Accepted)': (r) => r.status === 202,
        'has ticketId': (r) => r.json('ticketId') !== undefined,
        'status is QUEUED': (r) => r.json('status') === 'QUEUED',
    });
}