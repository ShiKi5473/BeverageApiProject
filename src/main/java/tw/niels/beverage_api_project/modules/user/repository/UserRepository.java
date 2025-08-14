package tw.niels.beverage_api_project.modules.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.user.entity.Staff;
import tw.niels.beverage_api_project.modules.user.entity.User;

@Repository
public interface UserRepository extends JpaRepository<Staff, Long> {

    // 使用手機號碼和品牌ID來尋找使用者，確保多租戶下的帳號唯一性
    @Query("SELECT u FROM User u WHERE u.primaryPhone = :phone AND u.brand.id = :brandId")
    Optional<User> findByPrimaryPhoneAndBrandId(String phone, Long brandId);

    // 為了 CustomUserDetailsService，我們需要一個簡化的查詢
    // 注意：在真實的多租戶系統中，登入時應有辦法確定 brandId
    Optional<User> findByPrimaryPhone(String primaryPhone);
}
