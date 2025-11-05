package tw.niels.beverage_api_project.modules.order.service;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
public class OrderNumberService {

    private final StringRedisTemplate redisTemplate;
    // 定義日期格式，用於產生 Redis Key
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");

    public OrderNumberService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 獲取指定店家當天的下一個訂單序號
     * * @param storeId 店家 ID
     * @return 當天的訂單流水號 (例如 1, 2, 3...)
     */
    public long getNextStoreDailySequence(Long storeId) {
        // 1. 產生 Redis Key，格式為：order_counter:store_id:{storeId}:date:{yyyyMMdd}
        // 例如：order_counter:store_id:1:date:20251105
        String dateString = LocalDate.now().format(dateFormatter);
        String redisKey = String.format("order_counter:store_id:%d:date:%s", storeId, dateString);

        // 2. 使用 INCR 指令。
        // 如果 Key 不存在，Redis 會自動建立它並設為 1
        // 如果 Key 已存在，Redis 會將其值 +1
        Long sequence = redisTemplate.opsForValue().increment(redisKey);

        // 3. (重要) 為這個 Key 設定過期時間 (TTL)，例如 2 天 (48 小時)
        // 這樣可以自動清除舊的計數器，節省 Upstash 記憶體
        // 只有在 sequence 為 1 (即 Key 剛被建立) 時才需要設定
        if (sequence != null && sequence == 1) {
            // 設定 48 小時後過期
            redisTemplate.expire(redisKey, 48, TimeUnit.HOURS);
        }

        return sequence != null ? sequence : -1; // -1 表示出錯
    }}
