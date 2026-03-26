package tw.niels.beverage_api_project.modules.product.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.product.dto.CreateOptionGroupRequestDto;
import tw.niels.beverage_api_project.modules.product.dto.CreateProductOptionRequestDto;
import tw.niels.beverage_api_project.modules.product.entity.OptionGroupEntity;
import tw.niels.beverage_api_project.modules.product.entity.ProductOptionEntity;
import tw.niels.beverage_api_project.modules.product.repository.OptionGroupJpaRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductOptionJpaRepository;

@Service
public class OptionGroupService {

    private final OptionGroupJpaRepository OptionGroupJpaRepository;
    private final ProductOptionJpaRepository ProductOptionJpaRepository;
    private final BrandJpaRepository brandRepository;

    public OptionGroupService(OptionGroupJpaRepository OptionGroupJpaRepository,
            ProductOptionJpaRepository ProductOptionJpaRepository,
            BrandJpaRepository brandRepository) {
        this.OptionGroupJpaRepository = OptionGroupJpaRepository;
        this.ProductOptionJpaRepository = ProductOptionJpaRepository;
        this.brandRepository = brandRepository;
    }

    @Transactional
    public OptionGroupEntity createOptionGroup(Long brandId, CreateOptionGroupRequestDto requestDto) {
        BrandEntity BrandEntity = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + brandId));

        OptionGroupEntity OptionGroupEntity = new OptionGroupEntity();
        OptionGroupEntity.setBrand(BrandEntity);
        OptionGroupEntity.setName(requestDto.getName());
        OptionGroupEntity.setSelectionType(requestDto.getSelectionType());
        OptionGroupEntity.setSortOrder(requestDto.getSortOrder());

        return OptionGroupJpaRepository.save(OptionGroupEntity);
    }

    @Transactional
    public ProductOptionEntity createProductOption(Long groupId, Long brandId, CreateProductOptionRequestDto requestDto) {
        OptionGroupEntity OptionGroupEntity = OptionGroupJpaRepository.findByBrand_IdAndId(brandId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到選項群組，ID：" + groupId));

        ProductOptionEntity ProductOptionEntity = new ProductOptionEntity();
        ProductOptionEntity.setOptionGroup(OptionGroupEntity);
        ProductOptionEntity.setOptionName(requestDto.getOptionName());
        ProductOptionEntity.setPriceAdjustment(requestDto.getPriceAdjustment());
        ProductOptionEntity.setDefault(requestDto.isDefault());

        return ProductOptionJpaRepository.save(ProductOptionEntity);
    }

    @Transactional(readOnly = true)
    public List<OptionGroupEntity> getOptionGroupsByBrand(Long brandId) {
        return OptionGroupJpaRepository.findByBrand_Id(brandId);
    }

    @Transactional(readOnly = true)
    public OptionGroupEntity getOptionGroupById(Long groupId, Long brandId) {
        return OptionGroupJpaRepository.findByBrand_IdAndId(brandId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到選項群組，ID：" + groupId));
    }
}