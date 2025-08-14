package tw.niels.beverage_api_project.modules.user.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.dto.CreateStaffRequestDto;
import tw.niels.beverage_api_project.modules.user.entity.Staff;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

@Service
public class StaffService {

    private final UserRepository staffRepository;
    private final BrandRepository brandRepository;
    private final StoreRepository storeRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffService(UserRepository staffRepository,
            BrandRepository brandRepository,
            StoreRepository storeRepository,
            PasswordEncoder passwordEncoder) {
        this.staffRepository = staffRepository;
        this.brandRepository = brandRepository;
        this.storeRepository = storeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Staff createStaff(Long brandId, CreateStaffRequestDto requestDto) {
        // 驗證品牌是否存在
        Brand brand = brandRepository.findById(brandId).orElseThrow(() -> new RuntimeException("找不到品牌，ID：" + brandId));
        // 驗證品牌下帳號是否存在
        staffRepository.findByBrand_BrandIdAndUsername(brandId, requestDto.getUserName())
                .ifPresent(existingStaff -> {
                    throw new RuntimeException("帳號 '" + requestDto.getUserName() + "' 在此品牌下已存在");
                });
        // 處理店家資訊
        Store store = null;
        if (requestDto.getStoreId() != null) {
            // 如果提供了 storeId，則驗證該店家是否存在且屬於該品牌
            store = storeRepository.findByBrand_BrandIdAndStoreId(brandId, requestDto.getStoreId())
                    .orElseThrow(
                            () -> new RuntimeException("在品牌 " + brandId + " 下找不到店家，ID：" + requestDto.getStoreId()));
        }
        // 建立新的 Staff 物件
        Staff newStaff = new Staff();
        newStaff.setBrand(brand);
        newStaff.setStore(store);// 如果是總部人員，這裡會是 null
        newStaff.setUsername(requestDto.getUserName());
        newStaff.setFullName(requestDto.getFullName());
        newStaff.setRole(requestDto.getRole());
        // 加密密碼
        newStaff.setPasswordHash(passwordEncoder.encode(requestDto.getPassword()));
        // 設定預設狀態為啟用
        newStaff.setActive(true);
        // 儲存並回傳
        return staffRepository.save(newStaff);

    }

}
