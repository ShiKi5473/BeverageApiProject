package tw.niels.beverage_api_project.modules.kds.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * SSE 連線生命週期管理器。
 * 負責 Emitter 的新增、移除、廣播與心跳，與 KDS 業務邏輯分離。
 * <p>
 * 【修正】
 * <ul>
 *   <li>廣播時對每個 emitter 個別捕捉 {@code IOException | IllegalStateException} 並移除：
 *       原本只捕捉 IOException，已完成的 emitter 拋出 IllegalStateException 會中斷對其他螢幕的推播</li>
 *   <li>每 25 秒送出 SSE 註解（heartbeat），避免反向代理／瀏覽器因閒置關閉連線，也能及早清掉已斷線的 emitter</li>
 *   <li>逾時時主動 complete()，避免 AsyncRequestTimeoutException 進入例外處理器</li>
 * </ul>
 * </p>
 */
@Component
public class SseEmitterManager {

    private static final Logger logger = LoggerFactory.getLogger(SseEmitterManager.class);

    /** 心跳間隔（毫秒） */
    private static final long HEARTBEAT_INTERVAL_MS = 25_000L;

    // Map<StoreId, List<Emitter>>，使用 ConcurrentHashMap 確保並發存取安全
    private final Map<Long, List<SseEmitter>> storeEmitters = new ConcurrentHashMap<>();

    /**
     * 新增 SSE 連線，並自動註冊 completion/timeout/error 清理 callback。
     *
     * @param storeId 店家 ID（不可為 null）
     * @param emitter 要管理的 SseEmitter 實例
     */
    public void addEmitter(Long storeId, SseEmitter emitter) {
        if (storeId == null) {
            // ConcurrentHashMap 不接受 null key；未綁定分店的帳號應在 Controller 就被拒絕
            throw new IllegalArgumentException("storeId 不可為 null");
        }
        // 【修正】改用 compute（同一個 key 原子執行），避免與 removeEmitter「清掉空列表」交錯時，
        // 新連線被加進一個剛從 Map 移除的列表而永遠收不到推播
        storeEmitters.compute(storeId, (id, list) -> {
            List<SseEmitter> emitters = (list != null) ? list : new CopyOnWriteArrayList<>();
            emitters.add(emitter);
            return emitters;
        });
        logger.debug("店家 {} 新增 SSE 連線。目前連線數: {}", storeId, getEmitters(storeId).size());

        // 連線結束時自動移除，避免 Map 中累積失效的 emitter
        emitter.onCompletion(() -> removeEmitter(storeId, emitter));
        emitter.onTimeout(() -> {
            // 逾時時主動結束，前端 EventSource 會自動重連
            removeEmitter(storeId, emitter);
            emitter.complete();
        });
        emitter.onError((e) -> removeEmitter(storeId, emitter));
    }

    /**
     * 移除指定店家的 SSE 連線。
     *
     * @param storeId 店家 ID
     * @param emitter 要移除的 SseEmitter 實例
     */
    public void removeEmitter(Long storeId, SseEmitter emitter) {
        if (storeId == null) {
            return;
        }
        // 【修正】以 computeIfPresent 原子地移除並清掉空列表（回傳 null 即移除該 key），
        // 避免 Map 持續累積已無連線的店家，也不會與 addEmitter 互相覆蓋
        storeEmitters.computeIfPresent(storeId, (id, emitters) -> {
            emitters.remove(emitter);
            return emitters.isEmpty() ? null : emitters;
        });
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

    /**
     * 【新增】對指定店家的所有連線推送一筆 data 事件。
     * 單一 emitter 失敗（已斷線／已完成）只會移除該 emitter，不影響其他螢幕。
     *
     * @param storeId 店家 ID
     * @param data    事件內容（以 JSON 序列化）
     */
    public void broadcast(Long storeId, Object data) {
        if (storeId == null) {
            return;
        }
        for (SseEmitter emitter : getEmitters(storeId)) {
            try {
                emitter.send(SseEmitter.event().data(data));
            } catch (IOException | IllegalStateException e) {
                // IOException：用戶端已斷線；IllegalStateException：emitter 已 complete
                logger.debug("店家 {} 的 SSE 連線已失效，移除: {}", storeId, e.getMessage());
                removeEmitter(storeId, emitter);
            }
        }
    }

    /**
     * 【新增】每 25 秒對所有連線送出 SSE 註解行（": heartbeat"），EventSource 會忽略註解，不影響前端邏輯。
     * 各伺服器實例只處理自己持有的連線，因此不需要 ShedLock。
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void sendHeartbeat() {
        storeEmitters.forEach((storeId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException | IllegalStateException e) {
                    removeEmitter(storeId, emitter);
                }
            }
        });
    }
}
