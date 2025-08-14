package tw.niels.beverage_api_project.modules.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfile;

public interface MemberProfileRepository extends JpaRepository<MemberProfile, Long> {
}