

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

  // 4.
  try {
    // 5. 發送請求
    const response = await fetch(endpoint, { ...options, headers });

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
    if (!window.location.pathname.endsWith("/login.html")) {
        window.location.href = "login.html";  }
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

/**
 * 對一筆現有訂單進行結帳 (付款、綁定會員)
 * (對應 OrderController)
 * @param {number} orderId -
 * @param {object} paymentData -
 */
export async function processPayment(orderId, paymentData) {
  const response = await fetchWithAuth(`/api/v1/orders/${orderId}/checkout`, {
    method: "PATCH",
    body: JSON.stringify(paymentData),
  });

  if (!response.ok) {
    const errorBody = await response.text();
    throw new Error(`付款失敗: ${errorBody}`);
  }
  return response.json();
}

/**
 * 建立一筆新訂單
 * (對應 OrderController)
 * @param {object} orderData - 包含 storeId 和 items 的訂單資料
 */
export async function createOrder(orderData) {
    // 呼叫後端的 POST /api/v1/orders
    const response = await fetchWithAuth("/api/v1/orders", {
        method: "POST",
        body: JSON.stringify(orderData),
    });

    if (!response.ok) {
        const errorBody = await response.text();
        throw new Error(`建立訂單失敗: ${errorBody}`);
    }
    return response.json();
}

/**
 * 執行 POS 現場「一步到位」結帳
 * (對應 OrderController @PostMapping("/pos-checkout"))
 * @param {object} checkoutData - 包含 items, memberId, pointsToUse, paymentMethod 的 DTO
 */
export async function posCheckoutComplete(checkoutData) {
    const response = await fetchWithAuth("/api/v1/orders/pos-checkout", {
        method: "POST",
        body: JSON.stringify(checkoutData),
    });

    if (!response.ok) {
        const errorBody = await response.text();
        throw new Error(`結帳失敗: ${errorBody}`);
    }
    return response.json();
}
/**
 * 取得單一訂單的詳細資料
 * (對應 OrderController GET /api/v1/orders/{orderId})
 * @param {number} orderId 訂單 ID
 */
export async function getOrderDetails(orderId) {
    const response = await fetchWithAuth(`/api/v1/orders/${orderId}`, {
        method: "GET",
    });

    if (!response.ok) {
        const errorBody = await response.text();
        throw new Error(`取得訂單詳情失敗: ${errorBody}`);
    }
    return response.json();
}

/**
 * 根據手機號碼查詢會員
 * (對應 UserController GET /api/v1/users/member/by-phone/{phone})
 * @param {string} phone 會員手機
 */
export async function findMemberByPhone(phone) {
    const response = await fetchWithAuth(`/api/v1/users/member/by-phone/${phone}`, {
        method: "GET",
    });

    if (response.status === 404) {
        // 404 不是伺服器錯誤，是「查無此人」，回傳 null
        return null;
    }

    if (!response.ok) {
        const errorBody = await response.text();
        throw new Error(`查詢會員失敗: ${errorBody}`);
    }
    return response.json();
}

/**
 * 根據狀態查詢訂單列表
 * (對應 OrderController GET /api/v1/orders?storeId=...&status=...)
 * @param {number} storeId
 * @param {string} status (e.g., "PREPARING", "READY_FOR_PICKUP")
 */
export async function getOrdersByStatus(storeId, status) {
    const response = await fetchWithAuth(
        `/api/v1/orders?storeId=${storeId}&status=${status}`,
        { method: "GET" }
    );
    if (!response.ok) {
        const errorBody = await response.text();
        throw new Error(`取得 ${status} 訂單失敗: ${errorBody}`);
    }
    return response.json();
}

/**
 * 更新訂單狀態
 * (對應 OrderController PATCH /api/v1/orders/{orderId}/status)
 * @param {number} orderId
 * @param {string} newStatus (e.g., "READY_FOR_PICKUP", "CLOSED")
 */
export async function updateOrderStatus(orderId, newStatus) {
    const response = await fetchWithAuth(
        `/api/v1/orders/${orderId}/status`,
        {
            method: "PATCH",
            body: JSON.stringify({ status: newStatus }),
        }
    );
    if (!response.ok) {
        const errorBody = await response.text();
        throw new Error(`更新訂單狀態為 ${newStatus} 失敗: ${errorBody}`);
    }
    return response.json();
}

/**
 * 取得品牌下所有分店
 * (對應 StoreController GET /api/v1/stores)
 */
export async function getStores() {
    const response = await fetchWithAuth("/api/v1/stores", {
        method: "GET",
    });
    if (!response.ok) {
        throw new Error("取得分店列表失敗");
    }
    return response.json();
}

/**
 * 取得分店日結統計 (折線圖資料)
 */
export async function getStoreDailyStats(storeId, startDate, endDate) {
    const params = new URLSearchParams({
        storeId,
        startDate,
        endDate
    });
    const response = await fetchWithAuth(`/api/v1/reports/store-daily?${params}`, {
        method: "GET"
    });
    if (!response.ok) throw new Error("取得營收統計失敗");
    return response.json();
}

/**
 * 取得熱銷商品排行 (長條圖資料)
 */
export async function getProductSalesRanking(storeId, startDate, endDate) {
    const params = new URLSearchParams({
        storeId,
        startDate,
        endDate
    });
    const response = await fetchWithAuth(`/api/v1/reports/product-sales?${params}`, {
        method: "GET"
    });
    if (!response.ok) throw new Error("取得商品排行失敗");
    return response.json();
}

/**
 * 取得品牌總覽 (KPI 卡片資料) - 僅品牌管理員
 */
export async function getBrandSummary(startDate, endDate) {
    const params = new URLSearchParams({ startDate, endDate });
    const response = await fetchWithAuth(`/api/v1/reports/brand-summary?${params}`, {
        method: "GET"
    });
    if (!response.ok) throw new Error("取得品牌總覽失敗");
    return response.json();
}

/**
 * 取得分店排行 (長條圖資料) - 僅品牌管理員
 */
export async function getStoreRanking(startDate, endDate) {
    const params = new URLSearchParams({ startDate, endDate });
    const response = await fetchWithAuth(`/api/v1/reports/store-ranking?${params}`, {
        method: "GET"
    });
    if (!response.ok) throw new Error("取得分店排行失敗");
    return response.json();
}

// ==========================================
// 📦 庫存管理相關 API (Inventory)
// ==========================================

/**
 * 取得當前所有庫存項目的快照 (用於盤點)
 * 對應後端: GET /api/v1/inventory/items (假設路徑)
 */
export async function getInventoryItems() {
    const response = await fetchWithAuth("/api/v1/inventory/items", {
        method: "GET",
    });
    if (!response.ok) {
        throw new Error("無法取得庫存列表");
    }
    return response.json();
}

/**
 * 提交盤點結果
 * 對應後端: POST /api/v1/inventory/audit
 * @param {object} auditData - 包含 items 的盤點資料物件
 */
export async function submitInventoryAudit(auditData) {
    const response = await fetchWithAuth("/api/v1/inventory/audit", {
        method: "POST",
        body: JSON.stringify(auditData),
    });

    if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`提交盤點失敗: ${errorText}`);
    }
    return response.json();
}