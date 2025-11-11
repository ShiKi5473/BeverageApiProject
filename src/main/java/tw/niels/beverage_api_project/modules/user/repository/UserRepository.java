package tw.niels.beverage_api_project.modules.user.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.user.entity.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // 使用手機號碼和品牌ID來尋找使用者，確保多租戶下的帳號唯一性
    @Query("SELECT u FROM User u WHERE u.primaryPhone = :phone AND u.brand.brandId = :brandId")
    Optional<User> findByPrimaryPhoneAndBrandId(String phone, Long brandId);



    /**
     * 根據品牌ID和使用者ID查找
     */
    @Query("SELECT u FROM User u WHERE u.brand.brandId = :brandId AND u.userId = :userId")
    Optional<User> findByBrand_BrandIdAndUserId(Long brandId, Long userId);

    /**
     * 禁用預設的 findById，強迫使用 findByBrand_BrandIdAndUserId()。
     */
    @Deprecated
    @Override
    @NonNull
    default Optional<User> findById(@NonNull Long brandId) {
        throw new UnsupportedOperationException("禁止呼叫預設的 findById()，請改用 findByBrand_BrandIdAndUserId() 以確保資料隔離");
    }

    /**
     * 禁用預設的 findAll。
     */
    @Deprecated
    @Override
    @NonNull
    default List<User> findAll() {
        throw new UnsupportedOperationException("禁止呼叫預設的 findAll()，請改用包含 brandId 的自訂查詢");
    }

    /**
     * 禁用預設的 deleteById。
     */
    @Deprecated
    @Override
    default void deleteById(@NonNull Long brandId) {
        throw new UnsupportedOperationException("禁止呼叫預設的 deleteById()，請先驗證 BrandId 再進行刪除");
    }

    /**
     * 禁用預設的 existsById。
     */
    @Deprecated
    @Override
    default boolean existsById(@NonNull Long brandId) {
        throw new UnsupportedOperationException("禁止呼叫預設的 existsById()，請改用包含 brandId 的自訂查詢");
    }
}
