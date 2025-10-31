import { getCategories, getPosProducts } from "./api.js";
import { createProductCard } from "./components/ProductCard.js";

let allProducts = [];

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

  // 3. 綁定登出按鈕事件
  logoutButton.addEventListener("click", () => {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("brandId"); // 【保留】登入時仍需 brandId，登出時一併清除
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

  // 8. 定義點擊商品時開啟 Modal 的函式 (維持不變)
  function openOptionsModal(product) {
    console.log("點擊了商品:", product);
    // TODO: 顯示 Modal 並根據 product.optionGroups 產生客製化選項
    // const modal = document.getElementById("modal-overlay");
    // modal.style.display = "flex";
  }

  // 9. 【修改】 執行 loadAllData
  loadAllData();
});
