
import '@material/web/icon/icon.js';
import '@material/web/iconbutton/icon-button.js';
import '@material/web/list/list.js';
import '@material/web/list/list-item.js';
import '@material/web/button/filled-button.js';
import '@material/web/button/outlined-button.js';
import '@material/web/dialog/dialog.js';
import '@material/web/divider/divider.js';


import '@material/web/chips/chip-set.js';
import '@material/web/chips/filter-chip.js';
import '@material/web/textfield/filled-text-field.js';

import {
  getCategories,
  getPosProducts,
  createOrder,
    getOrdersByStatus,
    updateOrderStatus
} from "./api.js";
import { createProductCard } from "./components/ProductCard.js";
import { createOptionsModalContent } from "./components/OptionsModal.js";
import { createCartItem, updateCartTotal } from "./components/Cart.js";
import { createNavbar } from "./components/Navbar.js";
import { connectToWebSocket } from "./ws-client.js";

let allProducts = [];
let shoppingCart = [];
let currentModal = null;

const MY_STORE_ID = localStorage.getItem("storeId");

const optionsDialog = document.getElementById("options-dialog");
const dialogContentSlot = document.getElementById("dialog-content-slot");
const modalCloseButton = document.getElementById("modal-close-btn");
const modalAddButton = document.getElementById("modal-add-btn");

