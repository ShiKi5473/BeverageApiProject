package tw.niels.beverage_api_project.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.user.entity.Staff;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;
import tw.niels.beverage_api_project.modules.user.repository.StaffRepository;

import java.util.List;

/**
 * 此類別用於在應用程式啟動時，自動填充必要的初始資料。
 * 主要用於解決「第一個品牌管理員」的建立問題。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    private final StaffRepository staffRepository;
    private final BrandRepository brandRepository;
    private final PasswordEncoder passwordEncoder;

    // *** 在這裡設定您要為哪個品牌建立管理員 ***
    private static final Long TARGET_BRAND_ID = 1L;

    public DataSeeder(StaffRepository staffRepository, BrandRepository brandRepository, PasswordEncoder passwordEncoder) {
        this.staffRepository = staffRepository;
        this.brandRepository = brandRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        seedFirstBrandAdmin();
    }

    private void seedFirstBrandAdmin() {
        // 1. 檢查目標品牌是否存在
        brandRepository.findById(TARGET_BRAND_ID).ifPresentOrElse(
            brand -> {
                // 2. 檢查該品牌是否已經有任何 BRAND_ADMIN
                List<Staff> admins = staffRepository.findByBrand_BrandIdAndRole(TARGET_BRAND_ID, StaffRole.BRAND_ADMIN);

                if (admins.isEmpty()) {
                    // 3. 如果沒有，就建立第一個 BRAND_ADMIN
                    logger.info("品牌 ID: {} 尚未建立管理員，現在開始建立預設管理員...", TARGET_BRAND_ID);

                    Staff adminStaff = new Staff();
                    adminStaff.setBrand(brand);
                    adminStaff.setStore(null); // 品牌管理員不隸屬於特定分店
                    
                    // *** 在這裡設定預設管理員的帳號密碼 ***
                    adminStaff.setUsername("admin");
                    adminStaff.setPasswordHash(passwordEncoder.encode("password123"));
                    adminStaff.setFullName("預設品牌管理員");
                    
                    adminStaff.setRole(StaffRole.BRAND_ADMIN);
                    adminStaff.setActive(true);
                    
                    staffRepository.save(adminStaff);
                    logger.info("預設管理員 'admin' 已成功建立。");
                } else {
                    logger.info("品牌 ID: {} 已存在管理員，跳過建立程序。", TARGET_BRAND_ID);
                }
            },
            () -> logger.warn("找不到目標品牌 ID: {}，無法建立預設管理員。", TARGET_BRAND_ID)
        );
    }
}
