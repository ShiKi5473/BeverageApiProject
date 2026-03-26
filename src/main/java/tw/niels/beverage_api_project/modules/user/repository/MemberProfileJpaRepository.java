package tw.niels.beverage_api_project.modules.user.repository;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfileEntity;

import java.util.Optional;

public interface MemberProfileJpaRepository extends JpaRepository<MemberProfileEntity, Long> {
    /**
     * 根據 userId 查找並使用 PESSIMISTIC_WRITE 鎖定該筆資料
     */
    @Query("SELECT mp FROM MemberProfileEntity mp WHERE mp.userId = :userId")
    @NotNull
    Optional<MemberProfileEntity> findById(@NotNull Long userId);
}