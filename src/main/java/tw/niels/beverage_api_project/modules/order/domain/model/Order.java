package tw.niels.beverage_api_project.modules.order.domain.model;

import tw.niels.beverage_api_project.modules.order.domain.event.OrderCancelledEvent;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderClosedEvent;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderPlacedEvent;
import tw.niels.beverage_api_project.modules.order.domain.event.OrderStatusChangedEvent;
import tw.niels.beverage_api_project.modules.order.domain.exception.OrderDomainException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Aggregate Root
 * ============================================================
 *
 * 訂單聚合根（Aggregate Root）
 *
 * 封裝完整的訂單生命週期，是 Order Bounded Context 的核心。
 * 所有狀態轉換必須透過此類別的業務方法觸發，外部不得直接修改狀態。
 *
 * 業務規則：
 * - 訂單只能從允許的狀態執行對應操作（見各方法的前置條件說明）
 * - 最終態（CLOSED / CANCELLED）的訂單不允許任何操作
 * - 積點使用後若訂單取消，需透過 OrderCancelledEvent 通知退還
 * - 訂單完成時（CLOSED）的積點累積，透過 OrderClosedEvent 通知
 *
 * 不變量（Invariants）：
 * - finalAmount ≥ 0（最低為 0 元，不得為負）
 * - totalAmount = sum(items.subtotal)
 * - finalAmount = totalAmount - discountAmount（最低歸零）
 * - 一旦進入 CLOSED 或 CANCELLED，狀態不再改變
 *
 * 發布事件：
 * - OrderPlacedEvent：訂單建立時
 * - OrderStatusChangedEvent：狀態轉換（除 CLOSED 和 CANCELLED 外）
 * - OrderClosedEvent：訂單完成取餐時
 * - OrderCancelledEvent：訂單取消時
 *
 * 與其他 Context 的互動：
 * - 發布 OrderClosedEvent → Inventory, Member, Report Application 訂閱
 * - 發布 OrderCancelledEvent → Member Application 訂閱（退還積點）
 * ============================================================
 */
public class Order {

    // ─── 可取消的訂單狀態集合（用於前置條件驗證）────────────────────────────
    private static final Set<OrderStatus> CANCELLABLE_STATUSES = Collections.unmodifiableSet(
            EnumSet.of(
                    OrderStatus.PENDING,
                    OrderStatus.HELD,
                    OrderStatus.AWAITING_ACCEPTANCE,
                    OrderStatus.PREPARING,
                    OrderStatus.READY_FOR_PICKUP
            )
    );

    // ─── 可結帳的訂單狀態集合（POS 結帳）────────────────────────────────────
    private static final Set<OrderStatus> PAYABLE_STATUSES = Collections.unmodifiableSet(
            EnumSet.of(OrderStatus.PENDING, OrderStatus.HELD)
    );

    // ─── 識別欄位 ─────────────────────────────────────────────────────────

    /** 訂單 ID（TSID），由 Infrastructure Layer 在持久化後設定 */
    private Long id;

    /** 訂單所屬品牌 ID */
    private final Long brandId;

    /** 訂單所屬店家 ID */
    private final Long storeId;

    /**
     * 服務此訂單的員工 ID（POS 現場點餐時設定，線上訂單可為 null）
     */
    private Long staffId;

    /**
     * 下單的會員 ID（POS 結帳時設定，訪客訂單為 null）
     * 注意：線上訂單建立時已知會員，POS 訂單在 processPayment 時才設定
     */
    private Long memberId;

    // ─── 訂單資訊欄位 ──────────────────────────────────────────────────────

    /** 訂單號碼（人類可讀，如 A001、B023） */
    private final OrderNumber orderNumber;

    /** 訂單當前狀態 */
    private OrderStatus status;

    /** 訂單原始總金額（品項小計加總，未扣除折扣） */
    private Money totalAmount;

    /** 折扣金額（積點折抵 + 促銷折扣之總和） */
    private Money discountAmount;

    /** 最終實付金額（totalAmount - discountAmount，最低為 0） */
    private Money finalAmount;

    /** 本次訂單使用的積點數量（0 表示未使用積點） */
    private Long pointsUsed;

