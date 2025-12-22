package tw.niels.beverage_api_project.modules.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfile;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

    // 1. 查詢品牌下所有員工 (User -> Brand)
    List<StaffProfile> findByUser_Brand_Id(Long brandId);

    // 2. 查詢特定分店的員工
    List<StaffProfile> findByUser_Brand_IdAndStore_Id(Long brandId, Long storeId);

    // 3. 查詢特定員工 (用於詳情或更新)
    Optional<StaffProfile> findByUser_Brand_IdAndUserId(Long brandId, Long userId);

    // --- 安全防護：禁用預設方法 ---

    @Deprecated
    @Override
    @NonNull
    default List<StaffProfile> findAll() {
        throw new UnsupportedOperationException("禁止使用 findAll()，請改用 findByUser_Brand_Id()");
    }

    @Deprecated
    @Override
    @NonNull
    default Optional<StaffProfile> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 findById()，請改用 findByUser_Brand_IdAndUserId()");
    }

    // 刪除員工通常是透過 User 的 isActive 設為 false (軟刪除)，或透過 UserService 刪除 User
    // 這裡暫不覆寫 deleteById，但建議避免直接使用

    @Deprecated
    @Override
    default void deleteById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 deleteById()");

    }
}