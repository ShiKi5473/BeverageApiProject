package tw.niels.beverage_api_project.security;

public class BrandContextHolder {

    private static final ThreadLocal<Long> brandIdHolder = new ThreadLocal<>();

    public static void setBrandId(Long brandId) {
        brandIdHolder.set(brandId);
    }

    public static Long getBrandId() {
        return brandIdHolder.get();
    }

    public static void clear() {
        brandIdHolder.remove();
    }
}