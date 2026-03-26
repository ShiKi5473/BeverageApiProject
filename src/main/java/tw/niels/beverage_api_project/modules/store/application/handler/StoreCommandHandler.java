package tw.niels.beverage_api_project.modules.store.application.handler;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tw.niels.beverage_api_project.modules.store.application.command.CreateStoreCommand;
import tw.niels.beverage_api_project.modules.store.entity.StoreEntity;
import tw.niels.beverage_api_project.modules.store.service.StoreService;

/**
 * 分店相關命令處理器
 *
 * 處理流程：
 * 1. 接收 CreateStoreCommand
 * 2. 委派 StoreService 執行建立邏輯（過渡期）
 * 3. 回傳新建分店的 ID
 *
 * 備註：過渡期直接委派 StoreService，待 Phase 3 Infrastructure 分離後
 * 改為操作 Domain Model + Domain Repository。
 */
@Component
public class StoreCommandHandler {

    private final StoreService storeService;

    public StoreCommandHandler(StoreService storeService) {
        this.storeService = storeService;
    }

    /**
     * 處理建立分店命令
     *
     * 處理流程：
     * 1. 設定 brandId 至 DTO
     * 2. 委派 StoreService 建立分店
     * 3. 回傳新建分店 ID
     */
    @Transactional
    public Long handle(CreateStoreCommand command) {
        // 設定 brandId（由 Controller 從 SecurityContext 取得）
        command.requestDto().setBrandId(command.brandId());
        StoreEntity store = storeService.createStore(command.requestDto());
        return store.getId();
    }
}
