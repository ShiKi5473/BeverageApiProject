package tw.niels.beverage_api_project.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import tw.niels.beverage_api_project.common.util.TsidUtil;

@Configuration
public class NodeIdAllocator {

    private static final Logger logger = LoggerFactory.getLogger(NodeIdAllocator.class);
    private static final String NODE_ID_KEY = "system:node:id-counter";

    private final StringRedisTemplate redisTemplate;

    public NodeIdAllocator(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void allocateNodeId() {
        try {
            // 1. 利用 Redis 原子遞增獲取全域唯一序號
            Long counter = redisTemplate.opsForValue().increment(NODE_ID_KEY);

            if (counter == null) {
                // 理論上不應發生，除非 Redis 掛掉
                logger.warn("無法從 Redis 獲取 Node ID，使用預設值 0。請檢查 Redis 連線。");
                TsidUtil.setNodeId(0);
                return;
            }

            // 2. 計算 Node ID (TSID 限制 Node ID 為 10 bits，即 0-1023)
            int nodeId = (int) (counter % 1024);

            logger.info("成功分配 Node ID: {} (Counter: {})", nodeId, counter);

            // 3. 設定到工具類
            TsidUtil.setNodeId(nodeId);

        } catch (Exception e) {
            logger.error("分配 Node ID 時發生錯誤，將使用預設值 0 (風險：可能產生 ID 衝突)", e);
            // 保持 TsidUtil 預設值 (0)
        }
    }
}