package tw.niels.beverage_api_project.modules.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.config.SecurityConfig;
import tw.niels.beverage_api_project.modules.auth.dto.GuestLoginRequestDto;
import tw.niels.beverage_api_project.modules.auth.dto.JwtAuthResponseDto;
import tw.niels.beverage_api_project.modules.auth.dto.LoginRequestDto;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.security.AppUserDetails;
import tw.niels.beverage_api_project.security.BrandContextHolder;
import tw.niels.beverage_api_project.security.jwt.JwtTokenProvider;

@Service
public class AuthService {
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;
    private final BrandJpaRepository brandJpaRepository;
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    /**
     * 【修改】明確注入「租戶專用」的 AuthenticationManager（只含員工／會員 Provider），
     * 平台管理員無法透過 /auth/login 取得 token。
     */
    public AuthService(@Qualifier(SecurityConfig.TENANT_AUTH_MANAGER) AuthenticationManager authenticationManager,
                       JwtTokenProvider tokenProvider,
                       BrandJpaRepository brandJpaRepository) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
        this.brandJpaRepository = brandJpaRepository;
    }

    /**
     * 訪客登入
     * <p>
     * 【修改】必須指定品牌，且品牌需存在並啟用；guest token 內含 type=GUEST、brandId、name。
     * </p>
     */
    public JwtAuthResponseDto guestLogin(GuestLoginRequestDto requestDto) {
        // 驗證品牌存在且啟用（唯讀查詢），避免簽發指向不存在品牌的訪客 token
        boolean brandActive = brandJpaRepository.findById(requestDto.brandId())
                .map(brand -> Boolean.TRUE.equals(brand.getIsActive()))
                .orElse(false);
        if (!brandActive) {
            throw new ResourceNotFoundException("找不到品牌或品牌未啟用 (ID: " + requestDto.brandId() + ")");
        }

        logger.debug("Generating guest token for brand: {}", requestDto.brandId());
        String token = tokenProvider.generateGuestToken(requestDto.displayName(), requestDto.brandId());
        // 訪客沒有 storeId；role 回傳 ROLE_GUEST 方便前端判斷
        JwtAuthResponseDto responseDto = new JwtAuthResponseDto(token);
        responseDto.setRole("ROLE_GUEST");
        return responseDto;
    }

    public JwtAuthResponseDto login(LoginRequestDto loginRequestDto) {
        try {
            logger.debug("Attempting login for brand: {}", loginRequestDto.getBrandId());
            // 在認證前，將 brandId 存入 ThreadLocal（CustomUserDetailsService 依此做租戶隔離查詢）
            BrandContextHolder.setBrandId(loginRequestDto.getBrandId());

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequestDto.getUsername(),
                            loginRequestDto.getPassword()));

            // 【修改】不再寫入 SecurityContextHolder：登入 API 為無狀態，token 回傳給前端即可
            String token = tokenProvider.generateToken(authentication);

            JwtAuthResponseDto responseDto = new JwtAuthResponseDto(token);
            Object principal = authentication.getPrincipal();
            if (principal instanceof AppUserDetails userDetails) {
                responseDto.setStoreId(userDetails.storeId());
                responseDto.setRole(userDetails.primaryRole());
                logger.info("Login successful for userId: {}, brand: {}", userDetails.userId(), userDetails.brandId());
            }
            return responseDto;
        } finally {
            // 無論認證成功或失敗，最後都要清除 ThreadLocal，避免記憶體洩漏
            BrandContextHolder.clear();
        }
    }
}
