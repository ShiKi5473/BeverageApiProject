package tw.niels.beverage_api_project.modules.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.member.entity.MemberPointLogEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberPointLogJpaRepository extends JpaRepository<MemberPointLogEntity, Long> {

    // 修正：使用 Brand_Id
    List<MemberPointLogEntity> findByMember_Brand_Id(Long brandId);

    Optional<MemberPointLogEntity> findByMember_Brand_IdAndId(Long brandId, Long id);

    // --- 安全防護 ---

    @Deprecated
    @Override
    @NonNull
    default Optional<MemberPointLogEntity> findById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 findById()，請改用 findByMember_Brand_IdAndId()");
    }

    @Deprecated
    @Override
    @NonNull
    default List<MemberPointLogEntity> findAll() {
        throw new UnsupportedOperationException("禁止使用 findAll()");
    }

    @Deprecated
    @Override
    default void deleteById(@NonNull Long id) {
        throw new UnsupportedOperationException("禁止使用 deleteById()");
    }
}