package tw.niels.beverage_api_project.modules.order.domain.model;

/**
 * ============================================================
 * Bounded Context: Order
 * Layer: Domain - Value Object
 * ============================================================
 *
 * 會員快照值物件（Value Object）
 *
 * 在訂單建立時，將會員的關鍵資訊（姓名、電話、Email）複製一份快照
 * 儲存於訂單中，即使日後會員資料異動，歷史訂單仍保有當時的正確資訊。
 *
 * 為什麼需要快照？
 * 若訂單直接關聯 Member，當 Member 更新個人資料後，
 * 查詢歷史訂單時會顯示「更新後」的資料，不符合歷史資料一致性需求。
 *
 * 不可變（Immutable）：使用 Java record，所有欄位為 final，無 setter。
 *
 * 注意：此類別為 Domain Layer 版本，與 `modules/order/vo/MemberSnapshot.java`
 * 共存，待 Phase 3 完成後再整併。
 * ============================================================
 */
public record MemberSnapshot(
        /** 會員的使用者 ID（TSID） */
        Long userId,

        /** 會員於下單當下的顯示名稱 */
        String fullName,

        /** 會員於下單當下的聯絡電話 */
        String phone,

        /** 會員於下單當下的電子郵件 */
        String email
) {}
