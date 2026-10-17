package tw.niels.beverage_api_project.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 標示 API 需要進行冪等性 (Idempotency) 檢查。
 * <p>
 * 前端以 {@code Idempotency-Key} Header 帶入冪等鍵（同一次送出產生一次、重試沿用）。
 * 【修改】由 IdempotencyAspect 依「呼叫者（principalKey）+ API 方法 + 冪等鍵」判斷：
 * <ul>
 *   <li>第一次請求成功（2xx）：保存回應，期限內相同請求直接重放第一次的結果</li>
 *   <li>第一次請求仍在處理中：回 409 Conflict</li>
 *   <li>第一次請求失敗（例外或非 2xx）：刪除紀錄，允許以同一個 key 重試</li>
 * </ul>
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /**
     * Key 的過期時間 (秒)，預設 5 分鐘。
     * 在這段時間內，相同的 Key 會被視為重複請求（重放第一次的結果）。
     */
    long expire() default 300;

    /**
     * 【新增】是否強制要求 Idempotency-Key Header。
     * 預設 false：未帶 Header 時不做冪等保護（相容舊前端與既有測試）；設為 true 時缺少 Header 回 400。
     */
    boolean required() default false;
}
