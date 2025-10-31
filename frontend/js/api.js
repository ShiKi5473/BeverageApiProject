// 定義後端 API 的基礎 URL
const API_BASE_URL = "http://localhost:8080";

/**
 * 處理 API 請求的主函式
 * @param {string} endpoint - API 路徑 (例如 /api/v1/orders)
 * @param {object} options - fetch API 的設定 (method, body 等)
 * @returns {Promise<Response>} - 回傳 fetch 的原始 Response 物件
 */
async function fetchWithAuth(endpoint, options = {}) {
  // 1. 從 localStorage 取得 Token
  const token = localStorage.getItem("accessToken");
  // const brandId = localStorage.getItem("brandId"); // <-- 【移除】不再需要從 localStorage 讀取 brandId

  // 2. 如果沒有 Token，立即導向回登入頁
  if (!token) {
    console.error("沒有找到 accessToken，導向至登入頁");
    redirectToLogin();
    return; // 中斷執行
  }

  // 3. 設定預設的 headers
  const headers = {
    "Content-Type": "application/json",
    ...options.headers, // 保留傳入的 headers
    Authorization: `Bearer ${token}`, // 附加 Bearer Token [cite: shiki5473/beverageapiproject/BeverageApiProject-frontendPosView/src/main/java/tw/niels/beverage_api_project/security/jwt/JwtAuthenticationFilter.java]
  };

  // 4. 【修改】 組合完整的 API 網址
  //    後端現在會自動從 Token 讀取 brandId，不再需要 {brandId} 佔位符
  //    (舊) const url = `${API_BASE_URL}${endpoint.replace("{brandId}", brandId)}`;
  const url = `${API_BASE_URL}${endpoint}`; // <-- 【簡化】

  try {
    // 5. 發送請求
    const response = await fetch(url, { ...options, headers });

    // 6. 檢查 401 (未授權) 錯誤
    if (response.status === 401) {
      console.error("Token 失效或未授權 (401)，清除 Token 並導向至登入頁");
      redirectToLogin();
      return; // 中斷執行
    }

    return response; // 回傳 response 供呼叫者處理
  } catch (error) {
    // 捕捉網路層級的錯誤
    console.error(`API 請求失敗 (${endpoint}):`, error);
    throw error; // 將錯誤丟出，讓呼叫者知道
  }
}

/**
 * 導向回登入頁並清除認證資料
 */
function redirectToLogin() {
  localStorage.removeItem("accessToken");
  localStorage.removeItem("brandId"); // 【保留】登入時仍需 brandId，登出時一併清除
  // 確保我們不會在 index.html 頁面還一直重複導向
  if (window.location.pathname !== "/index.html") {
    window.location.href = "index.html";
  }
}

// --------------------------------------------------
// 匯出 (export) 我們的函式，讓其他 JS 檔案可以使用
// --------------------------------------------------

/**
 * 取得 POS 商品列表
 * (對應 ProductController [cite: shiki5473/beverageapiproject/BeverageApiProject-frontendPosView/src/main/java/tw/niels/beverage_api_project/modules/product/controller/ProductController.java])
 */
export async function getPosProducts() {
  const response = await fetchWithAuth("/api/v1/brands/products/pos", {
    method: "GET",
  });
  if (!response.ok) {
    throw new Error("取得商品失敗");
  }
  return response.json();
}

export async function getCategories() {
  const response = await fetchWithAuth("/api/v1/brands/categories", {
    method: "GET",
  });
  if (!response.ok) {
    throw new Error("取得分類失敗");
  }
  return response.json();
}
