package tw.niels.beverage_api_project.modules.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.modules.user.dto.CreateUserRequestDto;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.service.UserService;

@RestController
@RequestMapping(ApiPaths.USERS)
public class UserController {

    @Autowired
    private UserService userService;

    // 建立新使用者 (員工或會員)
    // 只有品牌管理員或店長有權限建立新員工
    @PostMapping
    @PreAuthorize("hasAnyRole('BRAND_ADMIN', 'MANAGER')")
    public ResponseEntity<String> createUser(@Valid @RequestBody CreateUserRequestDto createUserRequestDto) {
        try {
            User newUser = userService.createUser(createUserRequestDto);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("User created successfully with ID: " + newUser.getUserId());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }
}
