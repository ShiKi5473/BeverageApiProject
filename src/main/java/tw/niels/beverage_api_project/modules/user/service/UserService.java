package tw.niels.beverage_api_project.modules.user.service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.common.constants.AuthorityConstants;
import tw.niels.beverage_api_project.common.exception.DomainException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.common.service.ControllerHelperService;
import tw.niels.beverage_api_project.modules.brand.entity.BrandEntity;
import tw.niels.beverage_api_project.modules.brand.repository.BrandJpaRepository;
import tw.niels.beverage_api_project.modules.order.dto.MemberDto;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.repository.StoreJpaRepository;
import tw.niels.beverage_api_project.modules.user.dto.CreateUserRequestDto;
import tw.niels.beverage_api_project.modules.user.dto.StaffDto;
import tw.niels.beverage_api_project.modules.user.dto.UpdateStaffRequestDto;
import tw.niels.beverage_api_project.modules.user.entity.UserEntity;
import tw.niels.beverage_api_project.modules.user.entity.profile.MemberProfileEntity;
import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfileEntity;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;
import tw.niels.beverage_api_project.modules.user.repository.StaffProfileJpaRepository;
import tw.niels.beverage_api_project.modules.user.repository.UserJpaRepository;
import tw.niels.beverage_api_project.security.AppUserDetails;

@Service
public class UserService {

    private final UserJpaRepository UserJpaRepository;

    private final BrandJpaRepository brandRepository;

    private final StoreJpaRepository storeRepository;

    private final PasswordEncoder passwordEncoder;

    private final StaffProfileJpaRepository staffProfileRepository;

    private final ControllerHelperService helperService;

    public UserService(UserJpaRepository UserJpaRepository, BrandJpaRepository brandRepository, StoreJpaRepository storeRepository,
            PasswordEncoder passwordEncoder,
                       StaffProfileJpaRepository staffProfileRepository,
                       ControllerHelperService helperService) {
        this.UserJpaRepository = UserJpaRepository;
        this.brandRepository = brandRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
        this.staffProfileRepository = staffProfileRepository;
        this.helperService = helperService;
    }

    @Transactional
    public UserEntity createUser(CreateUserRequestDto requestDto) {
        // 1. 檢查品牌是否存在
        BrandEntity BrandEntity = brandRepository.findById(requestDto.getBrandId())
                .orElseThrow(() -> new ResourceNotFoundException("Error: BrandEntity not found with ID: " + requestDto.getBrandId()));

        // 2. 檢查手機號碼是否已在該品牌下註冊
        UserJpaRepository.findByPrimaryPhoneAndBrandId(requestDto.getPrimaryPhone(), requestDto.getBrandId())
                .ifPresent(existingUser -> {
                    throw new DomainException("Error: Phone number '" + requestDto.getPrimaryPhone() + "' is already taken for this BrandEntity!");
                });

        // 3. 建立核心 UserEntity 物件
        UserEntity UserEntity = new UserEntity();
        UserEntity.setBrand(BrandEntity);
        UserEntity.setPrimaryPhone(requestDto.getPrimaryPhone());
        UserEntity.setPasswordHash(passwordEncoder.encode(requestDto.getPassword()));
        UserEntity.setIsActive(true);

        // 4. 根據請求，建立對應的 Profile
        // 建立員工 Profile
        if (requestDto.getStaffProfile() != null) {
            CreateUserRequestDto.StaffProfileDto staffDto = requestDto.getStaffProfile();
            StaffProfileEntity staffProfile = new StaffProfileEntity();
            staffProfile.setFullName(staffDto.getFullName());
            staffProfile.setEmployeeNumber(staffDto.getEmployeeNumber());
            staffProfile.setRole(staffDto.getRole());
            staffProfile.setHireDate(staffDto.getHireDate());

            if (staffDto.getStoreId() != null) {
                // 修改：改拋出 ResourceNotFoundException
                StoreEntity StoreEntity = storeRepository.findByBrand_IdAndId(BrandEntity.getId(), staffDto.getStoreId())
                        .orElseThrow(() -> new ResourceNotFoundException("Error: StoreEntity not found with ID: " + staffDto.getStoreId()));
                staffProfile.setStore(StoreEntity);
            }
            // 建立雙向關聯
            UserEntity.setStaffProfile(staffProfile);
        }

        // 建立會員 Profile
        if (requestDto.getMemberProfile() != null) {
            CreateUserRequestDto.MemberProfileDto memberDto = requestDto.getMemberProfile();
            MemberProfileEntity memberProfile = new MemberProfileEntity();
            memberProfile.setFullName(memberDto.getFullName());
            memberProfile.setEmail(memberDto.getEmail());
            memberProfile.setBirthDate(memberDto.getBirthDate());
            memberProfile.setGender(memberDto.getGender());
            memberProfile.setNotes(memberDto.getNotes());
            UserEntity.setMemberProfile(memberProfile);
        }

        // 5. 儲存 UserEntity (由於 CascadeType.ALL, Profile 會一併被儲存)
        return UserJpaRepository.save(UserEntity);
    }

