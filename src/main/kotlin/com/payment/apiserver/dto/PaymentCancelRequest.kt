package com.payment.apiserver.dto

import jakarta.validation.constraints.NotBlank

data class PaymentCancelRequest(
    @field:NotBlank(message = "TID는 필수입니다")
    val tid: String,
    
    @field:NotBlank(message = "취소 사유는 필수입니다")
    val reason: String,
    
    val cancelType: CancelType = CancelType.MANUAL
)

enum class CancelType {
    GENERAL,    // 일반 취소 (승인취소)
    MANUAL,     // 수동 취소 (승인취소) - 기존 호환성
    NETWORK     // 자동 취소 (망취소)
}