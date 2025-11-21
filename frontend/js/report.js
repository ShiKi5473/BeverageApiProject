// frontend/js/report.js
import * as echarts from 'echarts';
import '@material/web/button/filled-button.js';
import { createNavbar } from "./components/Navbar.js";

// 狀態變數
let revenueChartInstance = null;
let productChartInstance = null;

document.addEventListener("DOMContentLoaded", () => {
    // 1. 初始化 Navbar
    const layout = document.querySelector(".report-layout");
    const navbar = createNavbar("營收報表中心", () => {
        localStorage.removeItem("accessToken");
        localStorage.removeItem("brandId");
        window.location.href = "login.html";
    });
    // 插入到 layout 的第一列位置 (取代原本的 placeholder，或直接 prepend)
    const placeholder = document.getElementById("navbar-placeholder");
    if(placeholder) placeholder.replaceWith(navbar);

    // 2. 初始化日期選擇器 (預設為過去 7 天)
    const endDateInput = document.getElementById("end-date");
    const startDateInput = document.getElementById("start-date");

    const today = new Date();
    const lastWeek = new Date();
    lastWeek.setDate(today.getDate() - 6);

    endDateInput.valueAsDate = today;
    startDateInput.valueAsDate = lastWeek;

    // 3. 初始化 ECharts 實例 (但先不放入資料)
    initCharts();

    // 4. 綁定查詢按鈕
    document.getElementById("search-btn").addEventListener("click", () => {
        loadReportData();
    });

    // 5. 處理 RWD：視窗縮放時重繪圖表
    window.addEventListener("resize", () => {
        revenueChartInstance?.resize();
        productChartInstance?.resize();
    });

    // 6. 初次載入
    loadReportData();
});

function initCharts() {
    const revenueDom = document.getElementById('revenue-chart');
    const productDom = document.getElementById('product-chart');

    if (revenueDom) {
        revenueChartInstance = echarts.init(revenueDom);
    }
    if (productDom) {
        productChartInstance = echarts.init(productDom);
    }
}

/**
 * 主邏輯：載入並渲染所有報表資料
 * (下一階段我們將在此處呼叫 API)
 */
async function loadReportData() {
    const startDate = document.getElementById("start-date").value;
    const endDate = document.getElementById("end-date").value;
    const storeId = localStorage.getItem("storeId"); // 若為 null 則代表品牌管理員

    console.log(`正在查詢報表: ${startDate} ~ ${endDate}, StoreId: ${storeId}`);

    // 顯示 Loading 狀態
    revenueChartInstance?.showLoading();
    productChartInstance?.showLoading();

    try {
        // TODO: 下一步驟在此呼叫 api.js
        // const dailyStats = await getStoreDailyStats(...);
        // const productStats = await getProductSalesRanking(...);

        // 模擬延遲與假資料 (測試畫面用)
        setTimeout(() => {
            renderRevenueChart([], []); // 傳入空陣列暫時測試
            renderProductChart([], []);

            revenueChartInstance?.hideLoading();
            productChartInstance?.hideLoading();
        }, 500);

    } catch (error) {
        console.error("載入報表失敗", error);
        alert("載入失敗：" + error.message);
        revenueChartInstance?.hideLoading();
        productChartInstance?.hideLoading();
    }
}

function renderRevenueChart(dates, revenues) {
    if (!revenueChartInstance) return;

    // ECharts 設定檔
    const option = {
        tooltip: {
            trigger: 'axis'
        },
        xAxis: {
            type: 'category',
            data: dates // ['2023-10-01', '2023-10-02', ...]
        },
        yAxis: {
            type: 'value',
            name: '金額 (NT$)'
        },
        series: [
            {
                data: revenues, // [5000, 6200, ...]
                type: 'line',
                smooth: true,
                itemStyle: { color: '#007bff' },
                areaStyle: {
                    color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
                        { offset: 0, color: 'rgba(0, 123, 255, 0.5)' },
                        { offset: 1, color: 'rgba(0, 123, 255, 0.0)' }
                    ])
                }
            }
        ]
    };
    revenueChartInstance.setOption(option);
}

function renderProductChart(productNames, quantities) {
    if (!productChartInstance) return;

    const option = {
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'shadow' }
        },
        grid: {
            left: '3%',
            right: '4%',
            bottom: '3%',
            containLabel: true
        },
        xAxis: {
            type: 'value',
            boundaryGap: [0, 0.01]
        },
        yAxis: {
            type: 'category',
            data: productNames, // ['珍珠奶茶', '四季春', ...]
            inverse: true // 讓第一名排在最上面
        },
        series: [
            {
                name: '銷售杯數',
                type: 'bar',
                data: quantities, // [120, 85, ...]
                itemStyle: { color: '#28a745' }
            }
        ]
    };
    productChartInstance.setOption(option);
}