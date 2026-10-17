package tw.niels.beverage_api_project.common.aspect;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;
import tw.niels.beverage_api_project.common.annotation.Idempotent;
import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.security.TenantPrincipal;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * API 冪等性切面（搭配 {@link Idempotent}）。
 * <p>
 * 【B-9 修正】原本：
 * <ul>
 *   <li>Redis key 只用前端傳來的 Idempotency-Key，不同使用者若送出相同的 key 會互相干擾</li>
 *   <li>重複請求一律回 400，前端網路逾時後重試會被當成錯誤，拿不到第一次建立的訂單</li>
 * </ul>
 * 現在：
 * <ul>
 *   <li>key 以「呼叫者 principalKey（員工／會員 "U:"+userId、訪客 "GUEST:uuid"）+ API 方法 + 冪等鍵」組成，訪客同樣適用</li>
 *   <li>第一次成功（2xx）後保存「狀態碼 + 標頭 + JSON 本文」，期限內的重複請求直接重放第一次的回應（不再執行業務邏輯）</li>
 *   <li>第一次仍在處理中 → 409 Conflict；第一次失敗（例外或非 2xx）→ 刪除紀錄，允許以同一個 key 重試</li>
 * </ul>
 * 重放時直接寫入 HttpServletResponse 並回傳 null（Spring MVC 視為已處理），
 * 不需把 JSON 反序列化回各 Controller 的回傳型別，避免泛型（如 Map、List）在訊息轉換時型別不符。
 * </p>
 */
@Aspect
@Component
public class IdempotencyAspect {

    private static final Logger logger = LoggerFactory.getLogger(IdempotencyAspect.class);

    private static final String HEADER_KEY = "Idempotency-Key";
    /** 重放時附加的回應標頭，方便前端／除錯辨識 */
    private static final String REPLAYED_HEADER = "Idempotent-Replayed";
    private static final String REDIS_KEY_PREFIX = "idemp:";

    /** 處理中的標記值 */
    private static final String PROCESSING = "PROCESSING";
    /** 已成功但回應無法保存（序列化失敗）時的標記值：重複請求回 409，不會再次執行 */
    private static final String COMPLETED_NO_REPLAY = "COMPLETED";

    /** 處理中標記的最長存活時間：伺服器在處理中當機時，最多鎖住這麼久即可重試 */
    private static final long PROCESSING_TTL_SECONDS = 60;

    /** 冪等鍵格式：UUID 或英數、-、_、.、:，最長 128 字元（避免任意字串進入 Redis key） */
    private static final Pattern KEY_PATTERN = Pattern.compile("^[A-Za-z0-9._:\\-]{1,128}$");

    /** 重放時不複製的標頭（由容器自行計算或重新設定） */
    private static final Set<String> SKIPPED_HEADERS = Set.of(
            HttpHeaders.CONTENT_LENGTH.toLowerCase(),
            HttpHeaders.CONTENT_TYPE.toLowerCase(),
            HttpHeaders.TRANSFER_ENCODING.toLowerCase());

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public IdempotencyAspect(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Redis 中保存的第一次成功回應。
     *
     * @param status  HTTP 狀態碼
     * @param headers 回應標頭（不含 Content-Type / Content-Length）
     * @param body    以應用程式 ObjectMapper 序列化後的 JSON 字串（null 表示沒有本文）
     */
    public record StoredResponse(int status, Map<String, List<String>> headers, String body) {
    }

    @Around("@annotation(idempotent)")
    public Object checkIdempotency(ProceedingJoinPoint joinPoint, Idempotent idempotent) throws Throwable {
        // 1. 獲取 HTTP 請求（非 HTTP 呼叫，例如測試直接呼叫方法時，不做冪等保護）
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return joinPoint.proceed();
        }
        HttpServletRequest request = attributes.getRequest();

        // 2. 讀取並驗證 Header
        String idempotencyKey = request.getHeader(HEADER_KEY);
        if (!StringUtils.hasText(idempotencyKey)) {
            if (idempotent.required()) {
                throw new BadRequestException("缺少 Idempotency-Key 標頭");
            }
            // 未帶 Key 視為不啟用保護（相容舊前端與測試）
            return joinPoint.proceed();
        }
        idempotencyKey = idempotencyKey.trim();
        if (!KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new BadRequestException("Idempotency-Key 格式不正確");
        }

        // 3. 組合 Redis Key：呼叫者 + API 方法 + 冪等鍵（不同使用者／不同 API 使用相同的 key 互不影響）
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String redisKey = REDIS_KEY_PREFIX + currentPrincipalKey(request) + ":"
                + method.getDeclaringClass().getSimpleName() + "." + method.getName() + ":" + idempotencyKey;

        // 4. 原子操作 SET NX：取得「處理中」鎖
        Duration processingTtl = Duration.ofSeconds(Math.max(1, Math.min(idempotent.expire(), PROCESSING_TTL_SECONDS)));
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(redisKey, PROCESSING, processingTtl);
        if (!Boolean.TRUE.equals(acquired)) {
            return handleDuplicate(redisKey, attributes.getResponse());
        }

        // 5. 執行目標方法
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (Throwable ex) {
            // 執行失敗（例如驗證錯誤、點數不足）→ 刪除 Key，讓使用者可以修正後以同一個 key 重試
            redisTemplate.delete(redisKey);
            throw ex;
        }

        // 6. 保存成功的回應，供期限內的重複請求重放
        saveResult(redisKey, result, Duration.ofSeconds(Math.max(1, idempotent.expire())));
        return result;
    }