    /**
     * 依品牌 ID 與使用者 ID 查詢使用者（適用於員工查驗），找不到則拋出例外。
     * 供跨模組的 Application Service 使用，避免直接依賴 UserJpaRepository。
     */
    @Transactional(readOnly = true)
    public UserEntity getUser(Long brandId, Long userId) {
        return UserJpaRepository.findByBrand_IdAndId(brandId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到使用者 (ID: " + userId + ")"));
    }

    /**
     * 依品牌 ID 與使用者 ID 查詢會員（須有 memberProfile），找不到或非會員則拋出例外。
     * 「是否為會員」的判斷封裝在此，外部不需自行 filter。
     * 供跨模組的 Application Service 使用，避免直接依賴 UserJpaRepository。
     */
    @Transactional(readOnly = true)
    public UserEntity getMember(Long brandId, Long memberId) {
        return UserJpaRepository.findByBrand_IdAndId(brandId, memberId)
                .filter(u -> u.getMemberProfile() != null)
                .orElseThrow(() -> new ResourceNotFoundException("找不到會員，ID：" + memberId));
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
        return UserJpaRepository.findByPrimaryPhoneAndBrandId(phone, brandId)
                .filter(UserEntity -> UserEntity.getMemberProfile() != null)
                .map(MemberDto::fromEntity);
    }

    /**
     * 查詢員工列表
     * @param brandId 品牌 ID (強制)
     * @param storeId 分店 ID (可選，若 null 則回傳品牌下所有員工)
     */
    @Transactional(readOnly = true)
    public List<StaffDto> getStaffList(Long brandId, Long storeId) {
        List<StaffProfileEntity> profiles;
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
        StaffProfileEntity targetProfile = staffProfileRepository.findByUser_Brand_IdAndUserId(brandId, targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("找不到員工 ID: " + targetUserId));

        UserEntity targetUser = targetProfile.getUser();

        // 權限檢查邏輯
        AppUserDetails currentUser = helperService.getCurrentUserDetails();
        boolean isCurrentUserBrandAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(AuthorityConstants.ROLE_BRAND_ADMIN));

        // 如果當前操作者「不是」品牌管理員 (即為店長 MANAGER)
        if (!isCurrentUserBrandAdmin) {
            // A. 禁止修改 BRAND_ADMIN 的資料
            if (targetProfile.getRole() == StaffRole.BRAND_ADMIN) {
                throw new DomainException("權限不足：店長無法修改品牌管理員的資料");
            }
            // B. 禁止將任何人提升為 BRAND_ADMIN
            if (dto.getRole() == StaffRole.BRAND_ADMIN) {
                throw new DomainException("權限不足：店長無法指派品牌管理員角色");
            }
            // C. 禁止跨店操作 (雖然 Controller 層有檢查，這裡做雙重保險)
            Long targetStoreId = targetProfile.getStore() != null ? targetProfile.getStore().getId() : null;
            Long currentStoreId = currentUser.storeId();
            if (targetStoreId != null && !targetStoreId.equals(currentStoreId)) {
                throw new DomainException("權限不足：無法修改非本店員工資料");
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
            StoreEntity StoreEntity = storeRepository.findByBrand_IdAndId(brandId, dto.getStoreId())
                    .orElseThrow(() -> new ResourceNotFoundException("找不到分店 ID: " + dto.getStoreId()));
            targetProfile.setStore(StoreEntity);
        } else if (dto.getRole() == StaffRole.BRAND_ADMIN) {
            // 只有品牌管理員允許沒有分店
            targetProfile.setStore(null);
        }

        // 5. 更新帳號啟用狀態 (停權)
        if (dto.getIsActive() != null) {
            targetUser.setIsActive(dto.getIsActive());
        }

        staffProfileRepository.save(targetProfile);
        UserJpaRepository.save(targetUser);

        return StaffDto.fromEntity(targetUser);
    }
}