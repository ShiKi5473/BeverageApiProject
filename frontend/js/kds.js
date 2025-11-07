// 檔案： frontend/js/kds.js (新檔案)
document.addEventListener("DOMContentLoaded", () => {
    const orderWall = document.getElementById("order-wall");
    const statusEl = document.getElementById("connection-status");

    // !!! 假設 KDS 螢幕是給 "店家 1" 用的
    const MY_STORE_ID = 1;

    // 1. 從 localStorage 取得 JWT (必須先登入 POS)
    const token = localStorage.getItem("accessToken");
    if (!token) {
        statusEl.textContent = "錯誤：請先登入 POS 系統取得 Token";
        return;
    }

    function connect() {
        statusEl.textContent = "連線中...";

        // 2. 建立 SockJS 連線 (指向 WebSocketConfig 的端點)
        const socket = new SockJS("http://localhost:8080/ws-kds");
        const stompClient = Stomp.over(socket);
        stompClient.debug = null; // (設為 console.log 可看詳細日誌)

        // 3. 【關鍵】設定 STOMP 連線標頭，帶入 JWT
        const headers = {
            "Authorization": `Bearer ${token}`
        };

        stompClient.connect(headers,
            // 4. 連線成功
            (frame) => {
                statusEl.textContent = "已連線 (KDS - Store 1)";
                statusEl.style.color = "green";

                // 5. 【關鍵】訂閱 "我這家店" 的主題
                const topic = `/topic/kds/store/${MY_STORE_ID}`;
                console.log("訂閱:", topic);

                stompClient.subscribe(topic, (message) => {
                    // 6. 收到訊息時的處理
                    const kdsMessage = JSON.parse(message.body);
                    handleKdsMessage(kdsMessage.action, kdsMessage.payload);
                });
            },
            // 7. 連線失敗
            (error) => {
                console.error("連線失敗:", error);
                statusEl.textContent = "連線失敗，5 秒後重試";
                statusEl.style.color = "red";
                setTimeout(connect, 5000); // 5秒後重試
            }
        );
    }

    function handleKdsMessage(action, order) {
        const orderId = `order-${order.orderId}`;
        let card = document.getElementById(orderId);

        if (action === "NEW_ORDER") {
            if (card) return; // (如果重複收到，忽略)

            card = document.createElement("div");
            card.id = orderId;
            card.className = "order-card";

            let itemsHtml = order.items.map(item =>
                `<li>${item.productName} (x${item.quantity})</li>`
            ).join("");

            card.innerHTML = `
                <h3>#${order.orderNumber}</h3>
                <ul>${itemsHtml}</ul>
            `;
            orderWall.prepend(card); // 新訂單放在最前面

        } else if (action === "COMPLETE_ORDER") {
            if (!card) return;
            card.classList.add("completed");
            card.innerHTML += "<p><strong>已完成</strong></p>";

        } else if (action === "CANCEL_ORDER") {
            if (!card) return;
            card.classList.add("cancelled");
            card.innerHTML += "<p><strong>已取消</strong></p>";
        }
    }

    // 啟動連線
    connect();
});