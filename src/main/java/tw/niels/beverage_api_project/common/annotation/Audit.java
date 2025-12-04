package tw.niels.beverage_api_project.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 標記在 Controller 或 Service 方法上，用於自動記錄審計日誌。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Audit {
    String action(); // 操作名稱，例如 "UPDATE_STAFF"
}