    /**
     * 本次訂單累積的積點數量
     * 在訂單 CLOSED 時計算並記錄，0 表示訪客訂單或不符合積點條件
     */
    private Long pointsEarned;

    /** 結帳使用的支付方式 ID（POS 結帳後設定，線上訂單可預先設定） */
    private Long paymentMethodId;

    /** 顧客備註（如：多打包一個袋子） */
    private final String customerNote;

    /** 訂單建立時間（不可修改） */
    private final Instant orderTime;

    /** 訂單完成時間（顧客取餐並完成結帳時設定） */
    private Instant completedTime;

    // ─── 集合欄位 ──────────────────────────────────────────────────────────

    /** 訂單品項清單，由 Order Aggregate 管理，不允許外部直接修改 */
    private List<OrderItem> items;

    /**
     * 會員快照：結帳時儲存會員當下的基本資料，
     * 確保歷史訂單不受會員資料異動影響
     */
    private MemberSnapshot memberSnapshot;

    /**
     * 領域事件收集器（Domain Event Collector）
     *
     * 業務方法執行時將事件加入此清單，
     * 由 Application Layer 的 Handler 在儲存 Aggregate 後統一發布。
     * 使用 {@link #pullDomainEvents()} 取出並清空。
     */
    private final List<Object> domainEvents = new ArrayList<>();

    // ─── 建構子（私有，強制使用靜態工廠方法）────────────────────────────────

    /**
     * 私有建構子：僅供靜態工廠方法或 Infrastructure Mapper 使用。
     */
    private Order(Long brandId, Long storeId, Long staffId,
                  OrderNumber orderNumber, List<OrderItem> items,
                  Money totalAmount, String customerNote) {
        this.brandId = brandId;
        this.storeId = storeId;
        this.staffId = staffId;
        this.orderNumber = orderNumber;
        this.items = new ArrayList<>(items);
        this.totalAmount = totalAmount;
        this.finalAmount = totalAmount;   // 初始時無折扣
        this.discountAmount = Money.ZERO;
        this.pointsUsed = 0L;
        this.pointsEarned = 0L;
        this.customerNote = customerNote;
        this.orderTime = Instant.now();
        // 初始狀態由工廠方法決定，預設為 PENDING
        this.status = OrderStatus.PENDING;
    }

    /**
     * 供 Infrastructure Mapper 重建 Domain Model 的建構子。
     * 此建構子不執行業務驗證，僅做欄位對應。
     * 命名用 {@code reconstitute} 強調「重建」語意，而非「建立新訂單」。
     */
    private Order(Long id, Long brandId, Long storeId, Long staffId, Long memberId,
                  OrderNumber orderNumber, OrderStatus status,
                  Money totalAmount, Money discountAmount, Money finalAmount,
                  Long pointsUsed, Long pointsEarned, Long paymentMethodId,
                  String customerNote, Instant orderTime, Instant completedTime,
                  List<OrderItem> items, MemberSnapshot memberSnapshot) {
        this.id = id;
        this.brandId = brandId;
        this.storeId = storeId;
        this.staffId = staffId;
        this.memberId = memberId;
        this.orderNumber = orderNumber;
        this.status = status;
        this.totalAmount = totalAmount;
        this.discountAmount = discountAmount;
        this.finalAmount = finalAmount;
        this.pointsUsed = pointsUsed;
        this.pointsEarned = pointsEarned;
        this.paymentMethodId = paymentMethodId;
        this.customerNote = customerNote;
        this.orderTime = orderTime;
        this.completedTime = completedTime;
        this.items = new ArrayList<>(items);
        this.memberSnapshot = memberSnapshot;
    }

    // ─── 靜態工廠方法 ──────────────────────────────────────────────────────

