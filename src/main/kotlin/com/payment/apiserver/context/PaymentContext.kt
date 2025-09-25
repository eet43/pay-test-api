package com.payment.apiserver.context

import com.payment.apiserver.dto.*
import com.payment.apiserver.exception.PaymentException
import com.payment.apiserver.service.PgProviderService
import com.payment.apiserver.strategy.*
import org.springframework.stereotype.Component

@Component
class PaymentContext(
    private val inicisPaymentStrategy: InicisPaymentStrategy,
    private val pgProviderService: PgProviderService
) {

    fun executePaymentCancel(request: PaymentCancelRequest): PaymentCancelResponse {
        return inicisPaymentStrategy.cancelPayment(request)
    }
    
    fun executePaymentApproval(request: PaymentApprovalRequest): PaymentApprovalResponse {
        return inicisPaymentStrategy.approvePayment(request)
    }
    
    fun executePaymentPreparation(request: PaymentPrepareRequest): PaymentPrepareResponse {
        val actualPaymentType = determinePaymentTypeFromRequest(request)
        val strategy = getPaymentStrategy(actualPaymentType, request)
        return strategy.preparePayment(request)
    }
    
    private fun determinePaymentTypeFromRequest(request: PaymentPrepareRequest): PaymentType {
        return when (request.paymentMethod.uppercase()) {
            "PG" -> PaymentType.PG
            "HYBRID" -> PaymentType.HYBRID
            else -> PaymentType.PG // 기본값
        }
    }


    private fun getPaymentStrategy(paymentType: PaymentType, request: PaymentPrepareRequest? = null): PaymentStrategy {
        return when (paymentType) {
            PaymentType.PG -> {
                if (request != null) {
                    getPgStrategyByWeight()
                } else {
                    inicisPaymentStrategy
                }
            }
            PaymentType.POINT -> inicisPaymentStrategy
            PaymentType.HYBRID -> inicisPaymentStrategy
        }
    }
    
    private fun getPgStrategyByWeight(): PaymentStrategy {
        val selectedProvider = pgProviderService.selectProvider()

        return when (selectedProvider.name.lowercase()) {
            "inicis" -> inicisPaymentStrategy
            "toss" -> inicisPaymentStrategy
            else -> throw PaymentException("지원하지 않는 PG사입니다: ${selectedProvider.name}")
        }
    }

}