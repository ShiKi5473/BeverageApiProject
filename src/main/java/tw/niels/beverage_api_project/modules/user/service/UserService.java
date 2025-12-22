package tw.niels.beverage_api_project.modules.user.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.order.dto.MemberDto;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.dto.CreateUserRequestDto;
import tw.niels.beverage_api_project.modules.user.dto.StaffDto;
import tw.niels.beverage_api_project.modules.user.dto.UpdateStaffRequestDto;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfile;
import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfile;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;
import tw.niels.beverage_api_project.modules.user.repository.StaffProfileRepository;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;
import tw.niels.beverage_api_project.security.AppUserDetails;

@Service
public class UserService {

    private final UserRepository userRepository;

    private final BrandRepository brandRepository;

    private final StoreRepository storeRepository;

    private final PasswordEncoder passwordEncoder;

    private final StaffProfileRepository staffProfileRepository;

    private final ControllerHelperService helperService;

    public UserService(UserRepository userRepository, BrandRepository brandRepository, StoreRepository storeRepository,
            PasswordEncoder passwordEncoder,
                       StaffProfileRepository staffProfileRepository,
                       ControllerHelperService helperService) {
        this.userRepository = userRepository;
        this.brandRepository = brandRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
        this.staffProfileRepository = staffProfileRepository;
        this.helperService = helperService;
    }

    @Transactional
    public User createUser(CreateUserRequestDto requestDto) {
        // 1. 檢查品牌是否存在
        Brand brand = brandRepository.findById(requestDto.getBrandId())
                .orElseThrow(() -> new ResourceNotFoundException("Error: Brand not found with ID: " + requestDto.getBrandId()));

        // 2. 檢查手機號碼是否已在該品牌下註冊
        userRepository.findByPrimaryPhoneAndBrandId(requestDto.getPrimaryPhone(), requestDto.getBrandId())
                .ifPresent(existingUser -> {
                    throw new IllegalStateException("Error: Phone number '" + requestDto.getPrimaryPhone() + "' is already taken for this brand!");
                });

        // 3. 建立核心 User 物件
        User user = new User();
        user.setBrand(brand);
        user.setPrimaryPhone(requestDto.getPrimaryPhone());
        user.setPasswordHash(passwordEncoder.encode(requestDto.getPassword()));
        user.setIsActive(true);

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
                // 修改：改拋出 ResourceNotFoundException
                Store store = storeRepository.findByBrand_IdAndId(brand.getId(), staffDto.getStoreId())
                        .orElseThrow(() -> new ResourceNotFoundException("Error: Store not found with ID: " + staffDto.getStoreId()));
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

    /**
     * 查詢員工列表
     * @param brandId 品牌 ID (強制)
     * @param storeId 分店 ID (可選，若 null 則回傳品牌下所有員工)
     */
    @Transactional(readOnly = true)
    public List<StaffDto> getStaffList(Long brandId, Long storeId) {
        List<StaffProfile> profiles;
        if (storeId != null) {
            profiles = staffProfileRepository.findByUser_Brand_IdAndStore_Id(brandId, storeId);
        } else {
            profiles = staffProfileRepository.findByUser_Brand_Id(brandId);
        }

        return profiles.stream()
                .map(profile -> StaffDto.fromEntity(profile.getUser()))
                .collect(Collectors.toList());
    }

    /**
     * 更新員工資料 (調店、升職、停權)
     * 【強化】加入權限檢查，防止越權
     */
    @Transactional
    public StaffDto updateStaff(Long brandId, Long targetUserId, UpdateStaffRequestDto dto) {
        // 1. 查詢目標員工
        StaffProfile targetProfile = staffProfileRepository.findByUser_Brand_IdAndUserId(brandId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到員工 ID: " + targetUserId));

        User targetUser = targetProfile.getUser();

        // 權限檢查邏輯
        AppUserDetails currentUser = helperService.getCurrentUserDetails();
        boolean isCurrentUserBrandAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_BRAND_ADMIN"));

        // 如果當前操作者「不是」品牌管理員 (即為店長 MANAGER)
        if (!isCurrentUserBrandAdmin) {
            // A. 禁止修改 BRAND_ADMIN 的資料
            if (targetProfile.getRole() == StaffRole.BRAND_ADMIN) {
                throw new IllegalStateException("權限不足：店長無法修改品牌管理員的資料");
            }
            // B. 禁止將任何人提升為 BRAND_ADMIN
            if (dto.getRole() == StaffRole.BRAND_ADMIN) {
                throw new IllegalStateException("權限不足：店長無法指派品牌管理員角色");
            }
            // C. 禁止跨店操作 (雖然 Controller 層有檢查，這裡做雙重保險)
            Long targetStoreId = targetProfile.getStore() != null ? targetProfile.getStore().getId() : null;
            Long currentStoreId = currentUser.getStoreId();
            if (targetStoreId != null && !targetStoreId.equals(currentStoreId)) {
                throw new IllegalStateException("權限不足：無法修改非本店員工資料");
            }
        }

        // 2. 更新基本資料
        if (dto.getFullName() != null) {
            targetProfile.setFullName(dto.getFullName());
        }

        // 3. 更新角色
        if (dto.getRole() != null) {
            targetProfile.setRole(dto.getRole());
        }

        // 4. 更新所屬分店 (調店)
        if (dto.getStoreId() != null) {
            Store store = storeRepository.findByBrand_IdAndId(brandId, dto.getStoreId())
                    .orElseThrow(() -> new ResourceNotFoundException("找不到分店 ID: " + dto.getStoreId()));
            targetProfile.setStore(store);
        } else if (dto.getRole() == StaffRole.BRAND_ADMIN) {
            // 只有品牌管理員允許沒有分店
            targetProfile.setStore(null);
        }

        // 5. 更新帳號啟用狀態 (停權)
        if (dto.getIsActive() != null) {
            targetUser.setIsActive(dto.getIsActive());
        }

        staffProfileRepository.save(targetProfile);
        userRepository.save(targetUser);

        return StaffDto.fromEntity(targetUser);
    }
}