    /**
     * 建立一張新的現場訂單（POS 點餐流程）。
     *
     * 後置狀態（Post-state）：PENDING
     * 發布事件：OrderPlacedEvent
     *
     * @param brandId      品牌 ID
     * @param storeId      店家 ID
     * @param staffId      服務員工 ID
     * @param orderNumber  訂單號碼
     * @param items        訂單品項清單（至少一項）
     * @param customerNote 顧客備註（可為 null）
     * @return 狀態為 PENDING 的新訂單
     * @throws OrderDomainException 若 items 為空
     */
    public static Order place(Long brandId, Long storeId, Long staffId,
                              OrderNumber orderNumber, List<OrderItem> items,
                              String customerNote) {
        // 驗證品項不得為空
        if (items == null || items.isEmpty()) {
            throw new OrderDomainException("訂單必須包含至少一個品項");
        }

        // 計算總金額
        Money totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(Money.ZERO, Money::add);

        Order order = new Order(brandId, storeId, staffId, orderNumber, items, totalAmount, customerNote);

        // 發布「訂單已建立」事件
        order.domainEvents.add(
                OrderPlacedEvent.of(order.id, brandId, storeId, orderNumber.value())
        );

        return order;
    }

    /**
     * 供 Infrastructure Mapper 從資料庫重建 Domain Model。
     * 不發布任何事件，不執行業務驗證。
     */
    public static Order reconstitute(Long id, Long brandId, Long storeId, Long staffId, Long memberId,
                                     OrderNumber orderNumber, OrderStatus status,
                                     Money totalAmount, Money discountAmount, Money finalAmount,
                                     Long pointsUsed, Long pointsEarned, Long paymentMethodId,
                                     String customerNote, Instant orderTime, Instant completedTime,
                                     List<OrderItem> items, MemberSnapshot memberSnapshot) {
        return new Order(id, brandId, storeId, staffId, memberId, orderNumber, status,
                totalAmount, discountAmount, finalAmount, pointsUsed, pointsEarned,
                paymentMethodId, customerNote, orderTime, completedTime, items, memberSnapshot);
    }

    // ─── 業務方法（狀態轉換）─────────────────────────────────────────────

    /**
     * 暫存訂單（POS 服務員暫時儲存訂單，稍後繼續操作）。
     *
     * 前置條件（Pre-condition）：訂單狀態必須為 PENDING
     * 後置狀態（Post-state）：HELD
     * 發布事件：OrderStatusChangedEvent
     *
     * @throws OrderDomainException 若當前狀態不為 PENDING
     */
    public void hold() {
        validateStatus(OrderStatus.PENDING, "暫存（hold）");
        OrderStatus oldStatus = this.status;
        this.status = OrderStatus.HELD;
        domainEvents.add(OrderStatusChangedEvent.of(this.id, oldStatus, this.status));
    }

    /**
     * 更新訂單品項（僅允許在付款前修改，如 POS 服務員調整品項）。
     *
     * 前置條件（Pre-condition）：訂單狀態必須為 PENDING 或 HELD
     * 後置狀態（Post-state）：狀態不變
     * 發布事件：無
     *
     * @param newItems       更新後的品項清單（至少一項）
     * @param newTotalAmount 更新後的總金額
     * @throws OrderDomainException 若狀態不允許修改或品項為空
     */
    public void updateItems(List<OrderItem> newItems, Money newTotalAmount) {
        // 只有付款前（PENDING 或 HELD）才能修改品項
        if (!PAYABLE_STATUSES.contains(this.status)) {
            throw new OrderDomainException(
                    "訂單狀態為 " + this.status + "，無法修改品項內容（僅允許 PENDING 或 HELD 狀態）"
            );
        }
        if (newItems == null || newItems.isEmpty()) {
            throw new OrderDomainException("更新後的訂單品項不得為空");
        }
        // 更新品項與金額，重置折扣（品項異動後折扣需重新計算）
        this.items = new ArrayList<>(newItems);
        this.totalAmount = newTotalAmount;
        this.finalAmount = newTotalAmount;
        this.discountAmount = Money.ZERO;
        this.pointsUsed = 0L;
    }

