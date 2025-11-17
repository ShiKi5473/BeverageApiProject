import '@material/web/iconbutton/icon-button.js';
import '@material/web/icon/icon.js';

// 2. 為了 checkout.html 頁面本身
import '@material/web/textfield/filled-text-field.js';
import '@material/web/button/filled-tonal-button.js';
import '@material/web/chips/chip-set.js';
import '@material/web/chips/filter-chip.js';
import '@material/web/button/outlined-button.js';
import '@material/web/button/filled-button.js';
import '@material/web/button/text-button.js';

// 匯入 API
import {
    getOrderDetails,
    processPayment,
    findMemberByPhone
} from "./api.js";
import { createNavbar } from "./components/Navbar.js";

const paymentMethodChips = document.getElementById("payment-method-chips");

document.addEventListener("DOMContentLoaded", async () => {

    // --- 1. 狀態變數 ---
    let currentOrder = null;
    let currentMember = null;
    let selectedPaymentMethod = null;
    let originalTotalAmount = 0; // 訂單原始小計
    let pointsDiscount = 0; // 點數折抵金額
    let finalAmount = 0; // 最終應付金額
    let cashReceived = 0; // 實收現金

    // --- 2. 取得 DOM 元素 ---
    const layoutEl = document.getElementById("checkout-layout");
    const mainEl = document.getElementById("checkout-main");
    const loadingMask = document.getElementById("loading-mask"); // (您 HTML 中沒有，但邏輯保留)
    const checkoutContent = document.getElementById("checkout-main"); // (改為抓 main)

    // 左欄
    const itemCountEl = document.getElementById("item-count");
    const itemsListEl = document.getElementById("order-items-list");
    const originalTotalEl = document.getElementById("original-total");
    const discountRowEl = document.getElementById("discount-row");
    const discountAmountEl = document.getElementById("discount-amount");
    const finalTotalEl = document.getElementById("final-total");

    // 中欄
    const memberPhoneInput = document.getElementById("member-phone");
    const findMemberBtn = document.getElementById("find-member-btn");
    const memberDisplayEl = document.getElementById("member-display");
    const memberNameEl = document.getElementById("member-name");
    const memberPointsBalanceEl = document.getElementById("member-points-balance");
    const pointsToUseInput = document.getElementById("points-to-use");
    const pointsErrorEl = document.getElementById("points-error");
    const memberInfoTextEl = document.getElementById("member-info-text");

    // 右欄
    const paymentButtonsContainer = document.querySelector(".option-buttons-container");
    const cashCalculatorEl = document.getElementById("cash-calculator");
    const calcDisplayReceivedEl = document.getElementById("calc-display-received");
    const calcDisplayChangeEl = document.getElementById("calc-display-change");
    const calculatorGrid = document.querySelector(".calculator-grid");
    const confirmPaymentButton = document.getElementById("confirm-payment-button");
    const cancelButton = document.getElementById("cancel-button");
    const confirmBtnTextEl = document.getElementById("confirm-btn-text");
    const confirmBtnAmountEl = document.getElementById("confirm-btn-amount");

    // --- 3. 初始化 ---

    // 注入 Navbar
    const handleLogout = () => {
        localStorage.removeItem("accessToken");
        localStorage.removeItem("brandId");
        window.location.href = "login.html";
    };
    const navbar = createNavbar("結帳系統", handleLogout);
    layoutEl.insertBefore(navbar, mainEl);

    // --- 4. 核心功能函式 ---

    /**
     * 載入訂單資料並渲染頁面
     */
    async function loadOrderData() {
        try {
            const params = new URLSearchParams(window.location.search);
            const orderId = params.get("orderId");

            if (!orderId) {
                throw new Error("缺少訂單 ID");
            }

            const order = await getOrderDetails(Number(orderId));
            currentOrder = order;
            originalTotalAmount = order.totalAmount;

            // 填充左欄 (訂單明細)
            itemCountEl.textContent = order.items.length;
            renderOrderItems(order.items);

            // 更新所有金額顯示
            updateAllTotals();

            checkoutContent.style.display = "grid"; // (顯示三欄)
            // loadingMask.style.display = "none"; // (如果您有 loading mask)

        } catch (error) {
            alert(`載入訂單失敗: ${error.message}。即將返回點餐頁。`);
            window.location.href = "pos.html";
        }
    }

    /**
     * 渲染訂單品項列表 (左欄)
     */
    function renderOrderItems(items) {
        itemsListEl.innerHTML = ""; // 清空
        if (!items || items.length === 0) {
            itemsListEl.innerHTML = "<p>訂單中沒有品項</p>";
            return;
        }
        items.forEach(item => {
            const optionsStr = item.options.map(opt => opt.optionName).join(", ");
            const itemEl = document.createElement("div");
            itemEl.className = "checkout-item";
            itemEl.innerHTML = `
        <div class="checkout-item-details">
          <span class="checkout-item-name">${item.productName} (x${item.quantity})</span>
          ${optionsStr ? `<span class="checkout-item-options">${optionsStr}</span>` : ''}
        </div>
        <span class="checkout-item-subtotal">NT$ ${item.subtotal}</span>
      `;
            itemsListEl.appendChild(itemEl);
        });
    }

    /**
     * 處理會員查詢 (中欄)
     */
    async function handleFindMember() {
        const phone = memberPhoneInput.value;
        if (!phone) {
            alert("請輸入手機號碼");
            return;
        }

        findMemberBtn.disabled = true;
        findMemberBtn.textContent = "查詢中...";
        memberInfoTextEl.textContent = "";

        try {
            const member = await findMemberByPhone(phone);
            if (member) {
                currentMember = member;
                memberNameEl.textContent = `會員: ${member.fullName}`;
                memberPointsBalanceEl.textContent = member.totalPoints;
                pointsToUseInput.max = member.totalPoints;
                memberDisplayEl.style.display = "block";
                pointsToUseInput.value = 0; // 重設
            } else {
                currentMember = null;
                memberDisplayEl.style.display = "none";
                memberInfoTextEl.textContent = "查無此會員";
            }
            updateAllTotals(); // 查詢後更新總額
        } catch (error) {
            alert(error.message);
            memberInfoTextEl.textContent = "查詢失敗";
        } finally {
            findMemberBtn.disabled = false;
            findMemberBtn.textContent = "查詢";
        }
    }

    /**
     * (核心) 更新所有金額顯示
     * 1. 計算點數折抵
     * 2. 計算最終金額
     * 3. 更新左欄、右欄按鈕、計算機
     */
    function updateAllTotals() {
        let pointsToUse = pointsToUseInput.valueAsNumber || 0;

        // 檢查點數
        if (currentMember && pointsToUse > currentMember.totalPoints) {
            pointsToUse = currentMember.totalPoints;
            pointsToUseInput.value = pointsToUse;
            pointsErrorEl.textContent = "已達上限";
        } else {
            pointsErrorEl.textContent = "";
        }

        // 規則：10 點折 1 元 (來自 MemberPointService.java)
        pointsDiscount = Math.floor(pointsToUse / 10);
        finalAmount = originalTotalAmount - pointsDiscount;

        // 更新左欄
        originalTotalEl.textContent = `NT$ ${originalTotalAmount}`;
        finalTotalEl.textContent = `NT$ ${finalAmount}`;
        if (pointsDiscount > 0) {
            discountAmountEl.textContent = `- NT$ ${pointsDiscount}`;
            discountRowEl.style.display = "flex";
        } else {
            discountRowEl.style.display = "none";
        }

        // 更新右欄結帳按鈕
        confirmBtnAmountEl.textContent = `NT$ ${finalAmount}`;

        // 更新計算機
        updateCalculatorDisplay();
    }

    /**
     * 更新現金計算機顯示 (右欄)
     */
    function updateCalculatorDisplay() {
        calcDisplayReceivedEl.textContent = `$${cashReceived}`;
        const change = cashReceived - finalAmount;

        if (change >= 0) {
            calcDisplayChangeEl.textContent = `$${change}`;
            calcDisplayChangeEl.style.color = "#28a745"; // 綠色
        } else {
            calcDisplayChangeEl.textContent = `-$${-change}`;
            calcDisplayChangeEl.style.color = "#dc3545"; // 紅色
        }

        // 更新結帳按鈕狀態
        updateConfirmButtonState();
    }

    /**
     * 處理計算機按鈕點擊 (右欄)
     */
    function handleCalculatorClick(event) {
        const button = event.target.closest(".calc-btn");
        if (!button) return;

        const val = button.dataset.val;
        const fastVal = button.dataset.fast;

        if (fastVal) {
            // 快速鍵
            cashReceived = Number(fastVal);
        } else if (val === 'clear') {
            // 清除
            cashReceived = 0;
        } else if (val === 'exact') {
            // 剛好
            cashReceived = finalAmount;
        } else {
            // 數字鍵
            let currentStr = String(cashReceived);
            if (currentStr === '0') currentStr = '';
            currentStr += val;
            cashReceived = Number(currentStr);
        }

        updateCalculatorDisplay();
    }

    /**
     * 更新主結帳按鈕的狀態
     */
    function updateConfirmButtonState() {
        if (!selectedPaymentMethod) {
            confirmBtnTextEl.textContent = "請選擇付款方式";
            confirmPaymentButton.disabled = true;
            return;
        }

        if (selectedPaymentMethod === "CASH") {
            if (cashReceived < finalAmount) {
                confirmBtnTextEl.textContent = "金額不足";
                confirmPaymentButton.disabled = true;
            } else {
                confirmBtnTextEl.textContent = "現金結帳";
                confirmPaymentButton.disabled = false;
            }
        } else if (selectedPaymentMethod === "CREDIT_CARD") {
            confirmBtnTextEl.textContent = "信用卡結帳";
            confirmPaymentButton.disabled = false;
        }
    }

    /**
     * 處理最終付款
     */
    async function handleConfirmPayment() {
        if (confirmPaymentButton.disabled) return;

        confirmPaymentButton.disabled = true;
        confirmBtnTextEl.textContent = "付款處理中...";

        const paymentData = {
            memberId: currentMember ? currentMember.userId : null,
            pointsToUse: pointsToUseInput.valueAsNumber || 0,
            paymentMethod: selectedPaymentMethod,
        };

        try {
            const paidOrder = await processPayment(currentOrder.orderId, paymentData);
            alert(
                `付款成功！ 訂單 ${paidOrder.orderNumber} 狀態已更新為 ${paidOrder.status}`
            );
            window.location.href = "pos.html";
        } catch (error) {
            alert(`付款失敗: ${error.message}`);
            confirmPaymentButton.disabled = false;
            updateConfirmButtonState(); // 恢復按鈕文字
        }
    }


    // --- 5. 綁定所有事件監聽器 ---

    // 中欄
    findMemberBtn.addEventListener("click", handleFindMember);
    pointsToUseInput.addEventListener("input", updateAllTotals);

    // 右欄
    paymentButtonsContainer.addEventListener("click", (e) => {
        const btn = e.target.closest(".payment-btn");
        if (btn) {
            paymentButtonsContainer
                .querySelectorAll(".payment-btn")
                .forEach((b) => b.classList.remove("active"));
            btn.classList.add("active");

            selectedPaymentMethod = btn.dataset.method;

            // 顯示/隱藏現金計算機
            cashCalculatorEl.style.display = (selectedPaymentMethod === "CASH") ? "block" : "none";
            updateCalculatorDisplay(); // 更新顯示和按鈕狀態
        }
    });

    calculatorGrid.addEventListener("click", handleCalculatorClick);
    confirmPaymentButton.addEventListener("click", handleConfirmPayment);
    cancelButton.addEventListener("click", () => {
        if (confirm("確定要取消結帳並返回點餐頁嗎？ (此訂單將維持 PENDING 狀態)")) {
            window.location.href = "pos.html";        }
    });

    paymentMethodChips.addEventListener("change", (e) => {
        const selectedChip = e.target.closest("md-filter-chip[selected]");

        if (selectedChip) {
            selectedPaymentMethod = selectedChip.dataset.method;
        } else {
            selectedPaymentMethod = null; // 沒有選中
        }

        // 顯示/隱藏現金計算機
        cashCalculatorEl.style.display = (selectedPaymentMethod === "CASH") ? "block" : "none";
        updateCalculatorDisplay(); // 更新顯示和按鈕狀態
    });


    // --- 6. 啟動 ---
    loadOrderData();
});