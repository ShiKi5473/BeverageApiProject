package tw.niels.beverage_api_project.modules.inventory.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_shipments")
@AttributeOverride(name = "id", column = @Column(name = "shipment_id"))
@Getter
@Setter
public class PurchaseShipment extends BaseTsidEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_user_id")
    private User staff;

    @Column(name = "shipment_date", nullable = false)
    private LocalDateTime shipmentDate;

    @Column()
    private String supplier;

    @Lob
    private String notes;

}