    /**
     * 進行結帳付款（POS 現場結帳流程）。
     *
     * Application Layer 負責在呼叫此方法前：
     * 1. 計算積點折抵金額（pointsDiscount）
     * 2. 計算促銷折扣金額（promoDiscount）
     * 3. 合計總折扣（totalDiscount = pointsDiscount + promoDiscount）
     * 4. 從系統扣減積點（呼叫 MemberPointService）
     * 5. 建立 MemberSnapshot
     *
     * 前置條件（Pre-condition）：訂單狀態必須為 PENDING 或 HELD
     * 後置狀態（Post-state）：PREPARING
     * 發布事件：OrderStatusChangedEvent
     *
     * @param paymentMethodId 支付方式 ID
     * @param totalDiscount   總折扣金額（積點 + 促銷，由 Application Layer 計算）
     * @param pointsUsed      使用的積點數量（0 表示未使用）
     * @param memberId        會員 ID（訪客結帳可為 null）
     * @param memberSnapshot  會員快照（memberId 不為 null 時必填）
     * @throws OrderDomainException 若當前狀態不允許結帳
     */
    public void processPayment(Long paymentMethodId, Money totalDiscount,
                               Long pointsUsed, Long memberId, MemberSnapshot memberSnapshot) {
        // 前置狀態驗證
        if (!PAYABLE_STATUSES.contains(this.status)) {
            throw new OrderDomainException(
                    "訂單狀態為 " + this.status + "，無法進行結帳（僅允許 PENDING 或 HELD 狀態）"
            );
        }

        OrderStatus oldStatus = this.status;

        // 設定支付資訊
        this.paymentMethodId = paymentMethodId;
        this.memberId = memberId;
        this.memberSnapshot = memberSnapshot;
        this.pointsUsed = (pointsUsed != null) ? pointsUsed : 0L;

        // 計算折扣與最終金額
        this.discountAmount = (totalDiscount != null) ? totalDiscount : Money.ZERO;
        // subtract 內部確保最終金額不低於 0
        this.finalAmount = this.totalAmount.subtract(this.discountAmount);

        // 狀態轉換：→ PREPARING
        this.status = OrderStatus.PREPARING;
        domainEvents.add(OrderStatusChangedEvent.of(this.id, oldStatus, this.status));
    }

    /**
     * 店家接單（線上訂單流程：店家確認接受訂單後開始製作）。
     *
     * 前置條件（Pre-condition）：訂單狀態必須為 AWAITING_ACCEPTANCE
     * 後置狀態（Post-state）：PREPARING
     * 發布事件：OrderStatusChangedEvent（KDS 監聽以顯示新訂單）
     *
     * @throws OrderDomainException 若當前狀態不為 AWAITING_ACCEPTANCE
     */
    public void accept() {
        validateStatus(OrderStatus.AWAITING_ACCEPTANCE, "接單（accept）");
        OrderStatus oldStatus = this.status;
        this.status = OrderStatus.PREPARING;
        domainEvents.add(OrderStatusChangedEvent.of(this.id, oldStatus, this.status));
    }

    /**
     * 標記製作完成（KDS 廚房確認飲品製作完成，通知顧客取餐）。
     *
     * 前置條件（Pre-condition）：訂單狀態必須為 PREPARING
     * 後置狀態（Post-state）：READY_FOR_PICKUP
     * 發布事件：OrderStatusChangedEvent
     *
     * @throws OrderDomainException 若當前狀態不為 PREPARING
     */
    public void markReadyForPickup() {
        validateStatus(OrderStatus.PREPARING, "標記製作完成（markReadyForPickup）");
        OrderStatus oldStatus = this.status;
        this.status = OrderStatus.READY_FOR_PICKUP;
        domainEvents.add(OrderStatusChangedEvent.of(this.id, oldStatus, this.status));
    }

    /**
     * 完成訂單（顧客取餐，訂單歸檔）。
     *
     * Application Layer 負責在呼叫此方法前計算 pointsEarned
     * （根據 finalAmount 與品牌積點規則）。
     *
     * 前置條件（Pre-condition）：訂單狀態必須為 READY_FOR_PICKUP
     * 後置狀態（Post-state）：CLOSED（最終態）
     * 發布事件：OrderClosedEvent（Inventory、Member、Report 訂閱）
     *
     * @param pointsEarned 本次訂單累積的積點（0 表示訪客或不符條件，由 Application Layer 計算）
     * @throws OrderDomainException 若當前狀態不為 READY_FOR_PICKUP
     */
    public void close(Long pointsEarned) {
        validateStatus(OrderStatus.READY_FOR_PICKUP, "完成訂單（close）");

        this.completedTime = Instant.now();
        this.pointsEarned = (pointsEarned != null) ? pointsEarned : 0L;
        this.status = OrderStatus.CLOSED;

        // 發布「訂單完成」事件，攜帶財務資訊供下游系統使用
        domainEvents.add(
                OrderClosedEvent.of(this.id, this.finalAmount, this.pointsEarned, this.memberId)
        );
    }

