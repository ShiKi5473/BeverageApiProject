package tw.niels.beverage_api_project.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.store.entity.Store;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "inventory_snapshots", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"store_id", "inventory_item_id"})
})
@AttributeOverride(name = "id", column = @Column(name = "snapshot_id"))
@Getter
@Setter
public class InventorySnapshot extends BaseTsidEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItem inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

}