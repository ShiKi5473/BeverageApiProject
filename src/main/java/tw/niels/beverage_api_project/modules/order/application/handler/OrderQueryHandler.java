package tw.niels.beverage_api_project.modules.order.application.handler;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.application.query.GetOnlineOrderTicketQuery;
import tw.niels.beverage_api_project.modules.order.application.query.GetOrderQuery;
import tw.niels.beverage_api_project.modules.order.application.query.GetOrdersQuery;
import tw.niels.beverage_api_project.modules.order.application.service.OnlineOrderTicketService;
import tw.niels.beverage_api_project.modules.order.dto.OnlineOrderTicketResponseDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderResponseDto;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderJpaRepository;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 訂單查詢 Handler（唯讀，CQRS 讀取端）
 *
 * 修改說明（P0-8、P3 效能）：
 * - 讀取端不再經過 Domain Model：直接由 OrderJpaRepository（entity graph 一次載入品項、商品、
 *   規格、選項、付款方式）組出 OrderResponseDto，options 永不為 null，舊訂單（無快照）也能讀出。
 * - 列表查詢新增下單時間區間（預設最近 24 小時）與筆數上限，分兩步查詢（先取 ID 再抓明細），
 *   避免「集合 fetch + 分頁」在記憶體中分頁，也避免一次載入整店歷史訂單。
 * - 新增線上訂單 ticket 查詢（只限下單者本人）。
 */
@Component
@Transactional(readOnly = true)
public class OrderQueryHandler {

    /** 訂單列表單次最多回傳筆數（回應格式仍為 List，不改成分頁物件） */
    static final int MAX_LIST_SIZE = 500;

    /** 未指定 from 時預設查詢的時間長度 */
    static final Duration DEFAULT_RANGE = Duration.ofHours(24);

    /**
     * 查詢「進行中」狀態且未指定 from 時的預設時間長度。
     * 修正：KDS / POS 初次載入只帶 status（待接單、製作中、待取餐…），若沿用 24 小時，
     * 跨夜仍未完成的訂單會從畫面消失（SSE 也不會重推）；進行中訂單數量本來就有限，放寬到 30 天。
     */
    static final Duration ACTIVE_STATUS_DEFAULT_RANGE = Duration.ofDays(30);

    /** 進行中（非終態）的訂單狀態 */
    private static final Set<OrderStatus> ACTIVE_STATUSES = EnumSet.of(
        OrderStatus.PENDING, OrderStatus.AWAITING_ACCEPTANCE, OrderStatus.PREPARING,
        OrderStatus.READY_FOR_PICKUP, OrderStatus.HELD);

    /** 未指定 to 時往後多留的緩衝，避免剛建立的訂單因毫秒誤差被排除 */
    private static final Duration NOW_TOLERANCE = Duration.ofMinutes(1);

    private final OrderJpaRepository orderJpaRepository;
    private final OnlineOrderTicketService ticketService;
    private final Clock clock;

    public OrderQueryHandler(OrderJpaRepository orderJpaRepository,
                             OnlineOrderTicketService ticketService,
                             Clock clock) {
        this.orderJpaRepository = orderJpaRepository;
        this.ticketService = ticketService;
        this.clock = clock;
    }

