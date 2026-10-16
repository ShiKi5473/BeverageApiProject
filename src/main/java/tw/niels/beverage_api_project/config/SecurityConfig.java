package tw.niels.beverage_api_project.config;

import java.util.Arrays;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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

import jakarta.servlet.DispatcherType;
import tw.niels.beverage_api_project.common.constants.ApiPaths;
import tw.niels.beverage_api_project.security.jwt.JwtAccessDeniedHandler;
import tw.niels.beverage_api_project.security.jwt.JwtAuthenticationEntryPoint;
import tw.niels.beverage_api_project.security.jwt.JwtAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 品牌租戶（員工／會員）登入用 AuthenticationManager 的 Bean 名稱 */
    public static final String TENANT_AUTH_MANAGER = "tenantAuthenticationManager";
    /** 平台管理員登入用 AuthenticationManager 的 Bean 名稱 */
    public static final String PLATFORM_AUTH_MANAGER = "platformAuthenticationManager";

    private final JwtAuthenticationEntryPoint authenticationEntryPoint;
    private final JwtAccessDeniedHandler accessDeniedHandler;
    private final JwtAuthenticationFilter authenticationFilter;

    // 從 application.properties 讀取允許的 CORS 來源，避免寫死於程式碼中
    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 品牌租戶（員工／會員）登入專用的 AuthenticationManager。
     * <p>
     * 【修正】原本單一 ProviderManager 同時包含租戶與平台兩個 Provider，
     * 租戶查無使用者時會「退回」平台 Provider → 平台管理員可透過 /auth/login 取得 token。
     * 改為兩個獨立的 AuthenticationManager，各自只含一個 Provider。
     * 標記 @Primary：依型別注入 AuthenticationManager 時（含 Spring Security 內部）預設使用租戶版本。
     * </p>
     */
    @Bean(name = TENANT_AUTH_MANAGER)
    @Primary
    public AuthenticationManager tenantAuthenticationManager(
            @Qualifier("customUserDetailsService") UserDetailsService tenantUserDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider tenantProvider = new DaoAuthenticationProvider();
        tenantProvider.setUserDetailsService(tenantUserDetailsService);
        tenantProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(tenantProvider);
    }

    /**
     * 【新增】平台管理員登入專用的 AuthenticationManager（只查 platform_admins）。
     */
    @Bean(name = PLATFORM_AUTH_MANAGER)
    public AuthenticationManager platformAuthenticationManager(
            @Qualifier("platformAdminDetailsService") UserDetailsService platformAdminDetailsService,
            PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider platformProvider = new DaoAuthenticationProvider();
        platformProvider.setUserDetailsService(platformAdminDetailsService);
        platformProvider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(platformProvider);
    }

    /**
     * 【新增】停用 JwtAuthenticationFilter 的 Servlet 容器自動註冊。
     * <p>
     * 標了 @Component 的 Filter 會被 Spring Boot 自動註冊成一般 Servlet Filter，
     * 再加上 Security Filter Chain 內的那一次，會在兩個位置各執行一次（OncePerRequestFilter 只能部分避免）。
     * 停用後只在 Security Filter Chain 中執行。
     * </p>
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                // 【修改】401 與 403 都回傳統一的 JSON ErrorResponse
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 【新增】SSE 的非同步 dispatch（ASYNC）與錯誤頁 dispatch（ERROR）放行：
                        // 初始請求已完成認證；ASYNC dispatch 時 Stateless 的 SecurityContext 不存在，
                        // 若不放行，SSE 推送/逾時時會被判定為未認證並嘗試寫 401（回應已 commit → 例外）
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        // sse-ticket 需要認證才能呼叫（必須放在 auth/** permitAll 之前，否則會被覆蓋）
                        .requestMatchers(ApiPaths.API_V1 + ApiPaths.AUTH + "/sse-ticket").authenticated()
                        .requestMatchers(ApiPaths.API_V1 + ApiPaths.AUTH + "/**").permitAll() // 允許 "品牌" 登入、訪客登入
                        .requestMatchers(ApiPaths.API_V1 + "/platform/auth/login").permitAll() // 允許 "平台" 登入
                        .requestMatchers("/error").permitAll()
                        // STOMP 握手放行；CONNECT 時由 JwtAuthChannelInterceptor 驗證 token（無效即拒絕）
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
        // 【修改】允許的標頭：移除無用的 x-ijt，新增冪等鍵 Idempotency-Key 與語系 Accept-Language
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "Idempotency-Key", "Accept-Language"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 將此 CORS 設定應用到所有路徑 ("/**")
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
