package tw.niels.beverage_api_project.modules.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.order.entity.Order;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

}
