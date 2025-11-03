import {
  getCategories,
  getPosProducts,
  createOrder,
  processPayment,
} from "./api.js";
import { createProductCard } from "./components/ProductCard.js";
import { createOptionsModalContent } from "./components/OptionsModal.js";
import { createCartItem, updateCartTotal } from "./components/Cart.js";
import { createNavbar } from "./components/Navbar.js";

let allProducts = [];
let shoppingCart = [];
//
let currentModal = null;

document.addEventListener("DOMContentLoaded", () => {
  // 1. 取得元素
  const productGrid = document.getElementById("product-grid");
  const posLayout = document.querySelector(".pos-layout");
  const mainContent = document.querySelector(".pos-main-content");
  const categoryList = document.getElementById("category-list");
  const cartItemsContainer = document.getElementById("cart-items");
  const cartTotalAmount = document.getElementById("cart-total-amount");
  const modalOverlay = document.getElementById("modal-overlay");
  const modalContent = document.getElementById("modal-content");

  const handleLogout = () => {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("brandId");
    window.location.href = "index.html";
  };
  const navbar = createNavbar("POS 點餐系統", handleLogout);
  posLayout.insertBefore(navbar, mainContent);

  async function submitOrder(statusString) {
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
      storeId: 1, //
      items: orderItemsDto,
      status: statusString, //
    };

    try {
      //
      const newOrder = await createOrder(createOrderRequest);

      //
      openPaymentModal(newOrder);
    } catch (error) {
      console.error(` ${statusString} 失敗:`, error);
      alert(` ${statusString} 失敗: ${error.message}`);
    }
  }

  //
  function openPaymentModal(order) {
    //
    const totalAmount = order.totalAmount;
    const orderId = order.orderId;

    //
    modalContent.innerHTML = `
        <h3>訂單 ${order.orderNumber} - 結帳</h3>
        <p>總金額: NT$ ${totalAmount}</p>
        <div class"form-group">
            <label for="member-phone">會員手機 (選填)</label>
            <input type="text" id="member-phone" />
            <button type="button" id="find-member-btn">查詢</button>
            <span id="member-info"></span>
        </div>
        <div class"form-group" id="points-group" style="display:none;">
            <label for="points-to-use">使用點數</label>
            <input type="number" id="points-to-use" value="0" />
        </div>
        <div class"form-group">
            <label>付款方式</label>
            <div class="option-buttons-container">
                <button type"button" class="option-btn payment-btn" data-method="CASH">現金</button>
                <button type"button" class="option-btn payment-btn" data-method="CREDIT_CARD">信用卡</button>
            </div>
        </div>
    `;

    let selectedMemberId = null;
    let selectedPaymentMethod = null;

    //
    modalOverlay.style.display = "flex";

    // (
    //

    const paymentButtons = modalContent.querySelector(
      ".option-buttons-container"
    );
    paymentButtons.addEventListener("click", (e) => {
      if (e.target.classList.contains("payment-btn")) {
        paymentButtons
          .querySelectorAll(".payment-btn")
          .forEach((btn) => btn.classList.remove("active"));
        e.target.classList.add("active");
        selectedPaymentMethod = e.target.dataset.method;
      }
    });

    //
    const confirmPaymentButton = document.createElement("button");
    confirmPaymentButton.textContent = "確認付款";
    confirmPaymentButton.className = "modal-add-btn";
    confirmPaymentButton.onclick = async () => {
      if (!selectedPaymentMethod) {
        alert("請選擇付款方式！");
        return;
      }

      const pointsToUse =
        document.getElementById("points-to-use").valueAsNumber || 0;

      const paymentData = {
        memberId: selectedMemberId,
        pointsToUse: pointsToUse,
        paymentMethod: selectedPaymentMethod,
      };

      try {
        const paidOrder = await processPayment(orderId, paymentData);
        alert(
          `付款成功！ 訂單 ${paidOrder.orderNumber} 狀態已更新為 ${paidOrder.status}`
        );

        //
        shoppingCart = [];
        renderCart();
        closeModal(); //
      } catch (error) {
        alert(`付款失敗: ${error.message}`);
      }
    };
    modalContent.appendChild(confirmPaymentButton);
  }

  // 3.
  async function loadAllData() {
    productGrid.innerHTML = "<p>正在載入資料...</p>";
    categoryList.innerHTML = "<li><a>載入中...</a></li>";
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
      categoryList.innerHTML = "<li><a>載入失敗</a></li>";
    }
  }

  // 4.
  function renderCategoryList(categories) {
    categoryList.innerHTML = "";
    const allLi = document.createElement("li");
    allLi.innerHTML = `<a href="#" data-category-id="all" class="active">全部商品</a>`;
    categoryList.appendChild(allLi);
    categories.forEach((category) => {
      const li = document.createElement("li");
      li.innerHTML = `<a href="#" data-category-id="${category.categoryId}">${category.name}</a>`;
      categoryList.appendChild(li);
    });
  }

  // 5.
  function renderProducts(categoryId) {
    productGrid.innerHTML = "";
    const productsToRender =
      categoryId === "all"
        ? allProducts
        : allProducts.filter(
            (product) =>
              product.categories &&
              product.categories.some((cat) => cat.categoryId == categoryId)
          );

    if (productsToRender.length === 0) {
      productGrid.innerHTML = "<p>這個分類沒有商品</p>";
      return;
    }
    productsToRender.forEach((product) => {
      const productCard = createProductCard(product);
      productCard.addEventListener("click", () => {
        openOptionsModal(product); //
      });
      productGrid.appendChild(productCard);
    });
  }

  // 6.
  function addCategoryClickListeners() {
    categoryList.addEventListener("click", (event) => {
      event.preventDefault();
      const target = event.target;
      if (target.tagName === "A") {
        categoryList
          .querySelectorAll("a")
          .forEach((a) => a.classList.remove("active"));
        target.classList.add("active");
        const selectedCategoryId = target.getAttribute("data-category-id");
        renderProducts(selectedCategoryId);
      }
    });
  }

  function openOptionsModal(product) {
    // 1.
    currentModal = createOptionsModalContent(product);

    // 2.
    modalContent.innerHTML = "";
    modalContent.appendChild(currentModal.element);

    // 3.
    const addToCartButton = document.createElement("button");
    addToCartButton.textContent = "加入購物車";
    addToCartButton.className = "modal-add-btn";
    addToCartButton.onclick = () => {
      handleAddToCart(product, currentModal.getSelectedData());
    };
    modalContent.appendChild(addToCartButton);

    const closeButton = document.createElement("button");
    closeButton.textContent = "取消";
    closeButton.className = "modal-close-btn";
    closeButton.onclick = closeModal;
    modalContent.appendChild(closeButton);

    modalOverlay.style.display = "flex";
  }

  function closeModal() {
    modalOverlay.style.display = "none";
    currentModal = null; //
  }

  modalOverlay.addEventListener("click", (event) => {
    if (event.target === modalOverlay) {
      closeModal();
    }
  });

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

  loadAllData();
  renderCart();
});