    /**
     * 查詢單一訂單。
     *
     * @return 訂單回應 DTO
     * @throws ResourceNotFoundException 訂單不存在或不屬於該品牌
     */
    public OrderResponseDto handle(GetOrderQuery query) {
        OrderEntity entity = orderJpaRepository.findByBrand_IdAndId(query.brandId(), query.orderId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到訂單: " + query.orderId()));
        return OrderResponseDto.fromEntity(entity);
    }

    /**
     * 查詢分店訂單列表（依下單時間新到舊）。
     *
     * from / to 皆可省略：預設為「現在往前 24 小時」（只查進行中狀態時為 30 天）；
     * 最多回傳 {@value #MAX_LIST_SIZE} 筆。
     *
     * @return 訂單回應 DTO 清單
     * @throws BadRequestException from 不早於 to
     */
    public List<OrderResponseDto> handle(GetOrdersQuery query) {
        Instant now = Instant.now(clock);
        Instant to = query.to() != null ? query.to() : now.plus(NOW_TOLERANCE);
        // 修正：只查進行中狀態時放寬預設區間，避免跨夜未完成的訂單在 KDS / POS 重新整理後消失
        Duration defaultRange = ACTIVE_STATUSES.contains(query.status())
            ? ACTIVE_STATUS_DEFAULT_RANGE : DEFAULT_RANGE;
        Instant from = query.from() != null ? query.from()
            : (query.to() != null ? query.to() : now).minus(defaultRange);
        if (!from.isBefore(to)) {
            throw new BadRequestException("查詢區間錯誤：from 必須早於 to");
        }

        // 第一步：只取 ID（索引查詢 + 筆數上限）
        Pageable limit = PageRequest.of(0, MAX_LIST_SIZE);
        List<Long> ids = query.status() == null
            ? orderJpaRepository.findIdsByStoreAndOrderTimeRange(
                query.brandId(), query.storeId(), Date.from(from), Date.from(to), limit)
            : orderJpaRepository.findIdsByStoreAndStatusAndOrderTimeRange(
                query.brandId(), query.storeId(), query.status(), Date.from(from), Date.from(to), limit);
        if (ids.isEmpty()) {
            return List.of();
        }

        // 第二步：一次載入明細（單一 SQL，避免 N+1），再依第一步的順序（新到舊）排列
        Map<Long, Integer> position = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            position.put(ids.get(i), i);
        }
        return orderJpaRepository.findAllWithDetailsByBrandIdAndIdIn(query.brandId(), ids).stream()
            .sorted(Comparator.comparingInt(o -> position.getOrDefault(o.getId(), Integer.MAX_VALUE)))
            .map(OrderResponseDto::fromEntity)
            .toList();
    }

    /**
     * 查詢線上訂單 ticket 狀態（新增，只限下單者本人）。
     *
     * 為了不洩漏其他人的 ticket 是否存在，品牌或下單者不符時一律回 404。
     * 若 Redis 中的 ticket 仍為 QUEUED，但資料庫已有該 requestId 的訂單（例如更新 ticket 失敗），
     * 以資料庫結果為準回傳 CREATED。
     *
     * @return ticket 狀態與訂單目前狀態
     * @throws ResourceNotFoundException ticket 不存在、已過期或不屬於查詢者
     */
    public OnlineOrderTicketResponseDto handle(GetOnlineOrderTicketQuery query) {
        OnlineOrderTicketService.Ticket ticket = ticketService.find(query.ticketId())
            .filter(t -> Objects.equals(t.brandId(), query.brandId())
                && Objects.equals(t.requester(), query.requester()))
            .orElseThrow(() -> new ResourceNotFoundException("找不到線上訂單請求: " + query.ticketId()));

        // 有訂單 ID：補上訂單目前狀態（例如店家接單後變成 PREPARING）
        if (ticket.orderId() != null) {
            String orderStatus = orderJpaRepository.findStatusByBrandIdAndId(query.brandId(), ticket.orderId())
                .map(Enum::name)
                .orElse(null);
            return new OnlineOrderTicketResponseDto(ticket.ticketId(), ticket.status().name(),
                ticket.orderId(), ticket.orderNumber(), orderStatus, ticket.message());
        }

        // 仍在排隊：以資料庫備援確認是否其實已建立
        if (ticket.status() == OnlineOrderTicketService.TicketStatus.QUEUED) {
            Optional<OrderEntity> created = orderJpaRepository
                .findSummaryByBrandIdAndRequestId(query.brandId(), ticket.ticketId());
            if (created.isPresent()) {
                OrderEntity order = created.get();
                return new OnlineOrderTicketResponseDto(ticket.ticketId(),
                    OnlineOrderTicketService.TicketStatus.CREATED.name(),
                    order.getId(), order.getOrderNumber(),
                    order.getStatus() != null ? order.getStatus().name() : null,
                    OnlineOrderTicketService.MSG_CREATED);
            }
        }

        return new OnlineOrderTicketResponseDto(ticket.ticketId(), ticket.status().name(),
            null, null, null, ticket.message());
    }
}
