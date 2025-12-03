package tw.niels.beverage_api_project.modules.inventory.dao;

import java.math.BigDecimal;
import java.util.List;

/**
 * 庫存批次 DAO 介面
 * 定義高效能的 JDBC 批次操作規範。
 */
public interface InventoryBatchDAO {

    /**
     * 執行批次更新庫存數量
     */
    void batchUpdateQuantities(List<BatchUpdateTuple> updates);

    /**
     * 資料載體 DTO (定義在介面中方便引用)
     */
    record BatchUpdateTuple(Long batchId, BigDecimal newQuantity) {}
}