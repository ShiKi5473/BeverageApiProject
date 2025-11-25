package tw.niels.beverage_api_project.common.util;

import com.github.f4b6a3.tsid.TsidFactory;

public class TsidUtil {

    private static final TsidFactory FACTORY;

    static {
        // 從環境變數讀取 Node ID (0~1023)，預設為 0
        String nodeIdEnv = System.getenv("APP_NODE_ID");
        int nodeId = 0;
        if (nodeIdEnv != null && !nodeIdEnv.isBlank()) {
            try {
                nodeId = Integer.parseInt(nodeIdEnv);
            } catch (NumberFormatException e) {
                // 忽略錯誤，維持預設值 0，但在 Log 中應有提示 (此處簡化)
                System.err.println("Invalid APP_NODE_ID: " + nodeIdEnv + ", using default 0.");
            }
        }

        // 建立 TSID Factory，配置 Node ID 避免分散式環境衝突
        FACTORY = TsidFactory.builder()
                .withNode(nodeId)
                .build();
    }

    /**
     * 生成一個新的 TSID (Long 型態)
     */
    public static long nextId() {
        return FACTORY.create().toLong();
    }

    /**
     * 生成一個新的 TSID (String 型態，Base62 編碼)
     * 用於訂單編號等需要字串顯示的場景
     */
    public static String nextIdString() {
        return FACTORY.create().toString(); // Base62 string
    }
}