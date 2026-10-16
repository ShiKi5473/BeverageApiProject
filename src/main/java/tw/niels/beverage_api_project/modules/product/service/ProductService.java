package tw.niels.beverage_api_project.modules.product.service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.modules.product.dto.ProductPosDto;
import tw.niels.beverage_api_project.modules.product.dto.ProductSummaryDto;
import tw.niels.beverage_api_project.modules.product.entity.ProductEntity;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.ProductJpaRepository;

/**
 * 商品讀取服務（CQRS 讀取端的資料組裝）
 * <p>
 * 修改說明：
 * - 舊的寫入方法（createProduct、addProductVariant、updateProductVariant、deleteProductVariant、
 *   linkOptionGroupsToProduct）已無任何呼叫端，寫入一律走 {@code ProductCommandHandler}，
 *   保留兩套寫入路徑只會造成邏輯與快取清除不一致，故移除。
 * - 移除本類別上的 @Cacheable：快取統一由 {@code ProductQueryHandler} 負責，避免同一份資料被快取兩層。
 * - 回傳可變的 ArrayList（Collectors.toCollection），確保存入 Redis 後可被 Jackson 正確反序列化。
 * </p>
 */
@Service
public class ProductService {

    private final ProductJpaRepository productRepository;

    public ProductService(ProductJpaRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 取得商品摘要列表（上架中）
     */
    @Transactional(readOnly = true)
    public List<ProductSummaryDto> getAvailableSummaries(Long brandId) {
        List<ProductEntity> products = productRepository.findByBrand_IdAndStatus(brandId, ProductStatus.ACTIVE);
        return products.stream()
                .map(ProductSummaryDto::fromEntity)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * 取得 POS 完整商品列表（上架中，含規格、選項群組、分類；查詢已用 JOIN FETCH 避免 N+1）
     */
    @Transactional(readOnly = true)
    public List<ProductPosDto> getAvailableProductsForPos(Long brandId) {
        List<ProductEntity> products = productRepository.findByBrand_IdAndStatus(brandId, ProductStatus.ACTIVE);
        return products.stream()
                .map(ProductPosDto::fromEntity)
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
