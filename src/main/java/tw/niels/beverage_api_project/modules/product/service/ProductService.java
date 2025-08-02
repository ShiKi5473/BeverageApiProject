package tw.niels.beverage_api_project.modules.product.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.product.dto.CreateProductRequestDto;
import tw.niels.beverage_api_project.modules.product.dto.ProductPosDto;
import tw.niels.beverage_api_project.modules.product.dto.ProductSummaryDto;
import tw.niels.beverage_api_project.modules.product.entity.Category;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.repository.CategoryRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;

@Service
public class ProductService {
    private final ProductRepository productRepository;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    public ProductService(ProductRepository productRepository, BrandRepository brandRepository,
            CategoryRepository categoryRepository) {
        this.productRepository = productRepository;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
    }

    public Product createProduct(Long brandId, CreateProductRequestDto request) {
        Brand brand = brandRepository.findById(brandId)
                .orElseThrow(() -> new RuntimeException("找不到品牌，ID：" + brandId));

        Set<Category> categories = categoryRepository.findAllById(request.getCategoryIds())
                .stream()
                .collect(Collectors.toSet());

        if (categories.size() != request.getCategoryIds().size()) {
            throw new RuntimeException("部分分類 ID 無效");
        }

        Product newProduct = new Product();
        newProduct.setBrand(brand);
        newProduct.setName(request.getName());
        newProduct.setDescription(request.getDescription());
        newProduct.setBasePrice(request.getBasePrice());
        newProduct.setImageUrl(request.getImageUrl());
        newProduct.setIsAvailable(request.isAvailable());
        newProduct.setCategories(categories);

        return productRepository.save(newProduct);
    }

    @Transactional(readOnly = true)
    public List<ProductSummaryDto> getAvailableSummaries(Long brandId){
        return productRepository.findSummaryDtoByBrand_BrandIdAndIsAvailable(brandId, true);
    }

     @Transactional(readOnly = true)
    public List<ProductPosDto> getAvailableProductsForPos(Long brandId){
        return productRepository.findPosDtoByBrand_BrandIdAndIsAvailable(brandId, true);
    }



}
