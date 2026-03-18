package tw.niels.beverage_api_project.modules.kds.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 連線生命週期管理器。
 * 負責 Emitter 的新增、移除與查詢，與 KDS 業務邏輯分離。
 */
@Component
public class SseEmitterManager {

    private static final Logger logger = LoggerFactory.getLogger(SseEmitterManager.class);

    // Map<StoreId, List<Emitter>>，使用 ConcurrentHashMap 確保並發存取安全
    private final Map<Long, List<SseEmitter>> storeEmitters = new ConcurrentHashMap<>();

    /**
     * 新增 SSE 連線，並自動註冊 completion/timeout/error 清理 callback。
     *
     * @param storeId 店家 ID
     * @param emitter 要管理的 SseEmitter 實例
     */
    public void addEmitter(Long storeId, SseEmitter emitter) {
        storeEmitters.computeIfAbsent(storeId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        logger.debug("店家 {} 新增 SSE 連線。目前連線數: {}", storeId, storeEmitters.get(storeId).size());

        // 連線結束時自動移除，避免 Map 中累積失效的 emitter
        emitter.onCompletion(() -> removeEmitter(storeId, emitter));
        emitter.onTimeout(() -> removeEmitter(storeId, emitter));
        emitter.onError((e) -> removeEmitter(storeId, emitter));
    }

    /**
     * 移除指定店家的 SSE 連線。
     *
     * @param storeId 店家 ID
     * @param emitter 要移除的 SseEmitter 實例
     */
    public void removeEmitter(Long storeId, SseEmitter emitter) {
        List<SseEmitter> emitters = storeEmitters.get(storeId);
        if (emitters != null) {
            emitters.remove(emitter);
        }
    }

    /**
     * 取得指定店家的所有 SSE 連線列表（唯讀），供廣播使用。
     *
     * @param storeId 店家 ID
     * @return 該店家的 emitter 列表，若無連線則回傳空列表
     */
    public List<SseEmitter> getEmitters(Long storeId) {
        return storeEmitters.getOrDefault(storeId, Collections.emptyList());
    }
}
