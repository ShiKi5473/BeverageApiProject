package tw.niels.beverage_api_project.modules.member.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import tw.niels.beverage_api_project.modules.member.entity.MemberPointLog;

@Repository
public interface MemberPointLogRepository extends JpaRepository<MemberPointLog, Long> {

}