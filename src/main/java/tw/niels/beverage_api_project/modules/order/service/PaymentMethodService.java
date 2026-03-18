package tw.niels.beverage_api_project.modules.order.service;

import org.springframework.stereotype.Service;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.repository.PaymentMethodRepository;

/**
 * 支付方式服務。
 * 封裝支付方式的查詢邏輯，供 Facade 層使用，避免 Facade 直接依賴 Repository。
 */
@Service
public class PaymentMethodService {

    private final PaymentMethodRepository paymentMethodRepository;

    public PaymentMethodService(PaymentMethodRepository paymentMethodRepository) {
        this.paymentMethodRepository = paymentMethodRepository;
    }

    /**
     * 根據 code 取得支付方式，若不存在則拋出業務例外。
     *
     * @param code 支付方式代碼
     * @return 對應的 PaymentMethodEntity
     * @throws BadRequestException 若代碼無對應的支付方式
     */
    public PaymentMethodEntity getByCode(String code) {
        return paymentMethodRepository.findByCode(code)
                .orElseThrow(() -> new BadRequestException("無效的支付方式代碼：" + code));
    }
}
