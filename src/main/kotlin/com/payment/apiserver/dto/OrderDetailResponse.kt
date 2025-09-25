package com.payment.apiserver.dto

import com.payment.apiserver.entity.OrderStatus
import java.time.LocalDateTime

data class OrderDetailResponse(
    val orderId: String,
    val userId: Long,
    val productName: String,
    val productPrice: Long,
    val totalAmount: Long,
    val pointAmount: Long?,
    val cardAmount: Long?,
    val status: OrderStatus,
    val termsAgreed: Boolean,
    val pgTid: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)