package tw.niels.beverage_api_project.modules.member.domain.model;

import java.time.LocalDate;

/**
 * ============================================================
 * Bounded Context: Membership & Rewards
 * Layer: Domain - Value Object
 * ============================================================
 *
 * 會員個人資料值物件（Value Object）
 *
 * 封裝會員的個人基本資料（姓名、電話、Email、生日、性別）。
 * 以 Value Object 形式存在，確保這組資料的一致性，
 * 並使 Member Aggregate 本身不需要直接管理多個散落的欄位。
 *
 * 業務規則：
 * - fullName 不得為空
 *
 * 不可變（Immutable）：所有欄位為 final，無 setter。
 * 若需修改，請使用 Member.updateProfile() 方法產生新的 MemberProfile 實例。
 * ============================================================
 *
 * @param fullName 會員姓名（必填）
 * @param phone    聯絡電話（可選）
 * @param email    電子信箱（可選）
 * @param birthday 生日（可選，用於生日優惠計算）
 * @param gender   性別（"M" / "F" / "OTHER"；可選）
 */
public record MemberProfile(
        String fullName,
        String phone,
        String email,
        LocalDate birthday,
        String gender
) {

    /**
     * 緊湊建構子：驗證必填欄位。
     *
     * @throws IllegalArgumentException 若 fullName 為空
     */
    public MemberProfile {
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("會員姓名不得為空");
        }
    }

    /**
     * 更新姓名與 Email，回傳新的 MemberProfile 實例（不可變設計）。
     *
     * @param newName  新姓名
     * @param newEmail 新 Email（可為 null）
     * @return 更新後的新 MemberProfile
     */
    public MemberProfile withNameAndEmail(String newName, String newEmail) {
        return new MemberProfile(newName, this.phone, newEmail, this.birthday, this.gender);
    }
}
