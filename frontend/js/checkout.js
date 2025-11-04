// 匯入我們需要的 API 函式
import { getOrderDetails, processPayment } from "./api.js";

document.addEventListener("DOMContentLoaded", async () => {
    // 1. 取得頁面元素
    const loadingMask = document.getElementById("loading-mask");
    const checkoutContent = document.getElementById("checkout-content");
    const orderNumberEl = document.getElementById("order-number");
    const totalAmountEl = document.getElementById("total-amount");
    const paymentButtonsContainer = document.querySelector(".option-buttons-container");
    const confirmPaymentButton = document.getElementById("confirm-payment-button");
    const cancelButton = document.getElementById("cancel-button");
    const pointsToUseInput = document.getElementById("points-to-use");
    // (未來可加入會員查詢按鈕)

    let currentOrderId = null;
    let selectedMemberId = null;
    let selectedPaymentMethod = null;

    // 2. 從 URL 讀取 OrderID
    try {
        const params = new URLSearchParams(window.location.search);
        const orderId = params.get("orderId");

        if (!orderId) {
            throw new Error("缺少訂單 ID");
            // TODO 警告:(26, 13) 本地捕获异常的 'throw'
        }
        currentOrderId = Number(orderId);

        // 3. 透過 API 取得訂單資料
        const order = await getOrderDetails(currentOrderId);

        // 4. 將資料填充到頁面
        orderNumberEl.textContent = order.orderNumber;
        totalAmountEl.textContent = `NT$ ${order.totalAmount}`;

        // 載入完成，顯示內容
        loadingMask.style.display = "none";
        checkoutContent.style.display = "block";

    } catch (error) {
        alert(`載入訂單失敗: ${error.message}。即將返回點餐頁。`);
        window.location.href = "pos.html";
        return;
    }

    // 5. 綁定付款方式按鈕
    paymentButtonsContainer.addEventListener("click", (e) => {
        if (e.target.classList.contains("payment-btn")) {
            paymentButtonsContainer
                .querySelectorAll(".payment-btn")
                .forEach((btn) => btn.classList.remove("active"));
            e.target.classList.add("active");
            selectedPaymentMethod = e.target.dataset.method;
        }
    });

    // 6. 綁定 "確認付款" 按鈕
    confirmPaymentButton.addEventListener("click", async () => {
        if (!selectedPaymentMethod) {
            alert("請選擇付款方式！");
            return;
        }

        // 鎖定按鈕，防止連點
        confirmPaymentButton.disabled = true;
        confirmPaymentButton.textContent = "付款處理中...";

        const pointsToUse = pointsToUseInput.valueAsNumber || 0;

        const paymentData = {
            memberId: selectedMemberId, // 目前會員查詢功能還沒串，所以會是 null
            pointsToUse: pointsToUse,
            paymentMethod: selectedPaymentMethod,
        };

        try {
            const paidOrder = await processPayment(currentOrderId, paymentData);
            alert(
                `付款成功！ 訂單 ${paidOrder.orderNumber} 狀態已更新為 ${paidOrder.status}`
            );
            // 付款成功，跳回點餐頁
            window.location.href = "pos.html";

        } catch (error) {
            alert(`付款失敗: ${error.message}`);
            // 解鎖按鈕
            confirmPaymentButton.disabled = false;
            confirmPaymentButton.textContent = "確認付款";
        }
    });

    // 7. 綁定 "取消" 按鈕
    cancelButton.addEventListener("click", () => {
        if (confirm("確定要取消結帳並返回點餐頁嗎？ (此訂單將維持 PENDING 狀態)")) {
            window.location.href = "pos.html";
        }
    });
});