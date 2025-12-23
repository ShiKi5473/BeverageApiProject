import { createNavbar } from './components/Navbar.js';
import { getInventoryItems, submitInventoryAudit } from './api.js';

// 初始化
document.addEventListener('DOMContentLoaded', async () => {
    // 1. 掛載導覽列
    initNavbar();

    // 2. 顯示當前經手人與日期
    initStaffInfo();

    // 3. 載入盤點資料
    await loadInventoryItems();

    // 4. 綁定提交按鈕
    document.getElementById('btn-submit').addEventListener('click', submitAudit);
});

/**
 * 初始化並掛載導覽列
 */
function initNavbar() {
    const navbarRoot = document.getElementById('navbar-root');
    if (!navbarRoot) return;

    // 定義登出邏輯
    const handleLogout = () => {
        if (confirm("確定要登出嗎？")) {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('brandId');
            window.location.href = 'login.html';
        }
    };

    // 呼叫 createNavbar 函式 (不需要 new)
    // 參數 1: 標題
    // 參數 2: 登出回呼函式
    const headerElement = createNavbar("庫存盤點作業", handleLogout);

    // 清空掛載點並加入新的 Header
    navbarRoot.innerHTML = '';
    navbarRoot.appendChild(headerElement);
}

/**
 * 初始化員工資訊 (從 Token 解析)
 */
function initStaffInfo() {
    // 設定日期
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('audit-date').textContent = today;

    // 解析 Token 取得員工名稱
    const token = localStorage.getItem('accessToken');
    if (token) {
        try {
            const payload = JSON.parse(atob(token.split('.')[1])); // 簡單解碼 JWT Payload
            // 優先顯示 name，如果沒有則顯示 sub (通常是帳號)
            const staffName = payload.name || payload.sub || '店員';
            document.getElementById('staff-name').textContent = staffName;
            document.getElementById('staff-name').classList.remove('loading-text');
        } catch (e) {
            console.error('Token 解析失敗', e);
            document.getElementById('staff-name').textContent = '未知使用者';
        }
    } else {
        window.location.href = 'login.html';
    }
}

/**
 * 載入需盤點的庫存項目
 */
async function loadInventoryItems() {
    const listContainer = document.getElementById('audit-list');
    const loadingIndicator = document.getElementById('loading-indicator');

    if(loadingIndicator) loadingIndicator.style.display = 'block';
    if(listContainer) listContainer.innerHTML = '';

    try {
        const items = await getInventoryItems();

        if (!items || items.length === 0) {
            document.getElementById('empty-state').style.display = 'block';
            return;
        }

        items.forEach(item => {
            const row = createAuditRow(item);
            listContainer.appendChild(row);
        });

        updateProgress();
        document.getElementById('btn-submit').disabled = false;

    } catch (error) {
        console.error('載入庫存失敗:', error);
        alert(`無法載入庫存列表: ${error.message}`);
        // 如果錯誤是因為 "User not found" 或 "不屬於任何分店"，可能要導回登入頁
        if (error.message.includes("不屬於任何分店")) {
            window.location.href = 'login.html';
        }
    } finally {
        if(loadingIndicator) loadingIndicator.style.display = 'none';
    }
}

/**
 * 建立單一盤點列 (DOM 操作)
 */
