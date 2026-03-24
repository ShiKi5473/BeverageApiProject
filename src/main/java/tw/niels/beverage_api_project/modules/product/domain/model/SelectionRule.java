package tw.niels.beverage_api_project.modules.product.domain.model;

/**
 * ============================================================
 * Bounded Context: Product Catalog
 * Layer: Domain - Value Object
 * ============================================================
 *
 * 選項選取規則值物件
 *
 * 封裝選項群組的選取限制，例如「至少選 1 項」、「最多選 3 項」。
 *
 * 不可變（Immutable）：使用 Java record。
 * ============================================================
 */
public record SelectionRule(
        /** 最小選取數量（0 代表可不選，即「選配」） */
        int minSelection,

        /** 最大選取數量 */
        int maxSelection
) {
    public SelectionRule {
        if (minSelection < 0) {
            throw new IllegalArgumentException("最小選取數量不得為負數");
        }
        if (maxSelection < minSelection) {
            throw new IllegalArgumentException("最大選取數量不得小於最小選取數量");
        }
    }

    /**
     * 預設規則：必選一項（1, 1）。
     */
    public static SelectionRule requiredOne() {
        return new SelectionRule(1, 1);
    }

    /**
     * 預設規則：可選配多項（0, MAX_VALUE）。
     */
    public static SelectionRule optionalMany() {
        return new SelectionRule(0, Integer.MAX_VALUE);
    }
}
