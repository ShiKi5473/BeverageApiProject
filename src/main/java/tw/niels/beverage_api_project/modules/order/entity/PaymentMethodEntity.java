package tw.niels.beverage_api_project.modules.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment_methods")
public class PaymentMethodEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_method_id")
    private Integer paymentMethodId; // 與資料庫 SERIAL/INTEGER 匹配

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    // 無參數建構子
    public PaymentMethodEntity() {
    }

    // Getters
    public Integer getPaymentMethodId() {
        return paymentMethodId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    // Setters (通常查找表的實體不太需要 Setter，但保留以防萬一)
    public void setPaymentMethodId(Integer paymentMethodId) {
        this.paymentMethodId = paymentMethodId;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setName(String name) {
        this.name = name;
    }
}