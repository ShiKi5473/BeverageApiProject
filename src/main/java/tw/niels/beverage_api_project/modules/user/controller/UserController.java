package tw.niels.beverage_api_project.modules.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import tw.niels.beverage_api_project.modules.order.dto.MemberDto;
import tw.niels.beverage_api_project.modules.user.dto.CreateUserRequestDto;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.service.UserService;

import java.util.Map;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.USERS)
@Tag(name = "User Management", description = "使用者與會員管理 API")
public class UserController {

    private final UserService userService;
    private final ControllerHelperService helperService;

    public UserController(UserService userService,
            ControllerHelperService helperService) {
        this.userService = userService;
        this.helperService = helperService;
    }

    // 建立新使用者 (員工或會員)
    // 只有品牌管理員或店長有權限建立新員工
    @PostMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    @Operation(summary = "建立新使用者", description = "建立員工或會員帳號 (需管理員權限)")
    public ResponseEntity<Object> createUser(
            @Valid @RequestBody CreateUserRequestDto createUserRequestDto) {
        try {
            System.out.println("try to create new user");
            User newUser = userService.createUser(createUserRequestDto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("message", "User created successfully", "userId", newUser.getUserId()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * 根據手機號碼查詢會員
     * 只有員工、店長、品牌管理員可以查詢
     *
     * @param phone 會員的手機號碼
     * @return 找到會員則返回 200 和會員資料，否則返回 404
     */
    @GetMapping("/member/by-phone/{phone}")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'BRAND_ADMIN')")
    @Operation(summary = "查詢會員資料", description = "根據手機號碼搜尋會員 (用於 POS 結帳時綁定會員)")
    public ResponseEntity<MemberDto> getMemberByPhone(@PathVariable String phone) {
        Long brandId = this.helperService.getCurrentBrandId();
        return userService.findMemberByPhone(phone, brandId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}
