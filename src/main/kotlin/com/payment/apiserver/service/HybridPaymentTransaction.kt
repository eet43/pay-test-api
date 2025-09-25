package com.payment.apiserver.service

import com.payment.apiserver.dto.HybridPaymentStatus
import java.time.LocalDateTime

data class HybridPaymentTransaction(
    val hybridPaymentId: String,
    val orderId: String,
    val totalAmount: Long,
    val pointAmount: Long,
    val pgAmount: Long,
    val pointPaymentId: String? = null,
    val pgPaymentId: String? = null,
    val pgProvider: String? = null,
    val status: HybridPaymentStatus,
    val createdAt: LocalDateTime,
    val pointCompletedAt: LocalDateTime? = null,
    val pgCompletedAt: LocalDateTime? = null,
    val cancelledAt: LocalDateTime? = null
)