package tw.niels.beverage_api_project.modules.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import tw.niels.beverage_api_project.common.annotation.Audit;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.member.application.command.RegisterMemberCommand;
import tw.niels.beverage_api_project.modules.member.application.handler.MembershipCommandHandler;
import tw.niels.beverage_api_project.modules.member.application.handler.MembershipQueryHandler;
import tw.niels.beverage_api_project.modules.member.application.query.GetMemberByPhoneQuery;
import tw.niels.beverage_api_project.modules.order.dto.MemberDto;
import tw.niels.beverage_api_project.modules.user.application.command.RegisterStaffCommand;
import tw.niels.beverage_api_project.modules.user.application.handler.UserCommandHandler;
import tw.niels.beverage_api_project.modules.user.dto.CreateUserRequestDto;
import tw.niels.beverage_api_project.modules.user.dto.CreateUserResponseDto;

@RestController
@RequestMapping(ApiPaths.API_V1 + ApiPaths.USERS)
@Tag(name = "User Management", description = "使用者與會員管理 API")
public class UserController {

    private final UserCommandHandler userCommandHandler;
    private final MembershipCommandHandler membershipCommandHandler;
    private final MembershipQueryHandler membershipQueryHandler;
    private final ControllerHelperService helperService;

    public UserController(UserCommandHandler userCommandHandler,
                          MembershipCommandHandler membershipCommandHandler,
                          MembershipQueryHandler membershipQueryHandler,
                          ControllerHelperService helperService) {
        this.userCommandHandler = userCommandHandler;
        this.membershipCommandHandler = membershipCommandHandler;
        this.membershipQueryHandler = membershipQueryHandler;
        this.helperService = helperService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    @Operation(summary = "建立新使用者", description = "建立員工或會員帳號")
    @Audit(action = "CREATE_USER")
    public ResponseEntity<Object> createUser(@Valid @RequestBody CreateUserRequestDto request) {
        Long currentBrandId = helperService.getCurrentBrandId();
        if (!currentBrandId.equals(request.getBrandId())) {
            // 修正：原本回傳純字串 body，與全站統一的 ErrorResponse 格式不一致；
            // 改拋 AccessDeniedException，由 GlobalExceptionHandler 轉成 403 + ErrorResponse
            throw new AccessDeniedException("無權限為其他品牌建立使用者");
        }

        // 修正(S-1)：員工資料與會員資料都沒帶時，原本會建立「沒有任何 profile」的空帳號，
        // 登入後會被當成 ROLE_MEMBER，卻沒有會員資料可用。明確拒絕（400）。
        if (request.getMemberProfile() == null && request.getStaffProfile() == null) {
            throw new BadRequestException("必須提供員工資料(staffProfile)或會員資料(memberProfile)其中之一");
        }

        Long userId;
        if (request.getMemberProfile() != null) {
            userId = membershipCommandHandler.handle(new RegisterMemberCommand(currentBrandId, request));
        } else {
            userId = userCommandHandler.handle(new RegisterStaffCommand(currentBrandId, request));
        }

        // 改用 CreateUserResponseDto：userId 以字串序列化，避免 TSID 超過 JS 安全整數範圍而失真(P0-10)
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateUserResponseDto("User created successfully", userId));
    }

    @GetMapping("/member/by-phone/{phone}")
    // 修改：TESTER（測試人員）比照 STAFF 權限（使用者決策）
    @PreAuthorize("hasAnyRole('STAFF', 'TESTER', 'MANAGER', 'BRAND_ADMIN')")
    @Operation(summary = "查詢會員資料")
    public ResponseEntity<MemberDto> getMemberByPhone(@PathVariable String phone) {
        Long brandId = helperService.getCurrentBrandId();
        return membershipQueryHandler.handle(new GetMemberByPhoneQuery(brandId, phone))
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
