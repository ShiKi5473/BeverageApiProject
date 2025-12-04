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

const BASE_URL = 'http://localhost:8080/api/v1';

export function setup() {
    const brandId = 1;
    const username = '0911111111';
    const password = 'password123';
    const token = login(brandId, username, password);
    return { token };
}

export default function (data) {
    const token = data.token;
    if (!token) return;

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
        },
    };

    // 1. Get Products
    const productsRes = http.get(`${BASE_URL}/brands/products/pos`, params);
    check(productsRes, {
        'get products status is 200': (r) => r.status === 200,
        'products loaded': (r) => r.json().length > 0,
    });

    sleep(1);

    // 2. POS Checkout
    const idempotencyKey = uuidv4();

    // 【修改】加入選項 ID (11=半糖, 21=少冰)
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
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`,
            'Idempotency-Key': idempotencyKey,
        },
    };

    const checkoutRes = http.post(`${BASE_URL}/orders/pos-checkout`, checkoutPayload, checkoutParams);

    check(checkoutRes, {
        'checkout status is 201': (r) => r.status === 201,
        'order created': (r) => r.json('orderNumber') !== undefined,
    });

    sleep(2);
}