    /**
     * 取消訂單。
     *
     * 任何「進行中」的狀態皆可取消（見 CANCELLABLE_STATUSES）。
     * 若訂單已使用積點，由 OrderCancelledEvent 通知 Member Application 退還。
     *
     * 前置條件（Pre-condition）：
     *   狀態必須為 PENDING、HELD、AWAITING_ACCEPTANCE、PREPARING 或 READY_FOR_PICKUP
     * 後置狀態（Post-state）：CANCELLED（最終態）
     * 發布事件：OrderCancelledEvent（Member Application 訂閱以退還積點）
     *
     * @throws OrderDomainException 若當前狀態為 CLOSED 或 CANCELLED（已終結，無法取消）
     */
    public void cancel() {
        if (!CANCELLABLE_STATUSES.contains(this.status)) {
            throw new OrderDomainException(
                    "訂單狀態為 " + this.status + "，已是最終態，無法取消"
            );
        }
        OrderStatus cancelledFromStatus = this.status;
        this.status = OrderStatus.CANCELLED;

        // 發布「訂單取消」事件，攜帶積點資訊供 Member Application 退還
        domainEvents.add(
                OrderCancelledEvent.of(this.id, cancelledFromStatus, this.pointsUsed, this.memberId)
        );
    }

    // ─── 領域事件管理 ──────────────────────────────────────────────────────

    /**
     * 取出並清空領域事件清單。
     *
     * Application Layer 的 Handler 在儲存 Aggregate 後呼叫此方法，
     * 取得待發布的事件清單，再透過 ApplicationEventPublisher 發布。
     *
     * 為什麼在儲存後才發布？
     * 確保事件只在資料成功持久化後才通知下游系統，避免「事件發出但儲存失敗」的不一致情況。
     *
     * @return 待發布的領域事件清單（不可修改的副本，原清單被清空）
     */
    public List<Object> pullDomainEvents() {
        List<Object> events = List.copyOf(this.domainEvents);
        this.domainEvents.clear();
        return events;
    }

    // ─── 私有輔助方法 ──────────────────────────────────────────────────────

    /**
     * 驗證當前狀態是否符合操作的前置條件。
     *
     * @param required      要求的狀態
     * @param operationName 操作名稱（用於錯誤訊息）
     * @throws OrderDomainException 若當前狀態不符合要求
     */
    private void validateStatus(OrderStatus required, String operationName) {
        if (this.status != required) {
            throw new OrderDomainException(
                    "無法執行 " + operationName + "：訂單狀態必須為 " + required + "，當前狀態為 " + this.status
            );
        }
    }

    // ─── Getters（無 public setters，保護 Aggregate 完整性）──────────────────

    /** 訂單 ID（TSID） */
    public Long getId() { return id; }

    /** 由 Infrastructure Mapper 在儲存後回填 ID */
    public void setId(Long id) { this.id = id; }

    public Long getBrandId() { return brandId; }
    public Long getStoreId() { return storeId; }
    public Long getStaffId() { return staffId; }
    public Long getMemberId() { return memberId; }
    public OrderNumber getOrderNumber() { return orderNumber; }
    public OrderStatus getStatus() { return status; }
    public Money getTotalAmount() { return totalAmount; }
    public Money getDiscountAmount() { return discountAmount; }
    public Money getFinalAmount() { return finalAmount; }
    public Long getPointsUsed() { return pointsUsed; }
    public Long getPointsEarned() { return pointsEarned; }
    public Long getPaymentMethodId() { return paymentMethodId; }
    public String getCustomerNote() { return customerNote; }
    public Instant getOrderTime() { return orderTime; }
    public Instant getCompletedTime() { return completedTime; }
    public MemberSnapshot getMemberSnapshot() { return memberSnapshot; }

    /** 回傳不可修改的品項清單，防止外部繞過 Aggregate 直接修改 */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
