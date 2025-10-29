package tw.niels.beverage_api_project.modules.order.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;

@Repository
public interface PaymentMethodRepository extends JpaRepository<PaymentMethodEntity, Integer> {

    // 根據 code 查找支付方式 (常用的查找方式)
    Optional<PaymentMethodEntity> findByCode(String code);
}