package com.payment.apiserver.service

import java.time.LocalDateTime

data class PaymentTransaction(
    val tid: String,
    val orderId: String,
    val amount: Long,
    val pgProvider: String,
    val status: PaymentStatus,
    val createdAt: LocalDateTime,
    val approvedAt: LocalDateTime? = null,
    val cancelledAt: LocalDateTime? = null
)

enum class PaymentStatus {
    PREPARED,
    PENDING,
    APPROVED,
    FAILED,
    CANCELLED,
    NETWORK_CANCELLED
}