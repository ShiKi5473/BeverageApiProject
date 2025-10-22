// package tw.niels.beverage_api_project.config;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.boot.CommandLineRunner;
// import org.springframework.security.crypto.password.PasswordEncoder;
// import org.springframework.stereotype.Component;
// import org.springframework.transaction.annotation.Transactional;

// import tw.niels.beverage_api_project.modules.brand.entity.Brand;
// import
// tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
// import tw.niels.beverage_api_project.modules.user.entity.User;
// import
// tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfile;
// import tw.niels.beverage_api_project.modules.user.enums.StaffRole;
// import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

// /**
// * 此類別用於在應用程式啟動時，自動填充必要的初始資料。
// * 主要用於解決「第一個品牌管理員」的建立問題。
// */
// @Component
// public class DataSeeder implements CommandLineRunner {

// private static final Logger logger =
// LoggerFactory.getLogger(DataSeeder.class);

// private final UserRepository userRepository;
// private final BrandRepository brandRepository;
// private final PasswordEncoder passwordEncoder;

// // *** 在這裡設定您要為哪個品牌建立管理員 ***
// private static final String TARGET_BRAND_NAME = "品茶軒";
// private static final String ADMIN_PHONE = "0911111111";
// private static final String ADMIN_PASSWORD = "password123";

// public DataSeeder(UserRepository userRepository, BrandRepository
// brandRepository,
// PasswordEncoder passwordEncoder) {
// this.userRepository = userRepository;
// this.brandRepository = brandRepository;
// this.passwordEncoder = passwordEncoder;
// }

// @Override
// @Transactional
// public void run(String... args) throws Exception {
// seedFirstBrandAndAdmin();
// }

// private void seedFirstBrandAndAdmin() {
// // 1. 檢查品牌是否存在，如果不存在就建立它
// Brand brand = brandRepository.findByName(TARGET_BRAND_NAME).orElseGet(() -> {
// logger.info("品牌 '{}' 不存在，現在開始建立...", TARGET_BRAND_NAME);
// Brand newBrand = new Brand();
// newBrand.setName(TARGET_BRAND_NAME);
// newBrand.setContactPerson("自動建立");
// newBrand.setActive(true);
// return brandRepository.save(newBrand);
// });

// // 2. 檢查該品牌下是否已經有任何使用者 (特別是管理員)
// if (userRepository.findByPrimaryPhoneAndBrandId(ADMIN_PHONE,
// brand.getBrandId()).isEmpty()) {
// // 3. 如果沒有，就建立第一個 BRAND_ADMIN
// logger.info("品牌 ID: {} 尚未建立管理員，現在開始建立預設管理員 (帳號: {})...", brand.getBrandId(),
// ADMIN_PHONE);

// // 建立核心 User 物件
// User adminUser = new User();
// adminUser.setBrand(brand);
// adminUser.setPrimaryPhone(ADMIN_PHONE);
// // 使用應用程式的 PasswordEncoder 來加密密碼
// adminUser.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
// adminUser.setActive(true);

// // 建立對應的 StaffProfile
// StaffProfile staffProfile = new StaffProfile();
// staffProfile.setFullName("預設品牌管理員");
// staffProfile.setEmployeeNumber("ADMIN001");
// staffProfile.setRole(StaffRole.BRAND_ADMIN);
// staffProfile.setStore(null); // 品牌管理員不隸屬於特定分店

// // 建立雙向關聯
// adminUser.setStaffProfile(staffProfile);

// userRepository.save(adminUser);
// logger.info("預設管理員 '{}' 已成功建立。請用此帳號登入。", ADMIN_PHONE);
// } else {
// logger.info("品牌 ID: {} 已存在管理員帳號 '{}'，跳過建立程序。", brand.getBrandId(),
// ADMIN_PHONE);
// }
// }
// }