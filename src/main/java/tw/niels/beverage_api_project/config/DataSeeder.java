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
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryBatch;
import tw.niels.beverage_api_project.modules.inventory.entity.InventoryItem;
import tw.niels.beverage_api_project.modules.inventory.entity.PurchaseShipment;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryBatchRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.InventoryItemRepository;
import tw.niels.beverage_api_project.modules.inventory.repository.PurchaseShipmentRepository;
import tw.niels.beverage_api_project.modules.inventory.service.InventoryService;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryBatchRepository inventoryBatchRepository;
    private final PurchaseShipmentRepository purchaseShipmentRepository;

    public DataSeeder(UserRepository userRepository,
                      BrandRepository brandRepository,
                      StoreRepository storeRepository,
                      ProductRepository productRepository,
                      OptionGroupRepository optionGroupRepository,
                      ProductOptionRepository productOptionRepository,
                      PasswordEncoder passwordEncoder,
                      InventoryItemRepository inventoryItemRepository,
                      InventoryBatchRepository inventoryBatchRepository,
                      PurchaseShipmentRepository purchaseShipmentRepository) {
        this.userRepository = userRepository;
        this.brandRepository = brandRepository;
        this.storeRepository = storeRepository;
        this.productRepository = productRepository;
        this.optionGroupRepository = optionGroupRepository;
        this.productOptionRepository = productOptionRepository;
        this.passwordEncoder = passwordEncoder;
        this.inventoryItemRepository = inventoryItemRepository;
        this.inventoryBatchRepository = inventoryBatchRepository;
        this.purchaseShipmentRepository = purchaseShipmentRepository;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        seedDataForK6Testing();
    }

    private void seedDataForK6Testing() {
        // ==========================================
        // 1. 建立品牌 (修正：優先檢查名稱，避免 Unique Constraint 衝突)
        // ==========================================
        Brand brand = brandRepository.findByName("品茶軒").orElseGet(() -> {
            // 如果沒找到名字，再嘗試找 ID (防禦性檢查)
            return brandRepository.findById(1L).map(existing -> {
                // ID 1 存在但名字不同 -> 改名為品茶軒 (確保測試環境一致)
                existing.setName("品茶軒");
                return brandRepository.save(existing);
            }).orElseGet(() -> {
                // 完全不存在 -> 建立新的
                Brand b = new Brand();
                b.setId(1L); // 強制指定 ID 為 1
                b.setName("品茶軒");
                b.setContactPerson("測試員");
                b.setIsActive(true);
                return brandRepository.save(b);
            });
        });
        // ==========================================
        // 2. 建立分店 (修正：增加防呆，避免重複建立)
        // ==========================================
        Store store = storeRepository.findByBrand_IdAndId(brand.getId(), 1L).orElseGet(() -> {
            // 這裡也可以額外檢查是否已有同名分店，但通常分店ID綁定比較固定，暫時維持 ID 檢查
            Store s = new Store();
            s.setId(1L);
            s.setBrand(brand);
            s.setName("台北總店");
            s.setAddress("台北市測試路1號");
            s.setPhoneNumber("02-12345678");
            return storeRepository.save(s);
        });

        // 3. 建立或更新店員帳號
        User staffUser = userRepository.findByPrimaryPhoneAndBrandId("0911111111", brand.getId())
                .orElseGet(() -> {
                    User user = new User();
                    user.setBrand(brand);
                    user.setPrimaryPhone("0911111111");
                    user.setPasswordHash(passwordEncoder.encode("password123"));
                    user.setIsActive(true);

                    StaffProfile profile = new StaffProfile();
                    profile.setFullName("K6 測試員");
                    profile.setEmployeeNumber("K6-001");
                    profile.setRole(StaffRole.MANAGER);
                    profile.setStore(store);
                    user.setStaffProfile(profile);

                    return userRepository.save(user);
                });

        if (!passwordEncoder.matches("password123", staffUser.getPasswordHash())) {
            staffUser.setPasswordHash(passwordEncoder.encode("password123"));
            userRepository.save(staffUser);
            logger.info("DataSeeder: 已更新測試帳號密碼: 0911111111");
        }

        // ==========================================
        // 4. 建立選項群組與選項 (【本次修正重點】)
        // ==========================================

        // --- 4.1 甜度 ---
        OptionGroup sugarGroup = optionGroupRepository.findByBrand_IdAndName(brand.getId(), "甜度")
                .orElseGet(() -> optionGroupRepository.findByBrand_IdAndId(brand.getId(), 10L)
                        .map(existing -> {
                            existing.setName("甜度");
                            return optionGroupRepository.save(existing);
                        })
                        .orElseGet(() -> {
                            OptionGroup g = new OptionGroup();
                            g.setId(10L);
                            g.setBrand(brand);
                            g.setName("甜度");
                            g.setSelectionType(SelectionType.SINGLE);
                            g.setSortOrder(1);
                            return optionGroupRepository.save(g);
                        }));

        // 建立 "半糖" 選項 (檢查邏輯不變，因為 Option 沒有 Unique Name Constraint)
        if (productOptionRepository.findByOptionGroup_Brand_IdAndId(brand.getId(), 11L).isEmpty()) {
            ProductOption opt = new ProductOption();
            opt.setId(11L);
            opt.setOptionGroup(sugarGroup);
            opt.setOptionName("半糖");
            opt.setPriceAdjustment(BigDecimal.ZERO);
            opt.setDefault(false);
            productOptionRepository.save(opt);
        }

        // --- 4.2 冰塊 ---
        OptionGroup iceGroup = optionGroupRepository.findByBrand_IdAndName(brand.getId(), "冰塊")
                .orElseGet(() -> optionGroupRepository.findByBrand_IdAndId(brand.getId(), 20L)
                        .map(existing -> {
                            existing.setName("冰塊");
                            return optionGroupRepository.save(existing);
                        })
                        .orElseGet(() -> {
                            OptionGroup g = new OptionGroup();
                            g.setId(20L);
                            g.setBrand(brand);
                            g.setName("冰塊");
                            g.setSelectionType(SelectionType.SINGLE);
                            g.setSortOrder(2);
                            return optionGroupRepository.save(g);
                        }));

        // 建立 "少冰" 選項
        if (productOptionRepository.findByOptionGroup_Brand_IdAndId(brand.getId(), 21L).isEmpty()) {
            ProductOption opt = new ProductOption();
            opt.setId(21L);
            opt.setOptionGroup(iceGroup);
            opt.setOptionName("少冰");
            opt.setPriceAdjustment(BigDecimal.ZERO);
            opt.setDefault(false);
            productOptionRepository.save(opt);
        }

        // ==========================================
        // 5. 建立商品 (【本次修正重點】)
        // ==========================================
        Product product = productRepository.findByBrand_IdAndName(brand.getId(), "招牌紅茶")
                .orElseGet(() -> productRepository.findByBrand_IdAndId(brand.getId(), 1L)
                        .map(existing -> {
                            existing.setName("招牌紅茶");
                            return productRepository.save(existing);
                        })
                        .orElseGet(() -> {
                            Product p = new Product();
                            p.setId(1L);
                            p.setBrand(brand);
                            p.setName("招牌紅茶");
                            p.setDescription("K6 測試專用商品");
                            p.setBasePrice(new BigDecimal("30.00"));
                            p.setStatus(ProductStatus.ACTIVE);
                            // 注意：關聯會在下面處理
                            return productRepository.save(p);
                        }));

        // 補上關聯
        if (product.getOptionGroups() == null || product.getOptionGroups().isEmpty()) {
            product.setOptionGroups(Set.of(sugarGroup, iceGroup));
            productRepository.save(product);
        }

        // 確保 Variant 存在 (配合 V9 新架構)
        // 您之後可能需要在此處補上 ProductVariant 的 Seeder 邏輯

        // ==========================================
        // 6. 建立庫存資料 (【本次修正重點】)
        // ==========================================

        // 6-1. 原物料
        InventoryItem item = inventoryItemRepository.findByBrand_IdAndName(brand.getId(), "測試用茶葉")
                .orElseGet(() -> inventoryItemRepository.findByBrand_IdAndId(brand.getId(), 1L)
                        .map(existing -> {
                            existing.setName("測試用茶葉");
                            return inventoryItemRepository.save(existing);
                        })
                        .orElseGet(() -> {
                            InventoryItem i = new InventoryItem();
                            i.setId(1L);
                            i.setBrand(brand);
                            i.setName("測試用茶葉");
                            i.setUnit("g");
                            i.setTotalQuantity(new BigDecimal("1000.00"));
                            return inventoryItemRepository.save(i);
                        }));

        // 6-2. 進貨單 (Shipment 通常不檢查名稱，維持原樣)
        PurchaseShipment shipment = purchaseShipmentRepository.findByStore_Brand_IdAndId(brand.getId(), 1L).orElseGet(() -> {
            // ... 維持原樣 ...
            PurchaseShipment s = new PurchaseShipment();
            s.setId(1L);
            s.setStore(store);
            s.setStaff(staffUser);
            s.setShipmentDate(LocalDateTime.now());
            s.setSupplier("K6 供應商");
            return purchaseShipmentRepository.save(s);
        });

        // 6-3. 庫存批次 (Batch 維持原樣，但需注意 V9 新增了 store_id)
        // 如果您的 Batch Entity 已經更新了 V9 的 store 欄位，這裡需要補上 s.setStore(store);
        inventoryBatchRepository.findByShipment_Store_Brand_IdAndId(brand.getId(), 1L).orElseGet(() -> {
            InventoryBatch b = new InventoryBatch();
            b.setId(1L);
            b.setShipment(shipment);
            b.setInventoryItem(item);
            b.setStore(store);
            b.setQuantityReceived(new BigDecimal("1000.00"));
            b.setCurrentQuantity(new BigDecimal("1000.00"));
            b.setExpiryDate(LocalDate.now().plusYears(1));
            return inventoryBatchRepository.save(b);
        });

        logger.info("DataSeeder: 初始化資料完成！");
    }
}