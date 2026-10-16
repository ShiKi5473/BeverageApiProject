package tw.niels.beverage_api_project.modules.platform.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.config.SecurityConfig;
import tw.niels.beverage_api_project.modules.auth.dto.JwtAuthResponseDto;
import tw.niels.beverage_api_project.modules.platform.dto.PlatformLoginRequestDto;
import tw.niels.beverage_api_project.security.jwt.JwtTokenProvider;

@Service
public class PlatformAuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    /**
     * 【修改】明確注入「平台管理員專用」的 AuthenticationManager（只含 PlatformAdminDetailsService 的 Provider）。
     * 原本共用同一個 ProviderManager（租戶 + 平台），平台登入與租戶登入互相「退回」對方的 Provider；
     * 現在兩條登入路徑完全分離：平台登入只認 platform_admins，租戶登入只認品牌使用者。
     */
    public PlatformAuthService(@Qualifier(SecurityConfig.PLATFORM_AUTH_MANAGER) AuthenticationManager authenticationManager,
                               JwtTokenProvider tokenProvider) {
        this.authenticationManager = authenticationManager;
        this.tokenProvider = tokenProvider;
    }

    public JwtAuthResponseDto login(PlatformLoginRequestDto loginRequestDto) {
        // 認證失敗拋出 AuthenticationException，由 GlobalExceptionHandler 轉為 401 ErrorResponse
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequestDto.getUsername(),
                        loginRequestDto.getPassword()
                )
        );

        // 【修改】登入 API 為無狀態，不再寫入 SecurityContextHolder
        String token = tokenProvider.generateToken(authentication);
        JwtAuthResponseDto responseDto = new JwtAuthResponseDto(token);
        if (!authentication.getAuthorities().isEmpty()) {
            responseDto.setRole(authentication.getAuthorities().iterator().next().getAuthority());
        }
        return responseDto;
    }
}
