package tw.niels.beverage_api_project.modules.user.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfile;

import java.util.Optional;

public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {
    /**
     * 根據 userId 查找並使用 PESSIMISTIC_WRITE 鎖定該筆資料
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT mp FROM MemberProfile mp WHERE mp.userId = :userId")
    Optional<MemberProfile> findByIdForUpdate(Long userId);
}