function createAuditRow(item) {
    const div = document.createElement('div');
    div.className = 'audit-item';
    div.dataset.id = item.id; // 綁定 ID 方便提交時抓取
    div.dataset.systemQty = item.quantity;

    div.innerHTML = `
        <div class="item-info">
            <span class="item-name">${item.name}</span>
            <span class="item-unit">${item.unit}</span>
        </div>
        
        <div class="data-col">
            <span class="col-label">系統庫存</span>
            <div class="system-stock">${item.quantity}</div>
        </div>

        <div class="data-col">
            <span class="col-label">實際盤點</span>
            <input type="number" class="audit-input" 
                   value="${item.quantity}" 
                   onfocus="this.select()"
                   inputmode="decimal">
            
            <input type="date" class="audit-expiry-input" 
                   title="若知曉效期請填寫，以利系統建立批次">

            <div class="warning-msg" style="display:none;"></div>
        </div>

        <div class="data-col">
            <span class="col-label">差異</span>
            <div class="variance-display variance-zero">0</div>
        </div>
    `;

    // 綁定輸入事件: 計算差異
    const input = div.querySelector('.audit-input');
    const expiryInput = div.querySelector('.audit-expiry-input');
    const varianceDisplay = div.querySelector('.variance-display');
    const warningMsg = div.querySelector('.warning-msg');

    input.addEventListener('input', () => {
        const actual = parseFloat(input.value) || 0;
        const system = parseFloat(item.quantity);
        if (isNaN(actual)) {
            varianceDisplay.textContent = '-';
            warningMsg.style.display = 'none';
            expiryInput.style.display = 'none';
            return;
        }
        const diff = actual - system;

        // 更新差異數字與顏色
        varianceDisplay.textContent = diff > 0 ? `+${diff}` : diff;
        varianceDisplay.className = 'variance-display';
        warningMsg.style.display = 'none';
        expiryInput.style.display = 'none';
        if (diff > 0) {
            // --- 🔥 盤盈 (變多) ---
            varianceDisplay.classList.add('variance-positive');

            // 1. 顯示效期輸入框
            expiryInput.style.display = 'block';

            // 2. 顯示警示/提示訊息
            warningMsg.style.display = 'block';
            if (diff > 5) {
                warningMsg.textContent = "⚠️ 數量增加較多，請確認是否為進貨？(選填效期)";
            } else {
                warningMsg.textContent = "ℹ️ 庫存回補：建議填寫效期，若不填則由系統推斷。";
            }

        } else if (diff < 0) {
            // --- 💧 盤損 (變少) ---
            varianceDisplay.classList.add('variance-negative');
            // 盤損不需要填效期 (FIFO 自動扣)
        } else {
            varianceDisplay.classList.add('variance-zero');
        }

        updateProgress();
    });

    return div;
}

/**
 * 更新底部進度資訊
 */
function updateProgress() {
    const total = document.querySelectorAll('.audit-item').length;
    // 這裡簡單定義：只要輸入框有值就算已盤點 (其實預設都有值，可以改成偵測是否有修改)
    // 但實務上，盤點通常需要確認每一項，這裡顯示總數即可
    document.getElementById('progress-count').textContent = `${total} 項`;
}

/**
 * 提交盤點資料
 */
async function submitAudit() {
    const btn = document.getElementById('btn-submit');
    const rows = document.querySelectorAll('.audit-item');
    const auditData = [];

    // 收集資料
    rows.forEach(row => {
        const id = row.dataset.id;
        const systemQty = parseFloat(row.dataset.systemQty);

        const inputVal = row.querySelector('.audit-input').value;
        const actualQty = parseFloat(inputVal);

        // 取得效期輸入框的值
        const expiryVal = row.querySelector('.audit-expiry-input').value;

        if (isNaN(actualQty)) return;

        const diff = actualQty - systemQty;

        // 構建 DTO Item
        const itemPayload = {
            inventoryItemId: id,
            actualQuantity: actualQty,
            itemNote: ""
        };

        if (diff > 0 && expiryVal) {
            itemPayload.gainedItemExpiryDate = expiryVal;
        }

        auditData.push(itemPayload);

    });
    if (auditData.length === 0) return;

    if (!confirm(`確認提交共 ${auditData.length} 筆盤點資料？`)) return;

    try {
        btn.disabled = true;
        btn.textContent = '提交中...';

        const requestPayload = {
            note: "日常盤點 (Web)",
            items: auditData
        };

        // 呼叫後端 API
        const response = await submitInventoryAudit(requestPayload);
        if (!response.ok) {
            // 如果後端回傳錯誤狀態碼 (4xx, 5xx)，拋出錯誤
            const errorText = await response.text();
            throw new Error(errorText || '伺服器回應錯誤');
        }
        alert('✅ 盤點完成！庫存已更新。');
        window.location.reload(); // 重新整理

    } catch (error) {
        console.error('提交失敗', error);
        alert(`提交失敗: ${error.message}`);
        btn.disabled = false;
        btn.textContent = '提交盤點報告 (Submit)';
    }

}