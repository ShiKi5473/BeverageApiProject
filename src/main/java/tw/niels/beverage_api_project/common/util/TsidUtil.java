package tw.niels.beverage_api_project.common.util;

import com.github.f4b6a3.tsid.TsidFactory;

/**
 * TSID (Time-Sorted Unique Identifier) 生成工具類別。
 * <p>
 * 用於生成 64-bit 的 Long 型態唯一識別碼，具備時間排序特性。
 * 適合用於資料庫主鍵，能減少索引分裂 (Page Splitting) 並提升寫入效能。
 * </p>
 */
public class TsidUtil {

    private static final TsidFactory FACTORY;

    static {
        // 從環境變數讀取 Node ID (範圍 0~1023)，預設為 0
        // 在分散式部署時，每個實例應配置不同的 APP_NODE_ID 以避免 ID 衝突
        String nodeIdEnv = System.getenv("APP_NODE_ID");
        int nodeId = 0;
        if (nodeIdEnv != null && !nodeIdEnv.isBlank()) {
            try {
                nodeId = Integer.parseInt(nodeIdEnv);
            } catch (NumberFormatException e) {
                // 忽略錯誤，維持預設值 0，但在 Log 中應有提示
                System.err.println("Invalid APP_NODE_ID: " + nodeIdEnv + ", using default 0.");
            }
        }

        // 建立 TSID Factory，配置 Node ID
        FACTORY = TsidFactory.builder()
                .withNode(nodeId)
                .build();
    }

    /**
     * 生成一個新的 TSID (Long 型態)
     * @return 64-bit unique identifier
     */
    public static long nextId() {
        return FACTORY.create().toLong();
    }

    /**
     * 生成一個新的 TSID (String 型態，Base62 編碼)
     * 用於需要字串顯示的場景 (如訂單編號、公開的 URL 參數)
     * @return Base62 encoded string
     */
    public static String nextIdString() {
        return FACTORY.create().toString(); // Base62 string
    }
}