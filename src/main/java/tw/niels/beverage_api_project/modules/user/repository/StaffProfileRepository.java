package tw.niels.beverage_api_project.modules.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfile;

public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {
}
