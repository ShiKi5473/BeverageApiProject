package tw.niels.beverage_api_project.modules.brand.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.modules.brand.dto.CreateBrandRequestDto;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.service.BrandService;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.BRANDS)
public class BrandController {
    private final BrandService brandService;

    public BrandController(BrandService brandService) {
        this.brandService = brandService;
    }

    @PostMapping("/register")
    public ResponseEntity<Brand> registerBrand(@Valid @RequestBody CreateBrandRequestDto requestDto) {
        Brand newBrand = brandService.createBrandAndAdmin(requestDto);
        return new ResponseEntity<>(newBrand, HttpStatus.CREATED);
    }
}
