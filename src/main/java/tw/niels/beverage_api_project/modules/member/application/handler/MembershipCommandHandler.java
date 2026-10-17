package tw.niels.beverage_api_project.modules.member.application.handler;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.common.exception.ResourceNotFoundException;
import tw.niels.beverage_api_project.modules.brand.domain.repository.BrandRepository;
import tw.niels.beverage_api_project.modules.member.application.command.*;
import tw.niels.beverage_api_project.modules.member.domain.model.Member;
import tw.niels.beverage_api_project.modules.member.domain.model.MemberProfile;
import tw.niels.beverage_api_project.modules.member.domain.repository.MemberRepository;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * ============================================================
 * Bounded Context: Membership & Rewards
 * Layer: Application - Command Handler
 * ============================================================
 *
 * 會員相關命令處理器（Phase 3 重構完成版）
 *
 * 全部操作透過 {@link MemberRepository}（Domain 介面）操作 Domain Model，
 * 不再委派 UserService 或 MemberPointService，符合 DDD Application Layer 職責。
 *
 * 處理流程（RegisterMember）：
 * 1. 驗證品牌存在（BrandRepository）
 * 2. 驗證手機號碼在品牌內唯一（MemberRepository.findByPhone）
 * 3. 建立 MemberProfile VO（含 DTO → LocalDate / Gender String 型別轉換）
 * 4. 建立 Member Aggregate（積點 0、等級 REGULAR、isActive true）
 * 5. 雜湊密碼（Application Layer 安全職責）
 * 6. 透過 MemberRepository.create() 持久化，回傳新會員 ID
 *
 * 處理流程（AdjustPoints）：
 * 1. 載入 Member Aggregate（同時驗證品牌歸屬）
 * 2. 呼叫 member.adjustPoints()（Domain 方法確保業務規則，記錄 ADMIN_ADJUST 交易）
 * 3. 透過 MemberRepository.save() 儲存（自動寫入積點日誌）
 * ============================================================
 */
@Component
public class MembershipCommandHandler {

    private final BrandRepository brandRepository;
    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    // 營業時區（TimeConfig.businessZoneId）：生日等日期欄位以此時區換算成 LocalDate
    private final ZoneId businessZoneId;

    public MembershipCommandHandler(BrandRepository brandRepository,
                                    MemberRepository memberRepository,
                                    PasswordEncoder passwordEncoder,
                                    ZoneId businessZoneId) {
        this.brandRepository = brandRepository;
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.businessZoneId = businessZoneId;
    }

    /**
     * 註冊新會員。
     *
     * 處理流程：
     * 1. 驗證品牌存在
     * 2. 驗證手機號碼在品牌內唯一
     * 3. 建立 MemberProfile VO（DTO 轉換：birthDate → LocalDate、Gender enum → String）
     * 4. 建立 Member Aggregate
     * 5. 雜湊密碼（PasswordEncoder 屬 Application Layer 安全基礎設施）
     * 6. MemberRepository.create() 建立 UserEntity + MemberProfileEntity 並儲存
     *
     * @param command 包含品牌 ID 與會員建立資料
     * @return 新建會員的 ID
     */
    @Transactional
    public Long handle(RegisterMemberCommand command) {
        Long brandId = command.brandId();
        var req = command.requestDto();

        // 1. 驗證品牌存在
        brandRepository.findById(brandId)
            .orElseThrow(() -> new ResourceNotFoundException("找不到品牌，ID：" + brandId));

        // 2. 驗證手機號碼在品牌內唯一
        if (memberRepository.findByPhone(brandId, req.getPrimaryPhone()).isPresent()) {
            throw new BadRequestException("手機號碼已被使用：" + req.getPrimaryPhone());
        }

        // 3. 建立 MemberProfile VO（從 DTO 轉換型別）
        MemberProfile profile;
        if (req.getMemberProfile() != null) {
            var mp = req.getMemberProfile();

            // java.util.Date → LocalDate（birthDate）
            // 修正(B-7)：不再用 ZoneId.systemDefault()（Docker 映像預設 UTC，會讓 "2000-01-01" 變成前一天），
            // 改用營業時區（與 spring.jackson.time-zone 解析 JSON 日期時採用的時區一致）。
            LocalDate birthday = null;
            if (mp.getBirthDate() != null) {
                birthday = mp.getBirthDate().toInstant()
                    .atZone(businessZoneId).toLocalDate();
            }

            // Gender enum → String（MemberProfile VO 以 String 儲存）
            String gender = mp.getGender() != null ? mp.getGender().name() : null;

            // fullName 若未填寫則 fallback 為電話號碼
            String fullName = (mp.getFullName() != null && !mp.getFullName().isBlank())
                ? mp.getFullName() : req.getPrimaryPhone();

            profile = new MemberProfile(fullName, req.getPrimaryPhone(), mp.getEmail(), birthday, gender);
        } else {
            // 未提供會員資料：以電話號碼作為姓名 fallback
            profile = new MemberProfile(req.getPrimaryPhone(), req.getPrimaryPhone(), null, null, null);
        }

        // 4. 建立 Member Aggregate（積點 0、等級 REGULAR、isActive = true）
        Member member = Member.create(null, brandId, profile);

        // 5. 雜湊密碼（安全基礎設施職責由 Application Layer 承擔）
        String passwordHash = passwordEncoder.encode(req.getPassword());

        // 6. 持久化並回傳新會員 ID
        return memberRepository.create(member, passwordHash);
    }

    /**
     * 人工調整會員積點（管理員操作）。
     *
     * 處理流程：
     * 1. 載入 Member Aggregate（findById 隱含驗證品牌歸屬）
     * 2. 呼叫 member.adjustPoints()（Domain 方法確保餘額非負，並記錄 ADMIN_ADJUST 交易）
     * 3. MemberRepository.save() 更新積點餘額並寫入積點日誌
     *
     * @param command 包含品牌 ID、會員 ID、調整數量與原因
     */
    @Transactional
    public void handle(AdjustPointsCommand command) {
        // 1. 載入會員（findById 使用 brandId 隱含驗證品牌歸屬）
        Member member = memberRepository.findById(command.brandId(), command.memberId())
            .orElseThrow(() -> new ResourceNotFoundException("找不到會員，ID：" + command.memberId()));

        // 2. 透過 Domain 方法調整積點（業務規則：餘額不得為負）
        member.adjustPoints(command.points(), command.reason());

        // 3. 儲存（MemberRepositoryImpl 透過 pullPointTransactions() 自動寫入積點日誌）
        memberRepository.save(member);
    }
}
