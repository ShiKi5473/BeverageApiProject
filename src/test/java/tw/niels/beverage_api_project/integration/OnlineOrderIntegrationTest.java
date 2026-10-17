package tw.niels.beverage_api_project.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tw.niels.beverage_api_project.AbstractIntegrationTest;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.order.application.command.PlaceOnlineOrderCommand;
import tw.niels.beverage_api_project.modules.order.application.handler.PlaceOnlineOrderHandler;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
// Entity 類別使用 Entity 後綴，Spring Data Repository 使用 JpaRepository 後綴
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.OrderItemEntity;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderJpaRepository;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductVariantJpaRepository;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 線上點餐整合測試：API → RabbitMQ → Consumer → Database → ticket 查詢。
 *
 * 修改說明：
 * - 修正字串常值的 U+FFFD 亂碼（原本 "非同步測試" 有一處變成亂碼，斷言必失敗）。
 * - 不再依賴 DataSeeder 的種子資料（AbstractIntegrationTest 每個測試後會 TRUNCATE），改為自行建立品牌、分店、商品。
 * - 改用訪客身分（POST /api/v1/auth/guest 取得 token）走完整 HTTP 契約，驗證：
 *   202 + QUEUED、訂單初始狀態固定 AWAITING_ACCEPTANCE（請求中的 status 會被忽略）、綁定 guestRef、
 *   request_id = ticketId、GET /online-orders/{ticketId} 回 CREATED，且只有本人可查。
 * - 新增消費者冪等測試：同一 ticket 重複處理只會建立一筆訂單。
 */
@AutoConfigureMockMvc
public class OnlineOrderIntegrationTest extends AbstractIntegrationTest {

    private static final String NOTES = "非同步測試";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderJpaRepository orderRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private StoreJpaRepository storeJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private ProductVariantJpaRepository productVariantJpaRepository;

    @Autowired
    private PlaceOnlineOrderHandler placeOnlineOrderHandler;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private Long brandId;
    private Long storeId;
    private Long productId;
    private Long variantId;

    @BeforeEach
    void setUp() {
        // 自行建立測試資料（名稱加上亂數，避免與種子資料的唯一鍵衝突）
        BrandEntity brand = new BrandEntity();
        brand.setName("Online Order Brand " + UUID.randomUUID());
        brand = brandJpaRepository.save(brand);
        this.brandId = brand.getId();

        StoreEntity store = new StoreEntity();
        store.setBrand(brand);
        store.setName("Online Store");
        store = storeJpaRepository.save(store);
        this.storeId = store.getId();

        ProductEntity product = new ProductEntity();
        product.setBrand(brand);
        product.setName("珍珠奶茶");
        product.setBasePrice(new BigDecimal("50"));
        product.setStatus(ProductStatus.ACTIVE);
        product = productJpaRepository.save(product);
        this.productId = product.getId();

        ProductVariantEntity variant = new ProductVariantEntity();
        variant.setProduct(product);
        variant.setName("大杯");
        variant.setPrice(new BigDecimal("60"));
        variant.setSkuCode("BOBA-L-" + UUID.randomUUID());
        variant = productVariantJpaRepository.save(variant);
        this.variantId = variant.getId();
    }

