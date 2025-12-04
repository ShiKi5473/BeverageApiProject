package tw.niels.beverage_api_project.modules.inventory.entity;

import jakarta.persistence.*;
import tw.niels.beverage_api_project.common.entity.BaseTsidEntity;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.user.entity.User;

import java.time.LocalDateTime;

@Entity
@Table(name = "purchase_shipments")
@AttributeOverride(name = "id", column = @Column(name = "shipment_id"))
public class PurchaseShipment extends BaseTsidEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_user_id")
    private User staff;

    @Column(name = "shipment_date", nullable = false)
    private LocalDateTime shipmentDate;

    @Column(length = 255)
    private String supplier;

    @Lob
    private String notes;

    // Getters & Setters
    public Long getShipmentId() { return getId(); }

    public void setShipmentId(Long shipmentId) {
        this.setId(shipmentId);
    }

    public Store getStore() { return store; }
    public void setStore(Store store) { this.store = store; }

    public User getStaff() { return staff; }
    public void setStaff(User staff) { this.staff = staff; }

    public LocalDateTime getShipmentDate() { return shipmentDate; }
    public void setShipmentDate(LocalDateTime shipmentDate) { this.shipmentDate = shipmentDate; }

    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}