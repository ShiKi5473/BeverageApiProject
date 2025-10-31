import { getCategories, getPosProducts } from "./api.js";
import { createProductCard } from "./components/ProductCard.js";

let allProducts = [];
let shoppingCart = [];

// 等待 DOM 載入
document.addEventListener("DOMContentLoaded", () => {
  // 1. 檢查是否有 Token，沒有就踢回登入頁
  if (!localStorage.getItem("accessToken")) {
    window.location.href = "index.html";
    return;
  }

  // 2. 取得頁面上的主要元素
  const productGrid = document.getElementById("product-grid");
  const logoutButton = document.getElementById("logout-button");
  const categoryList = document.getElementById("category-list");
  const cartItemsContainer = document.getElementById("cart-items");
  const cartTotalAmount = document.getElementById("cart-total-amount");

  const modalOverlay = document.getElementById("modal-overlay");
  const modalContent = document.getElementById("modal-content");

  // 3. 綁定登出按鈕事件
  logoutButton.addEventListener("click", () => {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("brandId");
    window.location.href = "index.html";
  });

  // 4. 定義一個函式來載入並渲染商品
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

  // 5. 渲染分類列表
  function renderCategoryList(categories) {
    categoryList.innerHTML = "";

    // 新增 "全部商品" 選項
    const allLi = document.createElement("li");
    allLi.innerHTML = `<a href="#" data-category-id="all" class="active">全部商品</a>`;
    categoryList.appendChild(allLi);

    //
    categories.forEach((category) => {
      const li = document.createElement("li");
      li.innerHTML = `<a href="#" data-category-id="${category.categoryId}">${category.name}</a>`;
      categoryList.appendChild(li);
    });
  }

  // 6. 根據分類 ID 渲染商品
  function renderProducts(categoryId) {
    productGrid.innerHTML = ""; // 清空畫布

    // 篩選商品
    const productsToRender =
      categoryId === "all"
        ? allProducts // 如果是 "all"，顯示全部
        : allProducts.filter(
            (product) =>
              // 檢查 product.categories 是否存在，且是否 "有任何一個" 分類的 ID 符合
              product.categories &&
              product.categories.some((cat) => cat.categoryId == categoryId)
          );

    if (productsToRender.length === 0) {
      productGrid.innerHTML = "<p>這個分類沒有商品</p>";
      return;
    }

    // 渲染篩選後的商品
    productsToRender.forEach((product) => {
      const productCard = createProductCard(product);
      productCard.addEventListener("click", () => {
        openOptionsModal(product);
      });
      productGrid.appendChild(productCard);
    });
  }

  // 7. 綁定分類點擊事件
  function addCategoryClickListeners() {
    categoryList.addEventListener("click", (event) => {
      event.preventDefault(); //

      const target = event.target;
      if (target.tagName === "A") {
        categoryList
          .querySelectorAll("a")
          .forEach((a) => a.classList.remove("active"));
        //
        target.classList.add("active");

        const selectedCategoryId = target.getAttribute("data-category-id");
        renderProducts(selectedCategoryId);
      }
    });
  }

  function openOptionsModal(product) {
    // 1. 清空舊內容
    modalContent.innerHTML = "";

    // 2. 建立標題
    const title = document.createElement("h3");
    title.textContent = product.name;
    modalContent.appendChild(title);

    // 3. 遍歷 OptionGroups (例如: 甜度、冰塊)
    product.optionGroups.forEach((group) => {
      const groupContainer = document.createElement("div");
      groupContainer.className = "option-group";

      const groupTitle = document.createElement("h4");
      const selectionType = group.selectionType === "SINGLE" ? "單選" : "多選";
      groupTitle.textContent = `${group.name} (${selectionType})`;
      groupContainer.appendChild(groupTitle);

      const buttonsContainer = document.createElement("div");
      buttonsContainer.className = "option-buttons-container";

      // 5. 遍歷 Options (例如: 全糖、半糖)
      group.options.forEach((option) => {
        const button = document.createElement("button");
        button.type = "button"; //
        button.className = "option-btn";

        let labelText = option.optionName;
        if (option.priceAdjustment > 0) {
          labelText += ` (+NT$ ${option.priceAdjustment})`;
        }
        button.textContent = labelText;

        //
        button.dataset.optionId = option.optionId;

        buttonsContainer.appendChild(button);
      });

      buttonsContainer.addEventListener("click", (event) => {
        const clickedButton = event.target.closest(".option-btn");
        if (!clickedButton) return; //

        const selectionType = group.selectionType;

        if (selectionType === "SINGLE") {
          //
          buttonsContainer.querySelectorAll(".option-btn").forEach((btn) => {
            btn.classList.remove("active");
          });
          //
          clickedButton.classList.add("active");
        } else {
          // MULTIPLE
          //
          clickedButton.classList.toggle("active");
        }
      });

      groupContainer.appendChild(buttonsContainer);
      modalContent.appendChild(groupContainer);
    });

    const quantitySelector = document.createElement("div");
    quantitySelector.className = "quantity-selector";
    quantitySelector.innerHTML = `
      <button type="button" class="quantity-btn minus-btn" disabled>-</button>
      <span class="quantity-display">1</span>
      <button type="button" class="quantity-btn plus-btn">+</button>
    `;

    //
    const display = quantitySelector.querySelector(".quantity-display");
    const minusBtn = quantitySelector.querySelector(".minus-btn");
    const plusBtn = quantitySelector.querySelector(".plus-btn");

    minusBtn.onclick = () => {
      let qty = parseInt(display.textContent);
      if (qty > 1) {
        qty--;
        display.textContent = qty;
        plusBtn.disabled = false; //
        if (qty === 1) {
          minusBtn.disabled = true; //
        }
      }
    };

    plusBtn.onclick = () => {
      let qty = parseInt(display.textContent);
      qty++; //
      display.textContent = qty;
      minusBtn.disabled = false; //
    };

    modalContent.appendChild(quantitySelector);

    // 6. 建立按鈕
    const notesInput = document.createElement("input");
    notesInput.type = "text";
    notesInput.placeholder = "備註...";
    notesInput.className = "modal-notes";
    modalContent.appendChild(notesInput);

    const addToCartButton = document.createElement("button");
    addToCartButton.textContent = "加入購物車";
    addToCartButton.className = "modal-add-btn";
    addToCartButton.onclick = () => {
      handleAddToCart(product, modalContent);
    };
    modalContent.appendChild(addToCartButton);

    const closeButton = document.createElement("button");
    closeButton.textContent = "取消";
    closeButton.className = "modal-close-btn";
    closeButton.onclick = closeModal; //
    modalContent.appendChild(closeButton);

    // 7. 顯示 Modal
    modalOverlay.style.display = "flex";
  }

  //  9. 實作關閉 Modal 的函式
  function closeModal() {
    modalOverlay.style.display = "none";
  }

  //  10. 綁定點擊 Modal 灰色背景區域來關閉視窗
  modalOverlay.addEventListener("click", (event) => {
    //
    if (event.target === modalOverlay) {
      closeModal();
    }
  });

  function handleAddToCart(product, modalContent) {
    // 1. 取得備註
    const notes = modalContent.querySelector(".modal-notes").value;

    const quantity = parseInt(
      modalContent.querySelector(".quantity-display").textContent
    );

    // 2. 取得所有被選中的選項按鈕
    const selectedOptionIds = Array.from(
      modalContent.querySelectorAll(".option-btn.active")
    ).map((btn) => btn.dataset.optionId);

    // 3. 從 product 物件中，撈出完整的選項資料
    const allOptions = product.optionGroups.flatMap((group) => group.options);
    const selectedOptions = allOptions.filter((option) =>
      selectedOptionIds.includes(String(option.optionId))
    );

    // 4. 計算價格
    //
    const optionPriceAdjustment = selectedOptions.reduce(
      (sum, opt) => sum + opt.priceAdjustment,
      0
    );
    const unitPrice = product.basePrice + optionPriceAdjustment;

    // 5. 建立購物車品項物件
    const cartItem = {
      id: Date.now(), //
      productId: product.id,
      name: product.name,
      quantity: quantity,
      unitPrice: unitPrice,
      selectedOptions: selectedOptions,
      notes: notes,
    };

    // 6. 加入購物車並重新渲染
    shoppingCart.push(cartItem);
    renderCart();
    closeModal();
  }
  function renderCart() {
    // 1. 清空舊內容
    cartItemsContainer.innerHTML = "";
    let total = 0;

    if (shoppingCart.length === 0) {
      cartItemsContainer.innerHTML = "<p class='cart-empty'>購物車是空的</p>";
      cartTotalAmount.textContent = "NT$ 0";
      return;
    }

    // 2. 遍歷購物車陣列
    shoppingCart.forEach((item) => {
      const itemElement = document.createElement("div");
      itemElement.className = "cart-item";

      //
      const optionsText = item.selectedOptions
        .map((opt) => opt.optionName)
        .join(", ");

      const notesText = item.notes
        ? `<div class="cart-item-notes">備註: ${item.notes}</div>`
        : "";

      // 3. 產生 HTML
      itemElement.innerHTML = `
       <div class="cart-item-header">
          <span class="cart-item-name">${item.name} (x${item.quantity})</span>
          <button class="cart-item-remove-btn" data-cart-id="${
            item.id
          }">×</button>
        </div>
        <div class="cart-item-options">${optionsText}</div>
        ${notesText}
        <div class="cart-item-price">NT$ ${item.unitPrice * item.quantity}</div>
      `;
      // TODO:

      cartItemsContainer.appendChild(itemElement);

      // 4. 累加總金額
      total += item.unitPrice * item.quantity;
    });

    // 5. 更新總金額
    cartTotalAmount.textContent = `NT$ ${total}`;
  }

  function handleRemoveFromCart(cartId) {
    //
    shoppingCart = shoppingCart.filter((item) => item.id !== cartId);
    //
    renderCart();
  }

  cartItemsContainer.addEventListener("click", (event) => {
    const removeButton = event.target.closest(".cart-item-remove-btn");
    if (removeButton) {
      //
      const cartId = Number(removeButton.dataset.cartId);
      handleRemoveFromCart(cartId);
    }
  });

  // 9. 執行 loadAllData
  loadAllData();

  // 【新增】 14.
  renderCart();
});
