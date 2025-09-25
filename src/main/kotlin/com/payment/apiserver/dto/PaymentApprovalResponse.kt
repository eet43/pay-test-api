package com.payment.apiserver.dto

data class PaymentApprovalResponse(
    val success: Boolean,
    val message: String,
    val data: PaymentApprovalData?
)

data class PaymentApprovalData(
    val tid: String?,
    val orderId: String?,
    val paymentId: Long? = null,
    val amount: Long?,
    val pgProvider: String?,
    val paymentMethod: String?,
    val approvedAt: String?,
    val status: PaymentStatus
)

enum class PaymentStatus {
    PENDING,
    APPROVED,
    FAILED,
    CANCELLED,
    NETWORK_CANCELLED
}