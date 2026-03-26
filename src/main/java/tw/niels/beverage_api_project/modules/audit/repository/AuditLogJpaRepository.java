package tw.niels.beverage_api_project.modules.audit.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import tw.niels.beverage_api_project.modules.audit.entity.AuditLogEntity;

import java.util.List;

@Repository
public interface AuditLogJpaRepository extends MongoRepository<AuditLogEntity, String> {
    // 支援基本查詢，例如查某人的操作紀錄
    List<AuditLogEntity> findByOperatorIdOrderByTimestampDesc(Long operatorId);
}