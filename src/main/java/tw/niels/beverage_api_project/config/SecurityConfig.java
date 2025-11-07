package tw.niels.beverage_api_project.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
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
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationEntryPoint authenticationEntryPoint;
    @Autowired
    private JwtAuthenticationFilter authenticationFilter;

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
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception.authenticationEntryPoint(authenticationEntryPoint))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(ApiPaths.API_V1 + ApiPaths.AUTH + "/**").permitAll() // 允許 "品牌" 登入
                        .requestMatchers(ApiPaths.API_V1 + "/platform/auth/login").permitAll() // 【新增】允許 "平台" 登入
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/ws-kds/**").permitAll()
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
        // 允許來自您前端 Live Server 的來源
        configuration.setAllowedOrigins(Arrays.asList(
                "http://localhost:63342",
                "http://127.0.0.1:63342"
        ));
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