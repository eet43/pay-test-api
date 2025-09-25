package com.payment.apiserver.dto

data class PaymentAuthResponse(
    val success: Boolean,
    val message: String,
    val data: PaymentAuthData?
)

data class PaymentAuthData(
    val tid: String,
    val orderId: String,
    val amount: Long,
    val pgProvider: String,
    val authUrl: String,
    val timestamp: String
)