package tw.niels.beverage_api_project.modules.user.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.order.dto.MemberDto;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.dto.CreateUserRequestDto;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfile;
import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfile;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

@Service
public class UserService {

    private final UserRepository userRepository;

    private final BrandRepository brandRepository;

    private final StoreRepository storeRepository;

    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, BrandRepository brandRepository, StoreRepository storeRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.brandRepository = brandRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User createUser(CreateUserRequestDto requestDto) {
        // 1. 檢查品牌是否存在
        Brand brand = brandRepository.findById(requestDto.getBrandId())
                .orElseThrow(() -> new RuntimeException("Error: Brand is not found."));

        // 2. 檢查手機號碼是否已在該品牌下註冊
        userRepository.findByPrimaryPhoneAndBrandId(requestDto.getPrimaryPhone(), requestDto.getBrandId())
                .ifPresent(existingUser -> {
                    throw new RuntimeException("Error: Phone number is already taken for this brand!");
                });

        // 3. 建立核心 User 物件
        User user = new User();
        user.setBrand(brand);
        user.setPrimaryPhone(requestDto.getPrimaryPhone());
        user.setPasswordHash(passwordEncoder.encode(requestDto.getPassword()));
        user.setActive(true);

        // 4. 根據請求，建立對應的 Profile
        // 建立員工 Profile
        if (requestDto.getStaffProfile() != null) {
            CreateUserRequestDto.StaffProfileDto staffDto = requestDto.getStaffProfile();
            StaffProfile staffProfile = new StaffProfile();
            staffProfile.setFullName(staffDto.getFullName());
            staffProfile.setEmployeeNumber(staffDto.getEmployeeNumber());
            staffProfile.setRole(staffDto.getRole());
            staffProfile.setHireDate(staffDto.getHireDate());

            if (staffDto.getStoreId() != null) {
                Store store = storeRepository.findById(staffDto.getStoreId())
                        .orElseThrow(() -> new RuntimeException("Error: Store is not found."));
                staffProfile.setStore(store);
            }
            // 建立雙向關聯
            user.setStaffProfile(staffProfile);
        }

        // 建立會員 Profile
        if (requestDto.getMemberProfile() != null) {
            CreateUserRequestDto.MemberProfileDto memberDto = requestDto.getMemberProfile();
            MemberProfile memberProfile = new MemberProfile();
            memberProfile.setFullName(memberDto.getFullName());
            memberProfile.setEmail(memberDto.getEmail());
            memberProfile.setBirthDate(memberDto.getBirthDate());
            memberProfile.setGender(memberDto.getGender());
            memberProfile.setNotes(memberDto.getNotes());
            user.setMemberProfile(memberProfile);
        }

        // 5. 儲存 User (由於 CascadeType.ALL, Profile 會一併被儲存)
        return userRepository.save(user);
    }

    /**
     * 根據手機號碼和品牌 ID 查找會員資訊
     *
     * @param phone   會員手機號碼
     * @param brandId 當前操作的品牌 ID
     * @return 包含會員資訊的 Optional<MemberDto>
     */
    @Transactional
    public Optional<MemberDto> findMemberByPhone(String phone, Long brandId) {
        return userRepository.findByPrimaryPhoneAndBrandId(phone, brandId)
                .filter(user -> user.getMemberProfile() != null)
                .map(MemberDto::fromEntity);
    }
}