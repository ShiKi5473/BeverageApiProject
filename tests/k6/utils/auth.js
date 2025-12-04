import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = 'http://localhost:8080/api/v1';

export function login(brandId, username, password) {
    const payload = JSON.stringify({
        brandId: brandId,
        username: username,
        password: password,
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
        },
    };

    const res = http.post(`${BASE_URL}/auth/login`, payload, params);

    check(res, {
        'login successful': (r) => r.status === 200,
        'has access token': (r) => r.json('accessToken') !== undefined,
    });

    if (res.status !== 200) {
        console.error(`Login failed: ${res.body}`);
        return null;
    }

    return res.json('accessToken');
}