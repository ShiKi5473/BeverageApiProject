/**
 * 建立一個可複用的導覽列元件 (MWC 版本)
 * @param {string} title - 顯示在導覽列上的標題
 * @param {function} onLogout - 點擊登出按鈕時要執行的回呼函式
 * @returns {HTMLElement} - <md-top-app-bar> 元素
 */
export function createNavbar(title, onLogout) {
    // 1. 建立 Top App Bar 容器
    const topAppBar = document.createElement("md-top-app-bar");
    topAppBar.setAttribute("variant", "small"); // 您可以試試 "medium" 或 "large"
    topAppBar.style.position = "static";

    // 2. 建立標題
    const h1 = document.createElement("h1");
    h1.textContent = title;
    h1.slot = "headline"; // MWC Top App Bar 的標題 slot
    topAppBar.appendChild(h1);

    // 3. 建立登出按鈕 (使用 Icon Button)
    const logoutButton = document.createElement("md-icon-button");
    logoutButton.id = "logout-button";
    logoutButton.slot = "action"; // MWC 的 "操作按鈕" slot

    // 4. 建立登出圖示
    const icon = document.createElement("md-icon");
    icon.textContent = "logout"; // 使用 Material Symbols 圖示名稱
    logoutButton.appendChild(icon);

    // 5. 綁定事件
    if (onLogout && typeof onLogout === "function") {
        logoutButton.addEventListener("click", onLogout);
    }

    // 6. 附加到 Top App Bar
    topAppBar.appendChild(logoutButton);

    return topAppBar;
}