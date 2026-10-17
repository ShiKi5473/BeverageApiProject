package tw.niels.beverage_api_project.common.constants;

/**
 * 權限相關常數
 */
public final class AuthorityConstants {
    
    private AuthorityConstants() {}

    public static final String ROLE_PLATFORM_ADMIN = "ROLE_PLATFORM_ADMIN";
    public static final String ROLE_BRAND_ADMIN = "ROLE_BRAND_ADMIN";
    public static final String ROLE_MANAGER = "ROLE_MANAGER";
    public static final String ROLE_STAFF = "ROLE_STAFF";
    // 新增：測試人員角色（V16 加入 staff_role enum），權限比照 STAFF
    public static final String ROLE_TESTER = "ROLE_TESTER";
    public static final String ROLE_MEMBER = "ROLE_MEMBER";

    // 常用組合
    public static final String HAS_ROLE_PLATFORM_ADMIN = "hasRole('" + ROLE_PLATFORM_ADMIN + "')";
    public static final String HAS_ROLE_BRAND_ADMIN = "hasRole('" + ROLE_BRAND_ADMIN + "')";
    public static final String HAS_ANY_ROLE_ADMIN = "hasAnyRole('" + ROLE_PLATFORM_ADMIN + "', '" + ROLE_BRAND_ADMIN + "')";
    // 修改：STAFF 相關組合一併納入 TESTER（使用者決策：TESTER 比照 STAFF）
    public static final String HAS_ANY_ROLE_STAFF = "hasAnyRole('" + ROLE_MANAGER + "', '" + ROLE_STAFF + "', '" + ROLE_TESTER + "')";
    public static final String HAS_ANY_ROLE_STORE_ACCESS = "hasAnyRole('" + ROLE_BRAND_ADMIN + "', '" + ROLE_MANAGER + "', '" + ROLE_STAFF + "', '" + ROLE_TESTER + "')";
}
