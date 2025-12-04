package tw.niels.beverage_api_project.common.aspect;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tw.niels.beverage_api_project.common.annotation.Idempotent;
import tw.niels.beverage_api_project.common.exception.BadRequestException;

import java.time.Duration;

@Aspect
@Component
public class IdempotencyAspect {

    private final StringRedisTemplate redisTemplate;
    private static final String HEADER_KEY = "Idempotency-Key";
    private static final String REDIS_KEY_PREFIX = "idemp:";

    public IdempotencyAspect(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Around("@annotation(idempotent)")
    public Object checkIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 1. 獲取 HTTP 請求
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();

        // 2. 讀取 Header
        String idempotencyKey = request.getHeader(HEADER_KEY);

        // 如果前端沒有傳 Key，視為不啟用保護 (或者也可以強制拋出錯誤)
        if (!StringUtils.hasText(idempotencyKey)) {
            // throw new BadRequestException("缺少 Idempotency-Key Header");
            return joinPoint.proceed();
        }

        // 3. 組合 Redis Key (加上前綴避免衝突)
        String redisKey = REDIS_KEY_PREFIX + idempotencyKey;

        // 4. 原子操作：檢查並設定 (SET NX)
        // 如果 Key 不存在，寫入 "PROCESSING" 並回傳 true (取得鎖)
        // 如果 Key 已存在，回傳 false (鎖定中或已完成)
        Boolean isSuccess = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "PROCESSING", Duration.ofSeconds(idempotent.expire()));

        if (Boolean.FALSE.equals(isSuccess)) {
            throw new BadRequestException("重複的請求 (Idempotency Key Conflict)");
        }

        try {
            // 5. 執行目標方法
            return joinPoint.proceed();
        } catch (Throwable ex) {
            // 如果執行失敗 (例如驗證錯誤)，刪除 Key，讓使用者可以重試
            // (視業務需求而定，有些嚴格模式下失敗也不允許重試)
            redisTemplate.delete(redisKey);
            throw ex;
        }
        // 成功執行後，Key 會保留直到過期，防止重複提交
    }
}