package tw.niels.beverage_api_project.modules.product.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.product.dto.CreateOptionGroupRequestDto;
import tw.niels.beverage_api_project.modules.product.dto.CreateProductOptionRequestDto;
import tw.niels.beverage_api_project.modules.product.dto.OptionGroupResponseDto;
import tw.niels.beverage_api_project.modules.product.dto.ProductOptionResponseDto;
import tw.niels.beverage_api_project.modules.product.entity.OptionGroup;
import tw.niels.beverage_api_project.modules.product.entity.ProductOption;
import tw.niels.beverage_api_project.modules.product.service.OptionGroupService;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.BRANDS)
@PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')") // 僅管理員可設定
public class OptionGroupController {

    private final OptionGroupService optionGroupService;
    private final ControllerHelperService helperService;

    public OptionGroupController(OptionGroupService optionGroupService,
            ControllerHelperService helperService) {
        this.optionGroupService = optionGroupService;
        this.helperService = helperService;
    }

    // 建立選項群組 (例如: 甜度)
    @PostMapping("/option-groups")
    public ResponseEntity<OptionGroupResponseDto> createOptionGroup(
            ControllerHelperService helperService,
            @Valid @RequestBody CreateOptionGroupRequestDto requestDto) {
        Long brandId = helperService.getCurrentBrandId();
        OptionGroup newGroup = optionGroupService.createOptionGroup(brandId, requestDto);
        return new ResponseEntity<>(OptionGroupResponseDto.fromEntity(newGroup), HttpStatus.CREATED);
    }

    // 取得品牌的所有選項群組
    @GetMapping("/option-groups")
    public ResponseEntity<List<OptionGroupResponseDto>> getOptionGroupsByBrand(
            ControllerHelperService helperService) {
        Long brandId = helperService.getCurrentBrandId();
        List<OptionGroup> groups = optionGroupService.getOptionGroupsByBrand(brandId);
        List<OptionGroupResponseDto> dtos = groups.stream()
                .map(OptionGroupResponseDto::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    // 在群組下建立選項 (例如: "半糖")
    @PostMapping("/option-groups/{groupId}/options")
    public ResponseEntity<ProductOptionResponseDto> createProductOption(
            ControllerHelperService helperService,
            @PathVariable Long groupId,
            @Valid @RequestBody CreateProductOptionRequestDto requestDto) {
        Long brandId = helperService.getCurrentBrandId();
        ProductOption newOption = optionGroupService.createProductOption(groupId, brandId, requestDto);
        return new ResponseEntity<>(ProductOptionResponseDto.fromEntity(newOption), HttpStatus.CREATED);
    }
}
