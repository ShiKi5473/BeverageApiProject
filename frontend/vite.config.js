// 檔案： frontend/vite.config.js
import { defineConfig } from 'vite';

export default defineConfig({
    server: {
        // 我們在開發時，前端伺服器會開在 5173 (Vite 預設)
        port: 5173,
        proxy: {
            // 1. 代理 API 請求：
            // 任何 /api 開頭的請求 (例如 /api/v1/orders)
            // 都會被自動轉發到 http://localhost:8080/api/v1/orders
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true,
            },
            // 2. 代理 WebSocket 請求：
            // 任何 /ws-kds 開頭的請求
            // 都會被自動轉發到 http://localhost:8080/ws-kds
            '/ws-kds': {
                target: 'http://localhost:8080',
                ws: true, // 啟用 WebSocket 代理
            },
        },
    },
    build: {
        // 讓 Vite 知道我們的多頁面入口
        rollupOptions: {
            input: {
                login: 'frontend/pages/login.html',
                pos: 'frontend/pages/pos.html',
                checkout: 'frontend/pages/checkout.html',
                kds: 'frontend/pages/kds.html',
            }
        }
    }
});