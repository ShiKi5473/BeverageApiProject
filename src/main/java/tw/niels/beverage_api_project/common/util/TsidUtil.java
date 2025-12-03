package tw.niels.beverage_api_project.common.util;

import com.github.f4b6a3.tsid.TsidFactory;

/**
 * TSID (Time-Sorted Unique Identifier) 生成工具類別。
 * <p>
 * 1. 預設 Node ID 為 0 (避免未初始化時報錯)。
 * 2. 支援透過 setNodeId() 動態切換 Node ID (由 NodeIdAllocator 在啟動時呼叫)。
 * </p>
 */
public class TsidUtil {

    // 使用 volatile 確保多執行緒下的可見性，雖然通常只在啟動時設定一次
    private static volatile TsidFactory FACTORY = TsidFactory.builder()
            .withNode(0) // 預設值，防止 Spring Context 初始化前的調用導致 NPE
            .build();

    /**
     * 設定當前應用實例的 Node ID (0 ~ 1023)
     * 應在應用程式啟動時儘早呼叫
     */
    public static void setNodeId(int nodeId) {
        if (nodeId < 0 || nodeId > 1023) {
            throw new IllegalArgumentException("Node ID must be between 0 and 1023");
        }
        // 重新建立 Factory
        FACTORY = TsidFactory.builder()
                .withNode(nodeId)
                .build();

        System.out.println("[TsidUtil] TSID Factory initialized with Node ID: " + nodeId);
    }

    public static long nextId() {
        return FACTORY.create().toLong();
    }

    public static String nextIdString() {
        return FACTORY.create().toString();
    }
}