    /**
     * 重複請求：處理中 → 409；已完成 → 重放第一次的回應。
     */
    private Object handleDuplicate(String redisKey, HttpServletResponse response) throws Exception {
        String stored = redisTemplate.opsForValue().get(redisKey);
        if (stored == null || PROCESSING.equals(stored)) {
            // stored == null：前一個請求剛好失敗刪除或過期，請前端稍後以同一個 key 重試
            throw new ResponseStatusException(HttpStatus.CONFLICT, "相同的請求正在處理中，請稍後再試");
        }
        if (COMPLETED_NO_REPLAY.equals(stored) || response == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "相同的請求已處理完成，請勿重複送出");
        }

        StoredResponse saved;
        try {
            saved = objectMapper.readValue(stored, StoredResponse.class);
        } catch (JsonProcessingException e) {
            logger.warn("冪等紀錄內容無法解析，視為已處理: {}", e.getOriginalMessage());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "相同的請求已處理完成，請勿重複送出");
        }

        logger.debug("重放冪等請求的第一次回應 (status={})", saved.status());
        writeStoredResponse(response, saved);
        // 回傳 null：ResponseEntity／@ResponseBody 的回傳值處理器遇到 null 不會再寫入本文（回應已由上方寫出）
        return null;
    }

    /**
     * 直接把保存的回應寫入 HttpServletResponse。
     */
    private void writeStoredResponse(HttpServletResponse response, StoredResponse saved) throws Exception {
        response.setStatus(saved.status());
        if (saved.headers() != null) {
            saved.headers().forEach((name, values) -> {
                if (name == null || values == null || SKIPPED_HEADERS.contains(name.toLowerCase())) {
                    return;
                }
                for (String value : values) {
                    response.addHeader(name, value);
                }
            });
        }
        response.setHeader(REPLAYED_HEADER, "true");
        if (saved.body() != null) {
            byte[] bytes = saved.body().getBytes(StandardCharsets.UTF_8);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
        }
        response.flushBuffer();
    }

    /**
     * 保存成功的回應；非 2xx 的 ResponseEntity 視為失敗，刪除紀錄允許重試。
     */
    private void saveResult(String redisKey, Object result, Duration ttl) {
        try {
            StoredResponse stored;
            if (result instanceof ResponseEntity<?> entity) {
                if (!entity.getStatusCode().is2xxSuccessful()) {
                    redisTemplate.delete(redisKey);
                    return;
                }
                Map<String, List<String>> headers = new LinkedHashMap<>();
                entity.getHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));
                String body = entity.getBody() != null ? objectMapper.writeValueAsString(entity.getBody()) : null;
                stored = new StoredResponse(entity.getStatusCode().value(), headers, body);
            } else {
                // @ResponseBody 直接回傳 DTO（或 void → null）時，狀態碼視為 200
                String body = result != null ? objectMapper.writeValueAsString(result) : null;
                stored = new StoredResponse(HttpStatus.OK.value(), Map.of(), body);
            }
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(stored), ttl);
        } catch (JsonProcessingException e) {
            // 回應無法序列化：仍保留「已完成」標記，避免重複請求再次執行業務邏輯（例如重複建單）
            logger.warn("冪等回應無法序列化，重複請求將回 409: {}", e.getOriginalMessage());
            redisTemplate.opsForValue().set(redisKey, COMPLETED_NO_REPLAY, ttl);
        }
    }

    /**
     * 取得呼叫者識別：員工／會員為 "U:"+userId、訪客為 "GUEST:uuid"（TenantPrincipal#principalKey），
     * 其他已認證身分（平台管理員）以 "N:"+名稱，未認證則以來源 IP 區分。
     */
    private static String currentPrincipalKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            if (authentication.getPrincipal() instanceof TenantPrincipal tenant) {
                return tenant.principalKey();
            }
            return "N:" + authentication.getName();
        }
        return "ANON:" + request.getRemoteAddr();
    }
}
