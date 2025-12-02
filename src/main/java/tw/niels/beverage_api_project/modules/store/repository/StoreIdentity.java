package tw.niels.beverage_api_project.modules.store.repository;

/**
 * 用於報表服務的輕量級投影，只包含 ID
 */
public interface StoreIdentity {
    Long getStoreId();
    Long getBrandId();
}