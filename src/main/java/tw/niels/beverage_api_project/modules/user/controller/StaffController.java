package tw.niels.beverage_api_project.modules.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.modules.user.dto.CreateStaffRequestDto;
import tw.niels.beverage_api_project.modules.user.dto.StaffDto;
import tw.niels.beverage_api_project.modules.user.entity.Staff;
import tw.niels.beverage_api_project.modules.user.service.StaffService;


@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.BRANDS)
public class StaffController {
    private final StaffService staffService;
    public StaffController(StaffService staffService){
        this.staffService = staffService;
    }

    @PostMapping("/{brandId}/staff")
    @PreAuthorize("hasRole('BRAND_ADMIN')")
    public ResponseEntity<StaffDto> createStaff(@PathVariable Long brandId,@Valid @RequestBody CreateStaffRequestDto requestDto) {
        Staff createsStaff = staffService.createStaff(brandId, requestDto);
        StaffDto responseDto = StaffDto.fromEntity(createsStaff);
        return new ResponseEntity<>(responseDto, HttpStatus.CREATED);
    }
    
    
}