document.addEventListener("DOMContentLoaded", () => {
    if (!MY_STORE_ID) {
        // 可能是品牌管理員，或登入狀態異常
        const errorMsg = "錯誤：找不到店家 ID (storeId)。\n\n品牌管理員帳號無法使用 POS 點餐系統。\n\n將導回登入頁。";
        console.error(errorMsg);
        alert(errorMsg);
        window.location.href = "login.html";
        return; // 中斷此腳本的後續執行
    }

    // 1. 取得元素
  const productGrid = document.getElementById("product-grid");
  const posLayout = document.querySelector(".pos-layout");
  const mainContent = document.querySelector(".pos-main-content");
  const categoryList = document.getElementById("category-list");
  const cartItemsContainer = document.getElementById("cart-items");
  const cartTotalAmount = document.getElementById("cart-total-amount");


    const pickupListEl = document.getElementById("pickup-list");
  const handleLogout = () => {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("brandId");
      window.location.href = "login.html";
  };
  const navbar = createNavbar("POS 點餐系統", handleLogout);
  posLayout.insertBefore(navbar, mainContent);

  async function submitOrder(action) {
    if (shoppingCart.length === 0) {
      alert("購物車是空的！");
      return;
    }

    const orderItemsDto = shoppingCart.map((item) => ({
      productId: item.productId,
      quantity: item.quantity,
      notes: item.notes,
      optionIds: item.selectedOptions.map((opt) => opt.optionId),
    }));

    //
    const createOrderRequest = {
      items: orderItemsDto,
      status: (action === 'HELD') ? 'HELD' : 'PENDING'
    };

    try {
        const newOrder = await createOrder(createOrderRequest);
        if (action === 'HELD') {
            // 暫存成功
            alert(`訂單 ${newOrder.orderNumber} 已暫存`);
            shoppingCart = [];
            renderCart();
        } else {
            window.location.href = `checkout.html?orderId=${newOrder.orderId}`;
        }
    }catch (error) {
        console.error(` ${action} 失敗:`, error);
        alert(` ${action} 失敗: ${error.message}`);
    }
  }



  // 3.
  async function loadAllData() {
    productGrid.innerHTML = "<p>正在載入資料...</p>";
    categoryList.innerHTML = `<md-list-item headline="載入中..."></md-list-item>`;
    try {
      const [products, categories] = await Promise.all([
        getPosProducts(),
        getCategories(),
      ]);
      allProducts = products;
      renderCategoryList(categories);
      renderProducts("all");
      addCategoryClickListeners();
    } catch (error) {
      console.error("載入資料時發生錯誤:", error);
      productGrid.innerHTML = `<p class="error">資料載入失敗: ${error.message}</p>`;
        categoryList.innerHTML = `<md-list-item headline="載入失敗"></md-list-item>`;
    }
  }

  // 4.
    function renderCategoryList(categories) {
        categoryList.innerHTML = ""; // 清空

        // "全部商品"
        const allLi = document.createElement("md-list-item");
        allLi.setAttribute("headline", "全部商品");
        allLi.dataset.categoryId = "all";
        allLi.classList.add("active"); // 您可能需要 CSS 調整 .active 樣式

        // 1. 建立一個 <div> (或 <span>)
        const allLiText = document.createElement("div");
        // 2. 告訴它要被放入 "headline" 插槽
        allLiText.setAttribute("slot", "headline");
        // 3. 設定文字內容
        allLiText.textContent = "全部商品";
        // 4. 將這個 <div> 放入 <md-list-item> 中
        allLi.appendChild(allLiText);

        categoryList.appendChild(allLi);

        // 其他分類
        categories.forEach((category) => {
            const li = document.createElement("md-list-item");
            li.dataset.categoryId = category.categoryId;

            // 1. 建立一個 <div>
            const liText = document.createElement("div");
            // 2. 告訴它要被放入 "headline" 插槽
            liText.setAttribute("slot", "headline");
            // 3. 設定文字內容
            liText.textContent = category.name;
            // 4. 將這個 <div> 放入 <md-list-item> 中
            li.appendChild(liText);

            categoryList.appendChild(li);
        });
    }

  // 5.
  function renderProducts(categoryId) {
      // 1. 取得最新資料 (邏輯不變)
      const productsToRender =
          categoryId === "all"
              ? allProducts
              : allProducts.filter(
                  (product) =>
                      product.categories &&
                      product.categories.some((cat) => cat.categoryId === Number(categoryId))
              );

      for (const child of Array.from(productGrid.children)) {
          // 如果這個子元素「沒有」data-product-id，就代表它是 <p> 標籤，移除它
          if (!child.dataset.productId) {
              child.remove();
          }
      }

      // 2. 建立一個「新資料的 ID 集合」，方便快速查找
      const newIdSet = new Set(productsToRender.map(p => p.id));

      // 3. 建立一個「當前畫面上所有卡片的 Map」，方便快速存取
      //    Map<productId, domElement>
      const existingCardMap = new Map();
      for (const cardElement of productGrid.children) {
          const productId = cardElement.dataset.productId;
          if (productId) {
              existingCardMap.set(Number(productId), cardElement);
          }
      }

      // 4. 【移除階段】
      // 檢查畫面上舊的卡片，如果「不在」新的資料集合中，就移除它
      existingCardMap.forEach((cardElement, productId) => {
          if (!newIdSet.has(productId)) {
              cardElement.remove();
          }
      });

      // 5. 【新增階段】
      // 遍歷「新資料」，如果畫面上「沒有」這張卡，就建立並新增它
      productsToRender.forEach((product) => {
          if (!existingCardMap.has(product.id)) {
              const productCard = createProductCard(product);
              productCard.addEventListener("click", () => {
                  openOptionsModal(product);
              });
              productGrid.appendChild(productCard);
          }
          // 如果已存在 (existingCardMap.has(product.id) 為 true)，
          // 我們就什麼都不做，DOM 節點會被原地保留，這就是效能的來源！
      });

      // 處理空狀態
      if (productGrid.children.length === 0) {
          productGrid.innerHTML = "<p>這個分類沒有商品</p>";
      }
  }

  // 6.
    function addCategoryClickListeners() {
        categoryList.addEventListener("click", (event) => {
            // event.preventDefault(); // MWC 元件可能不需要
            const target = event.target.closest("md-list-item"); // 找 <md-list-item>
            if (target) {
                categoryList
                    .querySelectorAll("md-list-item")
                    .forEach((a) => a.classList.remove("active"));
                target.classList.add("active");

                const selectedCategoryId = target.getAttribute("data-category-id");
                renderProducts(selectedCategoryId);
            }
        });
    }

    function openOptionsModal(product) {
        // 1. 建立 modal 內容 (來自 OptionsModal.js)
        currentModal = createOptionsModalContent(product);

        // 2. 將內容注入 dialog
        dialogContentSlot.innerHTML = ""; // 清空舊內容
        dialogContentSlot.appendChild(currentModal.element);

        // 3. 【修改】移除舊的按鈕建立邏輯，改為替 dialog 的按鈕綁定新事件
        //    (我們需要移除舊監聽器，或用 .cloneNode(true) 取代按鈕來重設)

        // 簡單起見，我們直接重設按鈕的監聽器
        modalAddButton.onclick = () => {
            handleAddToCart(product, currentModal.getSelectedData());
        };
        modalCloseButton.onclick = closeModal;

        // 4. 顯示 dialog
        optionsDialog.show();
    }

    function closeModal() {
        optionsDialog.close();
        currentModal = null; // 清理
    }



  function handleAddToCart(product, selectedData) {
    const allOptions = product.optionGroups.flatMap((group) => group.options);
    const selectedOptions = allOptions.filter((option) =>
      selectedData.selectedOptionIds.includes(String(option.optionId))
    );

    const optionPriceAdjustment = selectedOptions.reduce(
      (sum, opt) => sum + opt.priceAdjustment,
      0
    );
    const unitPrice = product.basePrice + optionPriceAdjustment;

    const cartItem = {
      id: Date.now(),
      productId: product.id,
      name: product.name,
      quantity: selectedData.quantity,
      unitPrice: unitPrice,
      selectedOptions: selectedOptions,
      notes: selectedData.notes,
    };

    shoppingCart.push(cartItem);
    renderCart();
    closeModal();
  }

  function renderCart() {
    cartItemsContainer.innerHTML = "";
    if (shoppingCart.length === 0) {
      cartItemsContainer.innerHTML = "<p class='cart-empty'>購物車是空的</p>";
    } else {
      shoppingCart.forEach((item) => {
        //
        const itemElement = createCartItem(item);
        cartItemsContainer.appendChild(itemElement);
      });
    }
    //
    updateCartTotal(shoppingCart, cartTotalAmount);
  }

  function handleRemoveFromCart(cartId) {
    shoppingCart = shoppingCart.filter((item) => item.id !== cartId);
    renderCart();
  }

  cartItemsContainer.addEventListener("click", (event) => {
    const removeButton = event.target.closest(".cart-item-remove-btn");
    if (removeButton) {
      const cartId = Number(removeButton.dataset.cartId);
      handleRemoveFromCart(cartId);
    }
  });

    async function loadPickupOrders() {
        try {
            const orders = await getOrdersByStatus(MY_STORE_ID, "READY_FOR_PICKUP");
            pickupListEl.innerHTML = ""; // 清空
            if (orders.length === 0) {
                pickupListEl.innerHTML = "<p class='pickup-empty'>目前沒有待取餐點</p>";
            } else {
                orders.forEach(renderPickupItem);
            }
        } catch (e) {
            pickupListEl.innerHTML = `<p class="error">${e.message}</p>`;
        }
    }

    /**
     * 7. 【新增】渲染單一待取餐項目
     */
    function renderPickupItem(order) {
        const orderId = `pickup-order-${order.orderId}`;
        if (document.getElementById(orderId)) return; // 避免重複

        // 移除 "empty" 提示
        const emptyEl = pickupListEl.querySelector(".pickup-empty");
        if (emptyEl) emptyEl.remove();

        const itemEl = document.createElement("div");
        itemEl.id = orderId;
        itemEl.className = "pickup-item";
        itemEl.innerHTML = `
          <span class="pickup-item-number">#${order.orderNumber}</span>
          <button class="btn-complete-pickup" data-order-id="${order.orderId}">完成取餐</button>
      `;
        pickupListEl.prepend(itemEl); // 新的放最上面
    }

    /**
     * 8. 【新增】處理 WebSocket 訊息
     */
    function handlePosWebSocketMessage(action, order) {
        console.log("POS 收到 WS 訊息:", action, order.orderNumber);
        const orderElId = `pickup-order-${order.orderId}`;
        const existingEl = document.getElementById(orderElId);

        if (action === "MOVE_TO_PICKUP") {
            // KDS 製作完成 -> 加入待取餐
            if (!existingEl) {
                renderPickupItem(order);
            }
        } else if (action === "REMOVE_FROM_PICKUP" || action === "CANCEL_ORDER") {
            // 顧客已取餐 (CLOSED) 或 訂單取消 (CANCELLED)
            if (existingEl) {
                existingEl.remove();
            }
            // 檢查列表是否空了
            if (pickupListEl.children.length === 0) {
                pickupListEl.innerHTML = "<p class='pickup-empty'>目前沒有待取餐點</p>";
            }
        }
        // POS 不需要處理 NEW_ORDER (因為 KDS 會處理)
    }

    /**
     * 9. 【新增】處理 POS 上的「完成取餐」按鈕點擊
     */
    async function handleCompletePickup(event) {
        const button = event.target.closest(".btn-complete-pickup");
        if (!button) return;

        const orderId = button.dataset.orderId;
        button.disabled = true;
        button.textContent = "處理中...";

        try {
            // 【關鍵】呼叫 API，將狀態從 READY_FOR_PICKUP -> CLOSED
            await updateOrderStatus(orderId, "CLOSED");

            // 成功！
            // 後端會發布 "REMOVE_FROM_PICKUP" 事件，
            // handlePosWebSocketMessage() 會自動處理 UI 更新 (移除卡片)

        } catch (error) {
            console.error("更新訂單為 CLOSED 失敗:", error);
            alert(`訂單 ${orderId} 更新失敗: ${error.message}`);
            button.disabled = false;
            button.textContent = "完成取餐";
        }
    }

    // --- 10. 啟動程序 ---

    // 綁定「結帳」和「暫存」按鈕
    const checkoutButton = document.getElementById("checkout-button");
    const holdButton = document.getElementById("hold-button");
    checkoutButton.addEventListener("click", () => submitOrder('CHECKOUT'));
    holdButton.addEventListener("click", () => submitOrder('HELD'));

    // 【新增】綁定「完成取餐」按鈕 (使用事件委派)
    pickupListEl.addEventListener("click", handleCompletePickup);

    // 載入初始資料
    loadAllData(); // 載入商品
    renderCart();  // 渲染空購物車
    loadPickupOrders(); // 【新增】載入待取餐列表

    // 【新增】啟動 WebSocket
    connectToWebSocket(
        MY_STORE_ID,
        // onMessage
        (action, payload) => handlePosWebSocketMessage(action, payload),
        // onConnect
        () => { console.log("POS WebSocket 已連線"); },
        // onError
        (error) => { console.error("POS WebSocket 連線失敗:", error); }
    );
});