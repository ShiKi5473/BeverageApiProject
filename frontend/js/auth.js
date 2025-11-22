import '@material/web/textfield/filled-text-field.js';
import '@material/web/button/filled-button.js';

document.addEventListener("DOMContentLoaded", () => {
  // 1. 取得登入表單元素
  const loginForm = document.getElementById("login-form");
  const errorMessage = document.getElementById("error-message");

  // 2. 監聽表單的 "submit" (提交) 事件
  loginForm.addEventListener("submit", async (event) => {
    // 3. 防止表單的預設提交行為 (避免頁面重新整理)
    event.preventDefault();

    // 4. 清除先前的錯誤訊息
    errorMessage.textContent = "";

    // 5. 從表單欄位取得輸入值
    const brandId = document.getElementById("brandId").value;
    const username = document.getElementById("username").value;
    const password = document.getElementById("password").value;

    // 6. 建立要發送給後端的資料
    //    (結構必須符合 LoginRequestDto.java)
    const loginData = {
      brandId: parseInt(brandId), // 轉為數字
      username: username,
      password: password,
    };

    try {
      // 7. 使用 fetch API 呼叫後端登入 API
      //    (端點路徑來自 AuthController.java)
      const response = await fetch("http://localhost:8080/api/v1/auth/login", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify(loginData), // 將 JavaScript 物件轉為 JSON 字串
      });

      // 8. 檢查 API 回應是否成功 (HTTP 狀態碼 200-299)
      if (response.ok) {
        // 9. 解析回應的 JSON 資料
        //    (結構來自 JwtAuthResponseDto.java)
        const data = await response.json();

        // 10. 將 accessToken 存到瀏覽器的 localStorage
        localStorage.setItem("accessToken", data.accessToken);
        localStorage.setItem("brandId", brandId);

          const userRole = data.role;

          if (userRole === "ROLE_BRAND_ADMIN") {
              // --- 1. 品牌管理員 ---
              localStorage.removeItem("storeId");
              alert("品牌管理員登入成功。\n即將進入報表頁面。");
              // 建議管理員導向剛做好的報表頁
              window.location.href = "report.html";

          } else if (userRole === "ROLE_MEMBER") {
              // --- 2. 會員 (修正原本誤判為管理員的問題) ---
              localStorage.removeItem("storeId");
              // 視您的需求決定會員要去哪，例如會員中心或首頁
              alert("會員登入成功。");
              // window.location.href = "member.html"; // 範例

          } else {
              // --- 3. 店長或店員 (ROLE_MANAGER, ROLE_STAFF) ---
              if (data.storeId) {
                  localStorage.setItem("storeId", data.storeId);
                  window.location.href = "pos.html";
              } else {
                  // 異常狀況：是員工但沒有 storeId
                  console.error("員工帳號異常：無分店綁定");
                  errorMessage.textContent = "帳號設定異常，請聯繫管理員";
              }
          }

      } else {
        // 12. 登入失敗 (例如 401 帳密錯誤)
        const errorText = await response.text();
        errorMessage.textContent = `登入失敗：${errorText}`;
      }
    } catch (error) {
      // 13. 捕捉網路錯誤 (例如後端伺服器沒開)
      console.error("登入時發生錯誤:", error);
      errorMessage.textContent = "無法連線至伺服器，請稍後再試。";
    }
  });
});
