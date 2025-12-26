package tw.niels.beverage_api_project.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tw.niels.beverage_api_project.AbstractIntegrationTest;
import tw.niels.beverage_api_project.modules.order.dto.CreateOrderRequestDto;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.enums.OrderStatus;
import tw.niels.beverage_api_project.modules.order.repository.OrderRepository;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariant;
import tw.niels.beverage_api_project.modules.product.repository.ProductVariantRepository;
import tw.niels.beverage_api_project.security.BrandContextHolder;
import tw.niels.beverage_api_project.security.jwt.JwtTokenProvider;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
public class OnlineOrderIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductVariantRepository productVariantRepository; // 新增注入

    @Autowired
    private UserDetailsService customUserDetailsService;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    @DisplayName("測試非同步下單流程：API -> RabbitMQ -> Consumer -> Database")
    void testAsyncOrderFlow() throws Exception {
        // 1. 準備測試資料
        Long brandId = 1L;
        Long storeId = 1L;
        String userPhone = "0911111111"; // DataSeeder 建立的店長帳號

        // 1-1. 生成真實的 JWT Token
        BrandContextHolder.setBrandId(brandId);
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(userPhone);

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        String token = jwtTokenProvider.generateToken(authentication);


        // 1-2. 取得真實的 Product 與 Variant ID
        // 使用 findByProduct_Brand_IdAndIsDeletedFalse 代替 findAll()
        ProductVariant variant = productVariantRepository.findByProduct_Brand_IdAndIsDeletedFalse(brandId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("整合測試失敗：資料庫中找不到任何商品規格 (BrandId=" + brandId + ")，請檢查 DataSeeder"));

        Long realProductId = variant.getProduct().getId();
        Long realVariantId = variant.getId();

        BrandContextHolder.clear();

        // 1-3. 建立請求 DTO (使用 Record 建構子)
        OrderItemDto itemDto = new OrderItemDto(
                realProductId,            // productId
                realVariantId,            // variantId (使用真實 ID)
                2,                        // quantity
                "非同步測試",               // notes
                Collections.emptyList()   // optionIds
        );

        CreateOrderRequestDto requestDto = new CreateOrderRequestDto();
        requestDto.setStatus(OrderStatus.PENDING);
        requestDto.setItems(List.of(itemDto));

        // 2. 發送 POST 請求
        mockMvc.perform(post("/api/v1/online-orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestDto)))
                .andExpect(status().isAccepted()) // 202 Accepted
                .andExpect(jsonPath("$.ticketId").exists())
                .andExpect(jsonPath("$.status").value("QUEUED"));

        // 3. 驗證資料庫寫入 (非同步等待 RabbitMQ 消費完成)
        await().atMost(10, TimeUnit.SECONDS).pollInterval(Duration.ofMillis(500)).untilAsserted(() -> {
            transactionTemplate.execute(status -> {
                BrandContextHolder.setBrandId(brandId);
                try {
                    List<Order> orders = orderRepository.findAllByBrand_IdAndStore_Id(brandId, storeId);

                    // 檢查是否有一筆訂單包含我們剛才送出的備註
                    boolean orderExists = orders.stream()
                            .anyMatch(o -> o.getItems().stream()
                                    .anyMatch(i -> "非同步測試".equals(i.getNotes())));

                    assertThat(orderExists).isTrue();

                    // 進階驗證：檢查是否正確寫入了 variant 關聯 (選用)
                    boolean variantCorrect = orders.stream()
                            .flatMap(o -> o.getItems().stream())
                            .filter(i -> "非同步測試".equals(i.getNotes()))
                            .anyMatch(i -> i.getProductVariant().getId().equals(realVariantId));
                    assertThat(variantCorrect).isTrue();


                } finally {
                    BrandContextHolder.clear();
                }
                return null;
            });
        });
    }
}