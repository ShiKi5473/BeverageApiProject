package tw.niels.beverage_api_project.modules.order.service;

import org.springframework.stereotype.Service;

import tw.niels.beverage_api_project.common.exception.BadRequestException;
import tw.niels.beverage_api_project.modules.order.entity.PaymentMethodEntity;
import tw.niels.beverage_api_project.modules.order.repository.PaymentMethodJpaRepository;

/**
 * 支付方式服務。
 * 封裝支付方式的查詢邏輯，供結帳 Handler（PosCheckoutHandler、ProcessPaymentHandler）使用，
 * 避免 Handler 直接依賴 Repository。
 *
 * 修改：欄位 / 參數名稱由批次改名殘留的 "PaymentMethodJpaRepository"（與型別同名）改為 paymentMethodJpaRepository；
 * 移除已刪除的 Facade 相關說明。
 */
@Service
public class PaymentMethodService {

    private final PaymentMethodJpaRepository paymentMethodJpaRepository;

    public PaymentMethodService(PaymentMethodJpaRepository paymentMethodJpaRepository) {
        this.paymentMethodJpaRepository = paymentMethodJpaRepository;
    }

    /**
     * 根據 code 取得支付方式，若不存在則拋出業務例外。
     *
     * @param code 支付方式代碼
     * @return 對應的 PaymentMethodEntity
     * @throws BadRequestException 若代碼無對應的支付方式
     */
    public PaymentMethodEntity getByCode(String code) {
        return paymentMethodJpaRepository.findByCode(code)
                .orElseThrow(() -> new BadRequestException("無效的支付方式代碼：" + code));
    }
}
