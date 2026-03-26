package tw.niels.beverage_api_project.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "inventory_snapshots", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"store_id", "inventory_item_id"})
})
@AttributeOverride(name = "id", column = @Column(name = "snapshot_id"))
@Getter
@Setter
public class InventorySnapshotEntity extends BaseTsidEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_item_id", nullable = false)
    private InventoryItemEntity inventoryItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private StoreEntity store;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal quantity = BigDecimal.ZERO;

    @Column(name = "last_checked_at")
    private Instant lastCheckedAt;

}