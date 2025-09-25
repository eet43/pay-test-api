package com.payment.apiserver.dto

import com.payment.apiserver.entity.OrderStatus
import java.time.LocalDateTime

data class OrderListResponse(
    val orderId: String,
    val productName: String,
    val totalAmount: Long,
    val status: OrderStatus,
    val pgTid: String?,
    val createdAt: LocalDateTime
)