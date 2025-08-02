package tw.niels.beverage_api_project.modules.user.enums;

public enum StaffRole {
    /**
     * 【新增】品牌管理員 (總店人員)，擁有最高權限，可管理品牌下的所有店家。
     */
    BRAND_ADMIN,

    /**
     * 店經理，擁有單一店家的較高權限
     */
    MANAGER,

    /**
     * 一般店員
     */
    STAFF,

    /**
     * 【新增】測試人員角色，用於系統測試，可能擁有特定權限。
     */
    TESTER
}
