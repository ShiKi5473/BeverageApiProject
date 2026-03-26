package tw.niels.beverage_api_project.modules.platform.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.platform.entity.PlatformAdminEntity;

import java.util.Optional;

@Repository
public interface PlatformAdminJpaRepository extends JpaRepository<PlatformAdminEntity, Long> {
    Optional<PlatformAdminEntity> findByUsername(String username);
}
