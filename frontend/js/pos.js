// 從 api.js 匯入 (import) 我們需要的函式
import { getPosProducts } from "./api.js";
import { createProductCard } from "./components/ProductCard.js";

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

  // 3. 綁定登出按鈕事件
  logoutButton.addEventListener("click", () => {
    localStorage.removeItem("accessToken");
    localStorage.removeItem("brandId"); // 【保留】登入時仍需 brandId，登出時一併清除
    window.location.href = "index.html";
  });

  // 4. 定義一個函式來載入並渲染商品
  async function loadProducts() {
    try {
      // 呼叫 API 取得商品 [cite: shiki5473/beverageapiproject/BeverageApiProject-frontendPosView/src/main/java/tw/niels/beverage_api_project/modules/product/controller/ProductController.java]
      const products = await getPosProducts(); // products 是一個 ProductPosDto 陣列 [cite: shiki5473/beverageapiproject/BeverageApiProject-frontendPosView/src/main/java/tw/niels/beverage_api_project/modules/product/dto/ProductPosDto.java]

      // 清空 "載入中..."
      productGrid.innerHTML = "";

      // 5. 遍歷商品陣列，建立 DOM 元素
      products.forEach((product) => {
        const productCard = createProductCard(product); // <-- 使用元件

        // 6. 將「點擊事件」綁定在 pos.js (父層)
        //    元件本身不該知道點擊後要幹嘛，這由父層決定
        productCard.addEventListener("click", () => {
          openOptionsModal(product);
        });

        // 7. 將元件產生的 DOM 元素加入到格線中
        productGrid.appendChild(productCard);
      });
    } catch (error) {
      console.error("載入商品時發生錯誤:", error);
      productGrid.innerHTML = `<p class="error">載入商品失敗: ${error.message}</p>`;
    }
  }

  // 6. 定義點擊商品時開啟 Modal 的函式 (目前先留空)
  function openOptionsModal(product) {
    console.log("點擊了商品:", product);
    // TODO: 顯示 Modal 並根據 product.optionGroups 產生客製化選項 [cite: shiki5473/beverageapiproject/BeverageApiProject-frontendPosView/src/main/java/tw/niels/beverage_api_project/modules/product/dto/ProductPosDto.java]
    // const modal = document.getElementById("modal-overlay");
    // modal.style.display = "flex";
  }

  // 7. 立即執行載入商品
  loadProducts();
});
