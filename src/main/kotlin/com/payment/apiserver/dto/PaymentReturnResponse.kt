package com.payment.apiserver.dto

data class PaymentReturnResponse(
    val success: Boolean,
    val message: String,
    val data: PaymentReturnData?
)

data class PaymentReturnData(
    val resultCode: String,
    val resultMsg: String,
    val orderId: String,
    val amount: Long,
    val timestamp: String
)