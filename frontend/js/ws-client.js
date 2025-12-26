import Stomp from 'stompjs';
import SockJS from 'sockjs-client'
import { guestLogin } from './api.js';

/**
 * 連線到 WebSocket 服務 (支援自動訪客登入)
 * 改為 async 函式以支援 await guestLogin
 */
export async function connectToWebSocket(storeId, onMessageCallback, onConnectCallback, onErrorCallback) {
    let token = localStorage.getItem("accessToken");

    // 1. 如果沒有 Token，執行訪客登入流程
    if (!token) {
        // 簡單使用 prompt 詢問 (實務上可用 Modal 優化 UI)
        const guestName = prompt("您目前未登入。請輸入暱稱以加入點餐：", "訪客");

        if (!guestName) {
            if (onErrorCallback) onErrorCallback("使用者取消訪客登入，無法連線");
            return;
        }

        try {
            const res = await guestLogin(guestName);
            token = res.accessToken;
            localStorage.setItem("accessToken", token);
            // 訪客 Token 已存入，後續 API 呼叫可使用
        } catch (e) {
            console.error("訪客登入失敗:", e);
            if (onErrorCallback) onErrorCallback("訪客登入失敗，無法建立連線");
            return;
        }
    }

    // 2. 建立連線 (使用 SockJS 與 Stomp)
    const socket = new SockJS("http://localhost:8080/ws-kds");
    const stompClient = Stomp.over(socket);
    // stompClient.debug = null; // 開發階段建議註解掉這行

    const headers = {
        "Authorization": `Bearer ${token}`
    };

    stompClient.connect(headers,
        // On Connect
        (frame) => {
            console.log("WebSocket 已連線:", frame);
            if (onConnectCallback) onConnectCallback();

            // 訂閱 KDS 主題 (廣播)
            const topic = `/topic/kds/store/${storeId}`;
            stompClient.subscribe(topic, (message) => {
                try {
                    const kdsMessage = JSON.parse(message.body);
                    if (onMessageCallback) {
                        onMessageCallback(kdsMessage.action, kdsMessage.payload);
                    }
                } catch (e) {
                    console.error("WS 訊息解析失敗:", e);
                }
            });

            // 訂閱個人通知頻道 (例如：訂單完成通知)
            stompClient.subscribe('/user/queue/orders', (message) => {
                console.log("收到個人通知:", message.body);
                // TODO: 這裡可以觸發 Toast 通知
            });
        },
        // On Error
        (error) => {
            console.error("WS 連線失敗:", error);
            // 處理 Token 過期 (401/403)
            const errorMsg = typeof error === 'string' ? error : error.headers?.message;
            if (errorMsg && (errorMsg.includes("401") || errorMsg.includes("403") || errorMsg.includes("Unauthorized"))) {
                localStorage.removeItem("accessToken");
            }

            if (onErrorCallback) onErrorCallback(error);
        }
    );
}