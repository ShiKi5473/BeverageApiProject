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

          if (data.storeId) {
              // 登入者是「店員」或「店長」
              localStorage.setItem("storeId", data.storeId);
              // 11. 轉跳到點餐頁面 (pos.html)
              window.location.href = "pos.html";
          } else {
              // 登入者是「品牌管理員」
              localStorage.removeItem("storeId");
              console.warn("此帳號為品牌管理員，應導向管理頁面 (目前尚未建立)");
              // TODO 未來您可以建立一個 admin.html
              // window.location.href = "admin.html";

              // 目前暫時顯示提醒，並允許他進入 POS (雖然 POS 會擋住他)
              alert("品牌管理員登入成功。\n（目前沒有專屬後台，將嘗試進入 POS 頁）");
              window.location.href = "pos.html"; // 保留原狀，但已告知使用者
          }

        // 11. 登入成功，轉跳到點餐頁面 (pos.html)
          window.location.href = "pos.html";
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
