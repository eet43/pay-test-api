package com.payment.apiserver.dto

import com.payment.apiserver.entity.PaymentStatus

data class PaymentOrderDetailResponse(
    val success: Boolean,
    val message: String,
    val data: PaymentOrderDetailData?
)

data class PaymentOrderDetailData(
    val orderId: String,
    val tid: String?,
    val productName: String,
    val amount: Long,
    val pgName: String?,
    val paymentMethod: String?,
    val status: PaymentStatus,
    val createdAt: String,
    val paymentId: String
)