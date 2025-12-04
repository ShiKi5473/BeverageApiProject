import http from 'k6/http';
import { check, sleep } from 'k6';
import { login } from '../utils/auth.js';

export const options = {
    scenarios: {
        inventory_rush: {
            executor: 'ramping-arrival-rate',
            startRate: 10,
            timeUnit: '1s',
            preAllocatedVUs: 50,
            maxVUs: 100,
            stages: [
                { target: 50, duration: '1m' }, // 快速拉升到 50 RPS (模擬搶購)
                { target: 50, duration: '1m' },
                { target: 0, duration: '30s' },
            ],
        },
    },
    thresholds: {
        'http_req_failed{status:500}': ['rate<0.01'],
        http_req_duration: ['p(95)<1000'], // 95% 請求應在 1秒內完成 (考慮到 DB Lock)

    },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/api/v1';

export function setup() {
    const brandId = 1;
    // 使用 DataSeeder 建立的帳號
    const token = login(brandId, '0911111111', 'password123');
    if (!token) throw new Error("Login failed");
    return { token };
}

export default function (data) {
    const token = data.token;

    // 這裡我們直接打 "手動扣庫存" 的測試 API (InventoryController)
    // 或是打 "建立訂單" API
    // 為了單純測庫存鎖效能，我們打手動扣減 (itemId = 1)
    const storeId = 1;
    const itemId = 1;
    const qty = 1;

    const url = `${BASE_URL}/stores/${storeId}/inventory/${itemId}/deduct?quantity=${qty}`;

    const params = {
        headers: {
            'Authorization': `Bearer ${token}`,
            'Content-Type': 'application/json',
        },
        tags: { name: 'InventoryDeduct' }
    };

    const res = http.post(url, null, params);

    if (res.status !== 200 && res.status !== 400) {
        // 只印出前幾次失敗，避免洗版
        if (__ITER < 3) {
            console.error(`Request failed. Status: ${res.status}, Body: ${res.body}`);
        }
    }

    check(res, {
        'status is 200 (success) or 400 (out of stock)': (r) => r.status === 200 || r.status === 400,
    });
}