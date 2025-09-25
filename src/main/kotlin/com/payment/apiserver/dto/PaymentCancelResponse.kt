package com.payment.apiserver.dto

data class PaymentCancelResponse(
    val success: Boolean,
    val message: String,
    val data: PaymentCancelData?
)

data class PaymentCancelData(
    val tid: String,
    val orderId: String,
    val cancelledAmount: Long,
    val cancelledAt: String,
    val reason: String,
    val cancelType: CancelType
)