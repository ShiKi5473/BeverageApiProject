package tw.niels.beverage_api_project.modules.order.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.order.dto.OrderItemDto;
import tw.niels.beverage_api_project.modules.order.entity.OrderEntity;
import tw.niels.beverage_api_project.modules.order.entity.OrderItemEntity;
import tw.niels.beverage_api_project.modules.order.vo.ProductSnapshot;
import tw.niels.beverage_api_project.modules.product.entity.CategoryEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductOptionEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductVariantEntity;
import tw.niels.beverage_api_project.modules.product.repository.ProductOptionJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductVariantJpaRepository; // 新增

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderItemProcessorService {

    private final ProductJpaRepository productRepository;
    private final ProductOptionJpaRepository ProductOptionJpaRepository;
    private final ProductVariantJpaRepository ProductVariantJpaRepository; // 新增注入

    public OrderItemProcessorService(ProductJpaRepository productRepository,
                                     ProductOptionJpaRepository ProductOptionJpaRepository,
                                     ProductVariantJpaRepository ProductVariantJpaRepository) {
        this.productRepository = productRepository;
        this.ProductOptionJpaRepository = ProductOptionJpaRepository;
        this.ProductVariantJpaRepository = ProductVariantJpaRepository;
    }

    public record ProcessResult(
            Set<OrderItemEntity> orderItems,
            BigDecimal totalAmount) {}

    public ProcessResult processOrderItems(OrderEntity OrderEntity, List<OrderItemDto> itemDtos, Long brandId) {
        if (itemDtos == null || itemDtos.isEmpty()) {
            return new ProcessResult(new HashSet<>(), BigDecimal.ZERO);
        }

        // 1. 收集 ID (ProductEntity, Variant, Option)
        Set<Long> productIds = new HashSet<>();
        Set<Long> variantIds = new HashSet<>(); // 新增：收集規格 ID
        Set<Long> allOptionIds = new HashSet<>();

        for (OrderItemDto dto : itemDtos) {
            if (dto.productId() != null) productIds.add(dto.productId());
            if (dto.variantId() != null) variantIds.add(dto.variantId());
            if (dto.optionIds() != null) allOptionIds.addAll(dto.optionIds());
        }

        // 2. 批次查詢 ProductEntity
        Map<Long, ProductEntity> productMap = productRepository.findByBrand_IdAndIdIn(brandId, productIds)
                .stream()
                .collect(Collectors.toMap(ProductEntity::getId, Function.identity()));

        // 3. 批次查詢 ProductVariantEntity (新增步驟)
        // 需在 Repository 實作 findByProduct_Brand_IdAndIdInAndIsDeletedFalse
        Map<Long, ProductVariantEntity> variantMap = new HashMap<>();
        if (!variantIds.isEmpty()) {
            // 假設您在 Repository 加了這個方法，或是用 findByIdIn 配合 filter
            variantMap = ProductVariantJpaRepository.findByProduct_Brand_IdAndIdInAndIsDeletedFalse(brandId, variantIds)
                    .stream()
                    .collect(Collectors.toMap(ProductVariantEntity::getId, Function.identity()));
        }

        // 4. 批次查詢 Option
        Map<Long, ProductOptionEntity> optionMap = new HashMap<>();
        if (!allOptionIds.isEmpty()) {
            optionMap = ProductOptionJpaRepository.findByOptionGroup_Brand_IdAndIdIn(brandId, allOptionIds)
                    .stream()
                    .collect(Collectors.toMap(ProductOptionEntity::getId, Function.identity()));
        }

        Set<OrderItemEntity> orderItems = new HashSet<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        // 5. 處理每個 Item
        for (OrderItemDto itemDto : itemDtos) {
            ProductEntity ProductEntity = productMap.get(itemDto.productId());
            if (ProductEntity == null) {
                throw new ResourceNotFoundException("商品不存在或已下架，ID: " + itemDto.productId());
            }

            // 驗證並取得規格
            if (itemDto.variantId() == null) {
                throw new BadRequestException("必須選擇商品規格 (Variant ID)");
            }
            ProductVariantEntity variant = variantMap.get(itemDto.variantId());
            if (variant == null) {
                throw new BadRequestException("規格無效或已刪除，ID: " + itemDto.variantId());
            }
            // 安全性檢查：確保規格真的屬於該商品
            if (variant.getProduct().getId() != null && !variant.getProduct().getId().equals(ProductEntity.getId())) {
                throw new BadRequestException("規格 ID 與商品 ID 不匹配");
            }

            // 建立 OrderItemEntity
            OrderItemEntity OrderItemEntity = new OrderItemEntity();
            OrderItemEntity.setOrder(OrderEntity);
            OrderItemEntity.setProduct(ProductEntity);
            OrderItemEntity.setProductVariant(variant); // 【關鍵修正】設定規格關聯
            OrderItemEntity.setQuantity(itemDto.quantity());

            // XSS
            String notes = itemDto.notes();
            if (notes != null) notes = Jsoup.clean(notes, Safelist.none());
            OrderItemEntity.setNotes(notes);

            // 快照 (需包含規格名稱，例如 "紅茶 - 大杯")
            String categoryName = ProductEntity.getCategories().stream()
                    .findFirst()
                    .map(CategoryEntity::getName)
                    .orElse("Uncategorized");

            ProductSnapshot snapshot = new ProductSnapshot(
                    ProductEntity.getId(),
                    ProductEntity.getName(), // 這裡其實可以考慮加上 variant.getName()，但通常前端分開顯示
                    variant.getPrice(), // 使用規格價格，而非商品底價
                    categoryName,
                    variant.getName()   // 在 ProductSnapshot 中新增 variantName 欄位
            );
            // 若 ProductSnapshot 尚未支援 variantName，暫時只能存在 Name 裡，或先不改
            // 建議修改 ProductSnapshot record 結構
            OrderItemEntity.setProductSnapshot(snapshot);

            // 選項處理
            BigDecimal optionsPrice = BigDecimal.ZERO;
            if (itemDto.optionIds() != null) {
                Set<ProductOptionEntity> selectedOptions = new HashSet<>();
                for (Long optionId : itemDto.optionIds()) {
                    ProductOptionEntity option = optionMap.get(optionId);
                    if (option == null) throw new BadRequestException("無效選項: " + optionId);
                    selectedOptions.add(option);
                    optionsPrice = optionsPrice.add(option.getPriceAdjustment());
                }
                OrderItemEntity.setOptions(selectedOptions);
            }

            // 計算金額 (使用 variant price)
            BigDecimal unitPrice = variant.getPrice().add(optionsPrice); // 【關鍵修正】
            OrderItemEntity.setUnitPrice(unitPrice);

            BigDecimal subtotal = unitPrice.multiply(new BigDecimal(itemDto.quantity()));
            OrderItemEntity.setSubtotal(subtotal);

            orderItems.add(OrderItemEntity);
            totalAmount = totalAmount.add(subtotal);
        }

        return new ProcessResult(orderItems, totalAmount);
    }
}