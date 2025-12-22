package tw.niels.beverage_api_project.modules.inventory.dao;

import org.jetbrains.annotations.NotNull;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * 庫存批次 DAO 實作
 * 使用 JdbcTemplate 執行實際的 SQL 操作。
 */
@Repository
public class InventoryBatchDAOImpl implements tw.niels.beverage_api_project.modules.inventory.dao.InventoryBatchDAO {

    private final JdbcTemplate jdbcTemplate;

    public InventoryBatchDAOImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void batchUpdateQuantities(List<BatchUpdateTuple> updates) {
        if (updates == null || updates.isEmpty()) {
            return;
        }

        String sql = "UPDATE inventory_batches SET current_quantity = ? WHERE batch_id = ?";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(@NotNull PreparedStatement ps, int i) throws SQLException {
                BatchUpdateTuple tuple = updates.get(i);
                ps.setBigDecimal(1, tuple.newQuantity());
                ps.setLong(2, tuple.batchId());
            }

            @Override
            public int getBatchSize() {
                return updates.size();
            }
        });
    }
}