package tw.niels.beverage_api_project.modules.order.service;


import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.OrderItem;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.entity.ProductOption;
import tw.niels.beverage_api_project.modules.product.repository.ProductOptionRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 新服務：
 * 專門負責處理 OrderItem DTOs 轉換為 Entities 並計算總金額。
 * 可被 OrderService (建立) 和 PendingState (更新) 共用。
 */
@Service
public class OrderItemProcessorService {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;

    public OrderItemProcessorService(ProductRepository productRepository, ProductOptionRepository productOptionRepository) {
        this.productRepository = productRepository;
        this.productOptionRepository = productOptionRepository;
    }

    // 用於封裝品項處理結果的內部類別
    public static class ProcessedItemsResult {
        public final Set<OrderItem> orderItems;
        public final BigDecimal totalAmount;

        public ProcessedItemsResult(Set<OrderItem> orderItems, BigDecimal totalAmount) {
            this.orderItems = orderItems;
            this.totalAmount = totalAmount;
        }
    }

    /**
     * 處理訂單品項的核心邏輯：建立 OrderItem 實體並計算總金額
     * (從 OrderService 搬移至此)
     */
    public ProcessedItemsResult processOrderItems(Order order, List<OrderItemDto> itemDtos, Long brandId) {
        Set<OrderItem> orderItems = new HashSet<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemDto itemDto : itemDtos) {
            Product product = productRepository.findByBrand_BrandIdAndProductId(brandId, itemDto.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("找不到商品，ID：" + itemDto.getProductId()));

            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(itemDto.getQuantity());
// 【新增】簡易的 XSS 防護：移除所有 HTML 標籤
            String notes = itemDto.getNotes();
            if (notes != null) {
                notes = notes.replaceAll("<[^>]*>", ""); // 移除所有 <...> 標籤
            }
            orderItem.setNotes(notes); // 儲存清理過的 notes
            BigDecimal optionsPrice = BigDecimal.ZERO;
            if (itemDto.getOptionIds() != null && !itemDto.getOptionIds().isEmpty()) {
                Set<ProductOption> options = productOptionRepository.findByOptionIdIn(itemDto.getOptionIds());
                if (options.size() != itemDto.getOptionIds().size()) {
                    throw new BadRequestException("部分選項 ID 無效");
                }
                orderItem.setOptions(options);
                optionsPrice = options.stream()
                        .map(ProductOption::getPriceAdjustment)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

            }

            BigDecimal unitPrice = product.getBasePrice().add(optionsPrice);
            orderItem.setUnitPrice(unitPrice);

            BigDecimal subtotal = unitPrice.multiply(new BigDecimal(itemDto.getQuantity()));
            orderItem.setSubtotal(subtotal);

            orderItems.add(orderItem);
            totalAmount = totalAmount.add(subtotal);
        }
        return new ProcessedItemsResult(orderItems, totalAmount);
    }
}
