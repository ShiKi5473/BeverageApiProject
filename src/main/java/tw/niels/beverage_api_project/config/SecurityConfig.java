package tw.niels.beverage_api_project.config;

import java.util.Arrays;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.security.jwt.JwtAuthenticationEntryPoint;
import tw.niels.beverage_api_project.security.jwt.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {


    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAuthenticationFilter authenticationFilter;

    // 從 application.properties 讀取允許的 CORS 來源，避免寫死於程式碼中
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            @Qualifier("customUserDetailsService") UserDetailsService tenantUserDetailsService,
            @Qualifier("platformAdminDetailsService") UserDetailsService platformAdminDetailsService,
            PasswordEncoder passwordEncoder) {

        // 建立給「品牌租戶」用的 Provider
        DaoAuthenticationProvider tenantProvider = new DaoAuthenticationProvider();
        tenantProvider.setUserDetailsService(tenantUserDetailsService);
        tenantProvider.setPasswordEncoder(passwordEncoder);

        // 建立給「平台管理員」用的 Provider
        DaoAuthenticationProvider platformProvider = new DaoAuthenticationProvider();
        platformProvider.setUserDetailsService(platformAdminDetailsService);
        platformProvider.setPasswordEncoder(passwordEncoder);

        // ProviderManager 會依序嘗試所有 Provider
        return new ProviderManager(tenantProvider, platformProvider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // sse-ticket 需要認證才能呼叫（必須放在 auth/** permitAll 之前，否則會被覆蓋）
                        .requestMatchers(ApiPaths.API_V1 + ApiPaths.AUTH + "/sse-ticket").authenticated()
                        .requestMatchers(ApiPaths.API_V1 + ApiPaths.AUTH + "/**").permitAll() // 允許 "品牌" 登入、訪客登入
                        .requestMatchers(ApiPaths.API_V1 + "/platform/auth/login").permitAll() // 允許 "平台" 登入
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/ws-kds/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                );

        http.addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 統一的 CORS 設定
     * 
     * @return CorsConfigurationSource
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // 允許的來源從 application.properties 的 app.cors.allowed-origins 讀取
        configuration.setAllowedOrigins(allowedOrigins);
        // 允許所有標準的 HTTP 方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS", "HEAD"));
        // 允許前端攜帶認證資訊 (例如 Cookies 或 Authorization header)
        configuration.setAllowCredentials(true);
        // 允許所有常見的標頭
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "x-ijt"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 將此 CORS 設定應用到所有路徑 ("/**")
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}