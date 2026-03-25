package tw.niels.beverage_api_project.modules.store.application.command;

import tw.niels.beverage_api_project.modules.store.dto.CreateStoreRequestDto;

/**
 * 建立分店命令
 *
 * 由 StoreController 建構，傳遞至 StoreCommandHandler 處理。
 * 過渡期使用既有 CreateStoreRequestDto 作為承載資料。
 */
public record CreateStoreCommand(
    Long brandId,
    CreateStoreRequestDto requestDto
) {}