    @Test
    @DisplayName("訪客非同步下單：API -> RabbitMQ -> Consumer -> Database，ticket 只限本人查詢")
    void testAsyncOrderFlow() throws Exception {
        // 1. 以訪客身分登入（guest token 內含 brandId）
        String guestToken = guestLogin("訪客小明");

        // 2. 送出線上訂單；刻意夾帶 status=CLOSED，驗證後端忽略並固定為 AWAITING_ACCEPTANCE
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productId", String.valueOf(productId)); // ID 以字串傳送（前端行為）
        item.put("variantId", String.valueOf(variantId));
        item.put("quantity", 2);
        item.put("notes", NOTES);
        item.put("optionIds", List.of());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("storeId", String.valueOf(storeId));
        body.put("status", "CLOSED");
        body.put("items", List.of(item));
        body.put("customerNote", "<b>請多拿吸管</b>");

        String response = mockMvc.perform(post("/api/v1/online-orders")
                        .header("Authorization", "Bearer " + guestToken)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted()) // 202 Accepted
                .andExpect(jsonPath("$.ticketId").exists())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andReturn().getResponse().getContentAsString();
        String ticketId = JsonPath.read(response, "$.ticketId");

        // 3. 輪詢 ticket，直到消費者建立訂單（CREATED）
        await().atMost(15, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() ->
                mockMvc.perform(get("/api/v1/online-orders/" + ticketId)
                                .header("Authorization", "Bearer " + guestToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.ticketId").value(ticketId))
                        .andExpect(jsonPath("$.status").value("CREATED"))
                        .andExpect(jsonPath("$.orderStatus").value("AWAITING_ACCEPTANCE"))
                        .andExpect(jsonPath("$.orderId").isString())
                        .andExpect(jsonPath("$.orderNumber").exists()));

        // 4. 驗證資料庫內容（request_id = ticketId）
        transactionTemplate.executeWithoutResult(tx -> {
            OrderEntity order = orderRepository.findByBrand_IdAndRequestId(brandId, ticketId)
                    .orElseThrow(() -> new AssertionError("找不到 requestId=" + ticketId + " 的訂單"));

            assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_ACCEPTANCE);
            assertThat(order.getGuestRef()).startsWith("GUEST:");
            assertThat(order.getMember()).isNull();
            assertThat(order.getOrderNumber()).matches(storeId + "-\\d{8}-0001");
            // 顧客備註經 Jsoup 清洗（移除 HTML 標籤）
            assertThat(order.getCustomerNote()).isEqualTo("請多拿吸管");
            // 單價為規格價 60 元，數量 2 → 120 元
            assertThat(order.getTotalAmount()).isEqualByComparingTo("120.00");

            assertThat(order.getItems()).hasSize(1);
            OrderItemEntity orderItem = order.getItems().iterator().next();
            assertThat(orderItem.getNotes()).isEqualTo(NOTES);
            assertThat(orderItem.getProductVariant().getId()).isEqualTo(variantId);
            assertThat(orderItem.getProductSnapshot()).isNotNull();
            assertThat(orderItem.getProductSnapshot().name()).isEqualTo("珍珠奶茶");
            assertThat(orderItem.getProductSnapshot().variantName()).isEqualTo("大杯");
        });

        // 5. 其他訪客不能查詢這張 ticket（一律 404，不洩漏是否存在）
        String otherGuestToken = guestLogin("訪客小華");
        mockMvc.perform(get("/api/v1/online-orders/" + ticketId)
                        .header("Authorization", "Bearer " + otherGuestToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("消費者冪等：同一 ticket 重複處理只建立一筆訂單")
    void testConsumerIsIdempotent() {
        String ticketId = "TEST" + System.nanoTime();
        PlaceOnlineOrderCommand command = new PlaceOnlineOrderCommand(
                ticketId, brandId, storeId, null, "GUEST:" + UUID.randomUUID(),
                List.of(new OrderItemDto(productId, variantId, 1, NOTES, List.of())),
                null);

        PlaceOnlineOrderHandler.Result first = placeOnlineOrderHandler.handle(command);
        PlaceOnlineOrderHandler.Result second = placeOnlineOrderHandler.handle(command);

        assertThat(first.created()).isTrue();
        assertThat(first.status()).isEqualTo("AWAITING_ACCEPTANCE");
        assertThat(second.created()).isFalse();
        assertThat(second.orderId()).isEqualTo(first.orderId());
        assertThat(second.orderNumber()).isEqualTo(first.orderNumber());
    }

    /** 以訪客身分登入，回傳 accessToken */
    private String guestLogin(String displayName) throws Exception {
        Map<String, Object> request = Map.of("displayName", displayName, "brandId", String.valueOf(brandId));
        String response = mockMvc.perform(post("/api/v1/auth/guest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.accessToken");
    }
}
