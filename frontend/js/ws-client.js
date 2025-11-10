// 檔案： frontend/js/ws-client.js (新檔案)

// 引入 Stomp 和 SockJS (請確保 HTML 中已引入)
// <script src="https://cdnjs.cloudflare.com/ajax/libs/sockjs-client/1.6.1/sockjs.min.js"></script>
// <script src="https://cdnjs.cloudflare.com/ajax/libs/stomp.js/2.3.3/stomp.min.js"></script>

/**
 * 連線到 WebSocket 服務
 * @param {number} storeId - 要監聽的店家 ID
 * @param {function} onMessageCallback - 收到訊息時的回呼 (action, payload)
 * @param {function} onConnectCallback - 連線成功時的回呼
 * @param {function} onErrorCallback - 連線失敗/中斷時的回呼
 */
export function connectToWebSocket(storeId, onMessageCallback, onConnectCallback, onErrorCallback) {
    const token = localStorage.getItem("accessToken");
    if (!token) {
        console.error("WS Client: 缺少 Token");
        onErrorCallback("缺少 Token，請先登入");
        return;
    }

    const socket = new SockJS("http://localhost:8080/ws-kds");
    const stompClient = Stomp.over(socket);
    stompClient.debug = null; // 設為 console.log 可看詳細日誌

    const headers = {
        "Authorization": `Bearer ${token}`
    };

    stompClient.connect(headers,
        // On Connect
        (frame) => {
            if (onConnectCallback) onConnectCallback();

            // 訂閱 KDS 主題
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
        },
        // On Error
        (error) => {
            console.error("WS 連線失敗:", error);
            if (onErrorCallback) onErrorCallback(error);
            // 啟用重連機制
            setTimeout(() => {
                connectToWebSocket(storeId, onMessageCallback, onConnectCallback, onErrorCallback);
            }, 5000); // 5 秒後重試
        }
    );
}