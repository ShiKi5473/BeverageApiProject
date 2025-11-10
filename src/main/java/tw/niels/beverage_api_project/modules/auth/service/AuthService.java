package tw.niels.beverage_api_project.modules.auth.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import tw.niels.beverage_api_project.modules.auth.dto.JwtAuthResponseDto;
import tw.niels.beverage_api_project.modules.auth.dto.LoginRequestDto;
import tw.niels.beverage_api_project.security.AppUserDetails;
import tw.niels.beverage_api_project.security.BrandContextHolder;
import tw.niels.beverage_api_project.security.jwt.JwtTokenProvider;

@Service
public class AuthService {
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private JwtTokenProvider tokenProvider;

    public JwtAuthResponseDto login(LoginRequestDto loginRequestDto) {
        try {
            // 在認證前，將 brandId 存入 ThreadLocal
            BrandContextHolder.setBrandId(loginRequestDto.getBrandId());

            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequestDto.getUsername(),
                            loginRequestDto.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String token = tokenProvider.generateToken(authentication);

            JwtAuthResponseDto responseDto = new JwtAuthResponseDto(token);
            Object principal = authentication.getPrincipal();
            if(principal instanceof AppUserDetails userDetails) {
                responseDto.setStoreId(userDetails.getStoreId()); //
            }
            return responseDto;
        } finally {
            // 無論認證成功或失敗，最後都要清除 ThreadLocal，避免記憶體洩漏
            BrandContextHolder.clear();
        }
    }
}
