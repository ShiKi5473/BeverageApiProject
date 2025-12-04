package tw.niels.beverage_api_project.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 標示 API 需要進行冪等性 (Idempotency) 檢查。
 * 前端請求必須包含 Idempotency-Key Header。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Idempotent {
    /**
     * Key 的過期時間 (秒)，預設 5 分鐘。
     * 在這段時間內，相同的 Key 會被視為重複請求。
     */
    long expire() default 300;
}