package tw.niels.beverage_api_project.modules.brand.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import tw.niels.beverage_api_project.modules.brand.dto.CreateBrandRequestDto;
import tw.niels.beverage_api_project.modules.brand.entity.Brand;
import tw.niels.beverage_api_project.modules.brand.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.store.entity.Store;
import tw.niels.beverage_api_project.modules.store.repository.StoreRepository;
import tw.niels.beverage_api_project.modules.user.entity.Staff;
import tw.niels.beverage_api_project.modules.user.enums.StaffRole;
import tw.niels.beverage_api_project.modules.user.repository.StaffRepository;


@Service
public class BrandService {
    private final BrandRepository brandRepository;
    private final StoreRepository storeRepository;
    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;

    public BrandService (BrandRepository brandRepository,
                        StoreRepository storeRepository,
                        StaffRepository staffRepository,
                        PasswordEncoder passwordEncoder){
        this.brandRepository = brandRepository;
        this.storeRepository = storeRepository;
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Brand createBrandAndAdmin(CreateBrandRequestDto requestDto){
        Brand newBrand = new Brand();
        newBrand.setName(requestDto.getBrandName());
        brandRepository.save(newBrand);

        Store headquarters = new Store();
        headquarters.setBrand(newBrand);
        headquarters.setName(requestDto.getBrandName() + " - 總店");
        storeRepository.save(headquarters);

        CreateBrandRequestDto.BrandAdminDto adminDto = requestDto.getAdmin();
        Staff newStaff = new Staff();
        newStaff.setBrand(newBrand);
        newStaff.setStore(null);
        newStaff.setUsername(adminDto.getUsername());
        newStaff.setPasswordHash(passwordEncoder.encode(adminDto.getPassword()));
        newStaff.setFullName(adminDto.getFullName());
        newStaff.setRole(StaffRole.BRAND_ADMIN);
        newStaff.setActive(true);
        staffRepository.save(newStaff);

        return newBrand;
    }
}
