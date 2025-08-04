package tw.niels.beverage_api_project.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import tw.niels.beverage_api_project.modules.user.entity.Staff;
import tw.niels.beverage_api_project.modules.user.repository.StaffRepository;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final StaffRepository staffRepository;

    public CustomUserDetailsService(StaffRepository staffRepository) {
        this.staffRepository = staffRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameAndBrandId) throws UsernameNotFoundException {
        String[] parts = usernameAndBrandId.split(":");
        if (parts.length != 2) {
            throw new UsernameNotFoundException("傳入的使用者名稱格式不正確，應為 'username:brandId'");
        }

        String username = parts[0];
        Long brandId;
        try {
            brandId = Long.parseLong(parts[1]);
        } catch (NumberFormatException e) {
            throw new UsernameNotFoundException("傳入的 brandId 格式不正確");
        }

        Staff staff = staffRepository.findByBrand_BrandIdAndUsername(brandId, username)
            .orElseThrow(() -> new UsernameNotFoundException("在品牌 " + brandId + " 中找不到使用者: " + username));

        return new StaffUserDetails(staff);
    }

}
