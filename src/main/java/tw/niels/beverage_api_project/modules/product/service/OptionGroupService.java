package tw.niels.beverage_api_project.modules.product.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.product.application.ProductCacheNames;
import tw.niels.beverage_api_project.modules.product.dto.CreateOptionGroupRequestDto;
import tw.niels.beverage_api_project.modules.product.dto.CreateProductOptionRequestDto;
import tw.niels.beverage_api_project.modules.product.dto.OptionGroupResponseDto;
import tw.niels.beverage_api_project.modules.product.dto.ProductOptionResponseDto;
import tw.niels.beverage_api_project.modules.product.entity.OptionGroupEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductOptionEntity;
import tw.niels.beverage_api_project.modules.product.repository.OptionGroupJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductOptionJpaRepository;

/**
 * 選項群組服務
 * <p>
 * 修改說明：
 * - 欄位／區域變數原與型別同名（批次改名殘留），改為小寫駝峰命名。
 * - 對外方法改回傳 DTO，並在交易內完成轉換（避免 Controller 在交易外觸發延遲載入）。
 * - 新增選項會改變 POS 商品資料（選項群組內含選項），補上清除 product-pos 快取，
 *   否則新選項最多 10 分鐘（快取 TTL）後才會出現在 POS／線上點餐。
 * - 建立群組時改用 getReference 取得品牌參照，並先檢查同品牌群組名稱是否重複
 *   （DB 有 UNIQUE(brand_id, name)，先檢查才能回傳 400 而非 DB 例外）。
 * </p>
 */
@Service
public class OptionGroupService {

    private final OptionGroupJpaRepository optionGroupJpaRepository;
    private final ProductOptionJpaRepository productOptionJpaRepository;
    private final BrandJpaRepository brandRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public OptionGroupService(OptionGroupJpaRepository optionGroupJpaRepository,
            ProductOptionJpaRepository productOptionJpaRepository,
            BrandJpaRepository brandRepository) {
        this.optionGroupJpaRepository = optionGroupJpaRepository;
        this.productOptionJpaRepository = productOptionJpaRepository;
        this.brandRepository = brandRepository;
    }

    @Transactional
    public OptionGroupResponseDto createOptionGroup(Long brandId, CreateOptionGroupRequestDto requestDto) {
        // 驗證品牌存在（existsById 未被覆寫，只做存在性檢查，不載入整個品牌）
        if (!brandRepository.existsById(brandId)) {
            throw new ResourceNotFoundException("找不到品牌，ID：" + brandId);
        }
        if (optionGroupJpaRepository.findByBrand_IdAndName(brandId, requestDto.getName()).isPresent()) {
            throw new BadRequestException("選項群組名稱已存在：" + requestDto.getName());
        }

        OptionGroupEntity optionGroup = new OptionGroupEntity();
        optionGroup.setBrand(entityManager.getReference(BrandEntity.class, brandId));
        optionGroup.setName(requestDto.getName());
        optionGroup.setSelectionType(requestDto.getSelectionType());
        optionGroup.setSortOrder(requestDto.getSortOrder());

        return OptionGroupResponseDto.fromEntity(optionGroupJpaRepository.save(optionGroup));
    }

    @Transactional
    @CacheEvict(value = ProductCacheNames.POS, key = "#brandId")
    public ProductOptionResponseDto createProductOption(Long groupId, Long brandId, CreateProductOptionRequestDto requestDto) {
        OptionGroupEntity optionGroup = optionGroupJpaRepository.findByBrand_IdAndId(brandId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到選項群組，ID：" + groupId));

        ProductOptionEntity option = new ProductOptionEntity();
        option.setOptionGroup(optionGroup);
        option.setOptionName(requestDto.getOptionName());
        option.setPriceAdjustment(requestDto.getPriceAdjustment());
        option.setDefault(requestDto.isDefault());

        return ProductOptionResponseDto.fromEntity(productOptionJpaRepository.save(option));
    }

    @Transactional(readOnly = true)
    public List<OptionGroupResponseDto> getOptionGroupsByBrand(Long brandId) {
        return optionGroupJpaRepository.findByBrand_Id(brandId).stream()
                .map(OptionGroupResponseDto::fromEntity)
                .sorted(Comparator.comparing(OptionGroupResponseDto::getSortOrder,
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(OptionGroupResponseDto::getGroupId))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Transactional(readOnly = true)
    public OptionGroupResponseDto getOptionGroupById(Long groupId, Long brandId) {
        return optionGroupJpaRepository.findByBrand_IdAndId(brandId, groupId)
                .map(OptionGroupResponseDto::fromEntity)
                .orElseThrow(() -> new ResourceNotFoundException("找不到選項群組，ID：" + groupId));
    }
}
