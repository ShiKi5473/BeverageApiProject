package tw.niels.beverage_api_project.modules.product.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.product.dto.CreateOptionGroupRequestDto;
import tw.niels.beverage_api_project.modules.product.dto.CreateProductOptionRequestDto;
import tw.niels.beverage_api_project.modules.product.entity.OptionGroup;
import tw.niels.beverage_api_project.modules.product.entity.ProductOption;
import tw.niels.beverage_api_project.modules.product.repository.OptionGroupRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductOptionRepository;

@Service
public class OptionGroupService {

    private final OptionGroupRepository optionGroupRepository;
    private final ProductOptionRepository productOptionRepository;
    private final BrandRepository brandRepository;

    public OptionGroupService(OptionGroupRepository optionGroupRepository,
            ProductOptionRepository productOptionRepository,
            BrandRepository brandRepository) {
        this.optionGroupRepository = optionGroupRepository;
        this.productOptionRepository = productOptionRepository;
        this.brandRepository = brandRepository;
    }

    @Transactional
    public OptionGroup createOptionGroup(Long brandId, CreateOptionGroupRequestDto requestDto) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + brandId));

        OptionGroup optionGroup = new OptionGroup();
        optionGroup.setBrand(brand);
        optionGroup.setName(requestDto.getName());
        optionGroup.setSelectionType(requestDto.getSelectionType());
        optionGroup.setSortOrder(requestDto.getSortOrder());

        return optionGroupRepository.save(optionGroup);
    }

    @Transactional
    public ProductOption createProductOption(Long groupId, Long brandId, CreateProductOptionRequestDto requestDto) {
        OptionGroup optionGroup = optionGroupRepository.findByBrand_IdAndId(brandId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到選項群組，ID：" + groupId));

        ProductOption productOption = new ProductOption();
        productOption.setOptionGroup(optionGroup);
        productOption.setOptionName(requestDto.getOptionName());
        productOption.setPriceAdjustment(requestDto.getPriceAdjustment());
        productOption.setDefault(requestDto.isDefault());

        return productOptionRepository.save(productOption);
    }

    @Transactional(readOnly = true)
    public List<OptionGroup> getOptionGroupsByBrand(Long brandId) {
        return optionGroupRepository.findByBrand_Id(brandId);
    }

    @Transactional(readOnly = true)
    public OptionGroup getOptionGroupById(Long groupId, Long brandId) {
        return optionGroupRepository.findByBrand_IdAndId(brandId, groupId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到選項群組，ID：" + groupId));
    }
}