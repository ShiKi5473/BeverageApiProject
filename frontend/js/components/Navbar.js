/**
 * 建立一個可複用的導覽列元件
 * @param {string} title - 顯示在導覽列上的標題
 * @param {function} onLogout - 點擊登出按鈕時要執行的回呼函式
 * @returns {HTMLElement} -
 */
export function createNavbar(title, onLogout) {
  // 1.
  const navbar = document.createElement("nav");
  //
  navbar.className = "pos-navbar";

  // 2.
  const h1 = document.createElement("h1");
  h1.textContent = title;

  // 3.
  const logoutButton = document.createElement("button");
  logoutButton.id = "logout-button"; //
  logoutButton.textContent = "登出";

  // 4.
  if (onLogout && typeof onLogout === "function") {
    logoutButton.addEventListener("click", onLogout);
  }

  // 5.
  navbar.appendChild(h1);
  navbar.appendChild(logoutButton);

  return navbar;
}
