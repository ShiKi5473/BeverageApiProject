package tw.niels.beverage_api_project.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfile;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

import java.math.BigDecimal;

@Component
@Profile("dev") // 限制只在 dev 環境執行
public class DataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final BrandRepository brandRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      BrandRepository brandRepository,
                      StoreRepository storeRepository,
                      ProductRepository productRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.brandRepository = brandRepository;
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        seedDataForK6Testing();
    }

    private void seedDataForK6Testing() {
        // 1. 建立品牌 (強制 ID = 1，配合 K6 腳本)
        Brand brand = brandRepository.findById(1L).orElseGet(() -> {
            Brand b = new Brand();
            b.setId(1L); // 【關鍵】手動指定 ID
            b.setName("品茶軒");
            b.setContactPerson("測試員");
            b.setActive(true);
            return brandRepository.save(b);
        });

        // 2. 建立分店 (強制 ID = 1)
        Store store = storeRepository.findById(1L).orElseGet(() -> {
            Store s = new Store();
            s.setId(1L); // 【關鍵】手動指定 ID
            s.setBrand(brand);
            s.setName("台北總店");
            s.setAddress("台北市測試路1號");
            s.setPhoneNumber("02-12345678");
            return storeRepository.save(s);
        });

        // 3. 建立店員帳號 (0911111111)
        if (userRepository.findByPrimaryPhoneAndBrandId("0911111111", brand.getBrandId()).isEmpty()) {
            User user = new User();
            user.setBrand(brand);
            user.setPrimaryPhone("0911111111");
            user.setPasswordHash(passwordEncoder.encode("password123")); // 預設密碼
            user.setActive(true);

            StaffProfile profile = new StaffProfile();
            profile.setFullName("K6 測試員");
            profile.setEmployeeNumber("K6-001");
            profile.setRole(StaffRole.MANAGER);
            profile.setStore(store); // 綁定分店
            user.setStaffProfile(profile);

            userRepository.save(user);
            logger.info("已建立 K6 測試帳號: 0911111111 / password123 (Brand: 1, Store: 1)");
        }

        // 4. 建立測試商品 (強制 ID = 1)
        if (productRepository.findByBrand_IdAndId(brand.getBrandId(), 1L).isEmpty()) {
            Product product = new Product();
            product.setId(1L); // 【關鍵】手動指定 ID
            product.setBrand(brand);
            product.setName("招牌紅茶");
            product.setDescription("K6 測試專用商品");
            product.setBasePrice(new BigDecimal("30.00"));
            product.setStatus(ProductStatus.ACTIVE);

            productRepository.save(product);
            logger.info("已建立 K6 測試商品: 招牌紅茶 (ID: 1)");
        }
    }
}