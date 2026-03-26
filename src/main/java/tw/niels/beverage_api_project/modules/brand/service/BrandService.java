package tw.niels.beverage_api_project.modules.brand.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.constants.AuthorityConstants;
import tw.niels.beverage_api_project.common.exception.ApplicationException;
import tw.niels.beverage_api_project.common.exception.DomainException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.dto.CreateBrandRequestDto;
import tw.niels.beverage_api_project.modules.brand.dto.UpdatePointConfigDto;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.entity.BrandPointConfigEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandPointConfigJpaRepository;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.security.BrandContextHolder;

@Service
public class BrandService {

    private final BrandJpaRepository brandRepository;
    private final BrandPointConfigJpaRepository BrandPointConfigJpaRepository;

    public BrandService(BrandJpaRepository brandRepository,
                        BrandPointConfigJpaRepository BrandPointConfigJpaRepository) {
        this.brandRepository = brandRepository;
        this.BrandPointConfigJpaRepository = BrandPointConfigJpaRepository;
    }

    @Transactional // 建議加上交易註解
    public BrandEntity createBrand(CreateBrandRequestDto requestDto) {
        // 檢查品牌名稱是否已存在
        brandRepository.findByName(requestDto.getName()).ifPresent(b -> {
            throw new DomainException("品牌名稱 '" + requestDto.getName() + "' 已經存在。");
        });

        BrandEntity BrandEntity = new BrandEntity();
        BrandEntity.setName(requestDto.getName());
        BrandEntity.setContactPerson(requestDto.getContactPerson());
        BrandEntity.setIsActive(true); // 預設為啟用

        return brandRepository.save(BrandEntity);
    }

    /**
     * 【品牌端用戶專用】
     * 取得當前登入用戶所屬的品牌資訊
     * 安全性由 ContextHolder 保證
     */
    public BrandEntity getCurrentBrandProfile() {
        Long currentBrandId = BrandContextHolder.getBrandId();

        if (currentBrandId == null) {
            throw new ApplicationException("無法取得當前品牌 Context");
        }

        return brandRepository.findById(currentBrandId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到當前品牌資訊 (ID: " + currentBrandId + ")"));
    }

    @Transactional
    public BrandPointConfigEntity updatePointConfig(Long brandId, UpdatePointConfigDto dto) {
        // 1. 確保品牌存在
        BrandEntity BrandEntity = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + brandId));

        // 2. 直接從 config repository 查找 (因為 ID 是共享的，所以直接用 brandId)
        BrandPointConfigEntity config = BrandPointConfigJpaRepository.findById(brandId)
                .orElse(null);

        // 3. 如果不存在則建立
        if (config == null) {
            config = new BrandPointConfigEntity(BrandEntity);
        }

        // 4. 更新數值
        config.setEarnRate(dto.getEarnRate());
        config.setRedeemRate(dto.getRedeemRate());
        config.setExpiryDays(dto.getExpiryDays());

        // 5. 直接儲存 config
        return BrandPointConfigJpaRepository.save(config);
    }

    /**
     * 【危險方法】僅供平台管理員使用
     * 建議重新命名以明確意圖，例如 getAllBrandsForAdmin
     */
    // 權限驗證由 Controller 層的 @PreAuthorize 負責（符合 Phase 0 架構決策）
    public List<BrandEntity> getAllBrandsForAdmin() {
        return brandRepository.findAll();
    }

    /**
     * 【平台管理員專用】
     * 允許查詢系統中的任何品牌
     * 建議加上 @PreAuthorize("hasRole('PLATFORM_ADMIN')")
     */
    public Optional<BrandEntity> getBrandById(Long id) {
        return brandRepository.findById(id); // 這裡必須能呼叫 findById
    }


    public List<BrandEntity> getActiveBrands() {
        return brandRepository.findByIsActiveTrue();
    }
}
