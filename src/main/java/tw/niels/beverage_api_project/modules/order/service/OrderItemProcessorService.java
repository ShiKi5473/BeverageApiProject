package tw.niels.beverage_api_project.modules.order.service;


import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.entity.Order;
import tw.niels.beverage_api_project.modules.order.entity.OrderItem;
import tw.niels.beverage_api_project.modules.order.vo.ProductSnapshot;
import tw.niels.beverage_api_project.modules.product.entity.Category;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.entity.ProductOption;
import tw.niels.beverage_api_project.modules.product.repository.ProductOptionRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
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

    // 定義回傳 Record
    public record ProcessResult(
            Set<OrderItem> orderItems,
            BigDecimal totalAmount) {}


    /**
     * 處理訂單品項的核心邏輯：建立 OrderItem 實體並計算總金額
     * (從 OrderService 搬移至此)
     */
    public ProcessResult processOrderItems(Order order, List<OrderItemDto> itemDtos, Long brandId) {
        // 1. 邊界檢查
        if (itemDtos == null || itemDtos.isEmpty()) {
            return new ProcessResult(new HashSet<>(), BigDecimal.ZERO);
        }

        // 2. 收集所有 Product ID 與 Option ID
        Set<Long> productIds = new HashSet<>();
        Set<Long> allOptionIds = new HashSet<>();

        for (OrderItemDto dto : itemDtos) {
            if (dto.getProductId() != null) {
                productIds.add(dto.getProductId());
            }
            if (dto.getOptionIds() != null) {
                allOptionIds.addAll(dto.getOptionIds());
            }
        }

        // 3. 一次性批次查詢 Product (轉為 Map)
        // 確保 Repository 的 findByBrand_IdAndIdIn 有使用 JOIN FETCH categories
        Map<Long, Product> productMap = productRepository.findByBrand_IdAndIdIn(brandId, productIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        // 4. 一次性批次查詢 ProductOption (轉為 Map)
        Map<Long, ProductOption> optionMap = new HashMap<>();
        if (!allOptionIds.isEmpty()) {
            optionMap = productOptionRepository.findByOptionGroup_Brand_IdAndIdIn(brandId, allOptionIds)
                    .stream()
                    .collect(Collectors.toMap(ProductOption::getId, Function.identity()));
        }

        Set<OrderItem> orderItems = new HashSet<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 5. 記憶體內迴圈處理 (純 Java 運算)
        for (OrderItemDto itemDto : itemDtos) {
            // 取得商品
            Product product = productMap.get(itemDto.getProductId());
            if (product == null) {
                // 【修正】變數名稱從 dto 改為 itemDto
                throw new ResourceNotFoundException("商品不存在或已下架，ID: " + itemDto.getProductId());
            }

            // 建立 OrderItem
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(product);
            orderItem.setQuantity(itemDto.getQuantity());

            // XSS 防護
            String notes = itemDto.getNotes();
            if (notes != null) {
                notes = Jsoup.clean(notes, Safelist.none());
            }
            orderItem.setNotes(notes);

            // 設定快照 (CategoryName)
            // 注意：確保 ProductRepository 有 fetch categories，否則這裡會觸發 N+1
            String categoryName = product.getCategories().stream()
                    .findFirst()
                    .map(Category::getName)
                    .orElse("Uncategorized");

            ProductSnapshot snapshot = new ProductSnapshot(
                    product.getId(),
                    product.getName(),
                    product.getBasePrice(),
                    categoryName
            );
            orderItem.setProductSnapshot(snapshot);

            // 處理選項 (從 Map 取得，不查 DB)
            BigDecimal optionsPrice = BigDecimal.ZERO;
            if (itemDto.getOptionIds() != null && !itemDto.getOptionIds().isEmpty()) {
                Set<ProductOption> selectedOptions = new HashSet<>();

                for (Long optionId : itemDto.getOptionIds()) {
                    ProductOption option = optionMap.get(optionId);
                    if (option == null) {
                        throw new BadRequestException("選項 ID 無效或不屬於此品牌: " + optionId);
                    }
                    selectedOptions.add(option);
                    optionsPrice = optionsPrice.add(option.getPriceAdjustment());
                }
                orderItem.setOptions(selectedOptions);
            }

            // 計算金額
            BigDecimal unitPrice = product.getBasePrice().add(optionsPrice);
            orderItem.setUnitPrice(unitPrice);

            BigDecimal subtotal = unitPrice.multiply(new BigDecimal(itemDto.getQuantity()));
            orderItem.setSubtotal(subtotal);

            orderItems.add(orderItem);
            totalAmount = totalAmount.add(subtotal);
        }

        return new ProcessResult(orderItems, totalAmount);
    }


}
