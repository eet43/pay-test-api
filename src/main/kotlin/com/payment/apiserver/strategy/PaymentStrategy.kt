package com.payment.apiserver.strategy

import com.payment.apiserver.dto.*

interface PaymentStrategy {
    fun authenticatePayment(request: PaymentAuthRequest): PaymentAuthResponse
    fun approvePayment(request: PaymentApprovalRequest): PaymentApprovalResponse
    fun cancelPayment(request: PaymentCancelRequest): PaymentCancelResponse
    fun preparePayment(request: PaymentPrepareRequest): PaymentPrepareResponse
    fun handlePaymentReturn(request: PaymentReturnRequest): PaymentReturnResponse

    fun getPaymentType(): PaymentType
}

enum class PaymentType {
    PG,
    POINT,
    HYBRID
}