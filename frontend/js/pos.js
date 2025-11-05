import {
  getCategories,
  getPosProducts,
  createOrder,
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
              product.categories.some((cat) => cat.categoryId === Number(categoryId))
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
    const checkoutButton = document.getElementById("checkout-button");
    const holdButton = document.getElementById("hold-button");

    // 1. 綁定 "結帳" 按鈕
    checkoutButton.addEventListener("click", () => {
        submitOrder('CHECKOUT'); // 傳入 'CHECKOUT'，會被轉為 'PENDING'
    });

    // 2. 綁定 "暫存訂單" 按鈕
    holdButton.addEventListener("click", () => {
        submitOrder('HELD'); // 傳入 'HELD'
    });
  renderCart();
});
