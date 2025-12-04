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
import tw.niels.beverage_api_project.modules.product.entity.OptionGroup;
import tw.niels.beverage_api_project.modules.product.entity.Product;
import tw.niels.beverage_api_project.modules.product.entity.ProductOption;
import tw.niels.beverage_api_project.modules.product.enums.ProductStatus;
import tw.niels.beverage_api_project.modules.product.enums.SelectionType;
import tw.niels.beverage_api_project.modules.product.repository.OptionGroupRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductOptionRepository;
import tw.niels.beverage_api_project.modules.product.repository.ProductRepository;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.entity.User;
import tw.niels.beverage_api_project.modules.user.entity.profile.StaffProfile;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;
import tw.niels.beverage_api_project.modules.user.repository.UserRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

@Component
@Profile("dev")
public class DataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final BrandRepository brandRepository;
    private final StoreRepository storeRepository;
    private final ProductRepository productRepository;
    private final OptionGroupRepository optionGroupRepository;
    private final ProductOptionRepository productOptionRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      BrandRepository brandRepository,
                      StoreRepository storeRepository,
                      ProductRepository productRepository,
                      OptionGroupRepository optionGroupRepository,
                      ProductOptionRepository productOptionRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.brandRepository = brandRepository;
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.productOptionRepository = productOptionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        seedDataForK6Testing();
    }

    private void seedDataForK6Testing() {
        // 1. 建立品牌 (ID = 1)
        Brand brand = brandRepository.findById(1L).orElseGet(() -> {
            Brand b = new Brand();
            b.setId(1L);
            b.setName("品茶軒");
            b.setContactPerson("測試員");
            b.setActive(true);
            return brandRepository.save(b);
        });

        // 2. 建立分店 (ID = 1)
        Store store = storeRepository.findByBrand_IdAndId(brand.getBrandId(), 1L).orElseGet(() -> {
            Store s = new Store();
            s.setId(1L);
            s.setBrand(brand);
            s.setName("台北總店");
            s.setAddress("台北市測試路1號");
            s.setPhoneNumber("02-12345678");
            return storeRepository.save(s);
        });

        // 3. 建立或更新店員帳號
        Optional<User> existingUser = userRepository.findByPrimaryPhoneAndBrandId("0911111111", brand.getBrandId());

        if (existingUser.isPresent()) {
            // 強制重設密碼
            User user = existingUser.get();
            user.setPasswordHash(passwordEncoder.encode("password123"));
            userRepository.save(user);
            logger.info("DataSeeder: 已更新測試帳號密碼: 0911111111");
        } else {
            // 建立新帳號
            User user = new User();
            user.setBrand(brand);
            user.setPrimaryPhone("0911111111");
            user.setPasswordHash(passwordEncoder.encode("password123"));
            user.setActive(true);

            StaffProfile profile = new StaffProfile();
            profile.setFullName("K6 測試員");
            profile.setEmployeeNumber("K6-001");
            profile.setRole(StaffRole.MANAGER);
            profile.setStore(store);
            user.setStaffProfile(profile);

            userRepository.save(user);
            logger.info("DataSeeder: 已建立 K6 測試帳號: 0911111111");
        }

        // 4. 建立選項群組與選項
        OptionGroup sugarGroup = optionGroupRepository.findByBrand_IdAndId(brand.getBrandId(), 10L).orElseGet(() -> {
            OptionGroup g = new OptionGroup();
            g.setId(10L);
            g.setBrand(brand);
            g.setName("甜度");
            g.setSelectionType(SelectionType.SINGLE);
            g.setSortOrder(1);
            return optionGroupRepository.save(g);
        });

        if (productOptionRepository.findByOptionGroup_Brand_IdAndId(brand.getBrandId(), 11L).isEmpty()) {
            ProductOption opt = new ProductOption();
            opt.setId(11L);
            opt.setOptionGroup(sugarGroup);
            opt.setOptionName("半糖");
            opt.setPriceAdjustment(BigDecimal.ZERO);
            opt.setDefault(false);
            productOptionRepository.save(opt);
        }

        OptionGroup iceGroup = optionGroupRepository.findByBrand_IdAndId(brand.getBrandId(), 20L).orElseGet(() -> {
            OptionGroup g = new OptionGroup();
            g.setId(20L);
            g.setBrand(brand);
            g.setName("冰塊");
            g.setSelectionType(SelectionType.SINGLE);
            g.setSortOrder(2);
            return optionGroupRepository.save(g);
        });

        if (productOptionRepository.findByOptionGroup_Brand_IdAndId(brand.getBrandId(), 21L).isEmpty()) {
            ProductOption opt = new ProductOption();
            opt.setId(21L);
            opt.setOptionGroup(iceGroup);
            opt.setOptionName("少冰");
            opt.setPriceAdjustment(BigDecimal.ZERO);
            opt.setDefault(false);
            productOptionRepository.save(opt);
        }

        // 5. 建立商品
        Product product = productRepository.findByBrand_IdAndId(brand.getBrandId(), 1L).orElseGet(() -> {
            Product p = new Product();
            p.setId(1L);
            p.setBrand(brand);
            p.setName("招牌紅茶");
            p.setDescription("K6 測試專用商品");
            p.setBasePrice(new BigDecimal("30.00"));
            p.setStatus(ProductStatus.ACTIVE);
            return productRepository.save(p);
        });

        if (product.getOptionGroups() == null || product.getOptionGroups().isEmpty()) {
            product.setOptionGroups(Set.of(sugarGroup, iceGroup));
            productRepository.save(product);
        }

        logger.info("DataSeeder: 初始化資料完成！");
    }
}