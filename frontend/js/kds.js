// 檔案： frontend/js/kds.js (修改後)

import { getOrdersByStatus, updateOrderStatus } from "./api.js";
import { connectToWebSocket } from "./ws-client.js";

const MY_STORE_ID = localStorage.getItem("storeId");

document.addEventListener("DOMContentLoaded", () => {
    if (!MY_STORE_ID) {
        const errorMsg = "錯誤：找不到店家 ID (storeId)。KDS 無法啟動。\n將導回登入頁。";
        console.error(errorMsg);
        alert(errorMsg);
        window.location.href = "login.html";
        return; // 中斷執行
    }
    // DOM 元素
    const preparingListEl = document.getElementById("preparing-list");
    const pickupListEl = document.getElementById("pickup-list");
    const statusChip = document.getElementById("connection-status");

    /**
     * 1. 頁面載入時，抓取所有 "製作中" 和 "待取餐" 的訂單
     */
    async function loadInitialOrders() {
        try {
            // 平行抓取
            const [preparingOrders, pickupOrders] = await Promise.all([
                getOrdersByStatus(MY_STORE_ID, "PREPARING"),
                getOrdersByStatus(MY_STORE_ID, "READY_FOR_PICKUP")
            ]);

            preparingListEl.innerHTML = "";
            pickupListEl.innerHTML = "";

            preparingOrders.forEach(order => renderOrderCard(order, preparingListEl));
            pickupOrders.forEach(order => renderOrderCard(order, pickupListEl));

        } catch (error) {
            console.error("載入初始訂單失敗:", error);
            preparingListEl.innerHTML = `<p class="error">${error.message}</p>`;
        }
    }

    /**
     * 2. 渲染訂單卡片
     */
    function renderOrderCard(order, targetListElement) {
        const orderId = `kds-order-${order.orderId}`;

        // 避免重複渲染
        if (document.getElementById(orderId)) return;

        const card = document.createElement("md-filled-card");
        card.id = orderId;
        card.className = "kds-card";

        // 組合品項 HTML
        let itemsHtml = order.items.map(item => `
            <li>
                <strong>${item.productName} (x${item.quantity})</strong>
                ${item.options.length > 0 ?
            `<div class="kds-item-options">${item.options.map(opt => opt.optionName).join(", ")}</div>` : ''
        }
                ${item.notes ?
            `<div class="kds-item-notes">備註: ${item.notes}</div>` : ''
        }
            </li>
        `).join("");

        // 根據狀態決定是否顯示按鈕
        const buttonHtml = order.status === "PREPARING" ?
            `<md-filled-button class="kds-complete-btn" data-order-id="${order.orderId}" style="width: 100%; margin-top: 15px;">
                製作完成
            </md-filled-button>` :
            '';

        card.innerHTML = `
            <h3>#${order.orderNumber}</h3>
            <ul>${itemsHtml}</ul>
            ${buttonHtml}
        `;

        // 新訂單放在最前面
        targetListElement.prepend(card);
    }

    /**
     * 3. 處理 WebSocket 訊息
     */
    function handleKdsMessage(action, order) {
        const orderId = `kds-order-${order.orderId}`;
        const existingCard = document.getElementById(orderId);

        console.log("KDS 收到 WS 訊息:", action, order.orderNumber);

        if (action === "NEW_ORDER") {
            // 新訂單 -> 加入 "製作中"
            renderOrderCard(order, preparingListEl);

        } else if (action === "MOVE_TO_PICKUP") {
            // 製作完成 -> 從 "製作中" 移到 "待取餐"
            if (existingCard) {
                existingCard.remove(); // 從舊列表移除
            }
            renderOrderCard(order, pickupListEl); // 渲染到新列表

        } else if (action === "CANCEL_ORDER") {
            // 訂單取消
            if (existingCard) {
                existingCard.classList.add("cancelled");
                // 移除所有按鈕
                const btn = existingCard.querySelector("button");
                if (btn) btn.remove();
            }

        } else if (action === "REMOVE_FROM_PICKUP") {
            // 顧客已取餐
            if (existingCard) {
                existingCard.remove(); // 從 "待取餐" 移除
            }
        }
    }

    /**
     * 4. 處理 KDS 上的「製作完成」按鈕點擊
     */
    async function handleCompleteProduction(event) {
        const button = event.target.closest(".kds-complete-btn");
        if (!button) return;

        const orderId = button.dataset.orderId;
        button.disabled = true;
        button.textContent = "傳送中...";

        try {
            // 【關鍵】呼叫 API，將狀態從 PREPARING -> READY_FOR_PICKUP
            await updateOrderStatus(orderId, "READY_FOR_PICKUP");

            // 成功！
            // 我們不需要手動移動卡片，因為後端會發布 "MOVE_TO_PICKUP" 事件，
            // handleKdsMessage() 會自動處理 UI 更新

        } catch (error) {
            console.error("更新訂單為 READY_FOR_PICKUP 失敗:", error);
            alert(`訂單 ${orderId} 更新失敗: ${error.message}`);
            button.disabled = false;
            button.textContent = "製作完成";
        }
    }

    /**
     * 5. 啟動 WebSocket 連線
     */
    function startWebSocket() {
        connectToWebSocket(
            MY_STORE_ID,
            // onMessage
            (action, payload) => handleKdsMessage(action, payload),
            // onConnect
            () => {
                // 【修改】更新 <md-chip>
                statusChip.label = `已連線 (店家 ${MY_STORE_ID})`;
                statusChip.classList.remove("status-disconnected");
                statusChip.classList.add("status-connected");
            },
            // onError
            (error) => {
                // 【修改】更新 <md-chip>
                statusChip.label = `連線中斷: ${error} (5秒後重試)`;
                statusChip.classList.remove("status-connected");
                statusChip.classList.add("status-disconnected");
            }
        );
    }

    // --- 啟動程序 ---

    // 1. 綁定按鈕點擊 (使用事件委派)
    preparingListEl.addEventListener("click", handleCompleteProduction);

    // 2. 載入初始訂單
    loadInitialOrders();

    // 3. 啟動 WS
    startWebSocket();
});