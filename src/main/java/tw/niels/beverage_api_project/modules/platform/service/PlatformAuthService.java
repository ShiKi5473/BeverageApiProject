package tw.niels.beverage_api_project.modules.platform.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import tw.niels.beverage_api_project.modules.auth.dto.JwtAuthResponseDto;
import tw.niels.beverage_api_project.modules.platform.dto.PlatformLoginRequestDto;
import tw.niels.beverage_api_project.security.jwt.JwtTokenProvider;

@Service
public class PlatformAuthService {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    //【注意】
    // 我們不需要直接注入 PlatformAdminDetailsService，
    // AuthenticationManager 會自動根據 Token 類型 (UsernamePasswordAuthenticationToken)
    // 去尋找合適的 UserDetailsService。
    // 為了讓平台登入使用 PlatformAdminDetailsService，
    // 我們需要修改 SecurityConfig，讓 AuthenticationManager 知道有兩個 UserDetailsService。

    public JwtAuthResponseDto login(PlatformLoginRequestDto loginRequestDto) {
        // 這裡的認證會由 AuthenticationManager 處理，
        // 它會去呼叫 PlatformAdminDetailsService
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginRequestDto.getUsername(),
                        loginRequestDto.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        String token = tokenProvider.generateToken(authentication);
        return new JwtAuthResponseDto(token);
    }
}