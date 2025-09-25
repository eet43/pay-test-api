package com.payment.apiserver.dto

import jakarta.validation.constraints.*

data class PaymentPrepareRequest(
    @field:NotNull(message = "회원번호는 필수입니다")
    val userId: Long,

    @field:NotNull(message = "결제금액은 필수입니다")
    @field:Min(value = 100, message = "최소 결제금액은 100원입니다")
    val amount: Long,

    @field:NotBlank(message = "상품명은 필수입니다")
    @field:Size(max = 40, message = "상품명은 40자 이내여야 합니다")
    val productName: String,

    @field:NotBlank(message = "구매자명은 필수입니다")
    @field:Size(max = 30, message = "구매자명은 30자 이내여야 합니다")
    val buyerName: String,

    @field:NotBlank(message = "이메일은 필수입니다")
    @field:Email(message = "올바른 이메일 형식이 아닙니다")
    val buyerEmail: String,

    @field:NotBlank(message = "연락처는 필수입니다")
    val buyerTel: String,

    @field:NotBlank(message = "타임스탬프는 필수입니다")
    val timestamp: String,

    val isMobile: Boolean = false,

    val usePoints: Boolean = false,

    @field:Min(value = 0, message = "포인트 사용량은 0 이상이어야 합니다")
    val pointsToUse: Long = 0,

    @field:NotNull(message = "최종 결제금액은 필수입니다")
    @field:Min(value = 0, message = "최종 결제금액은 0 이상이어야 합니다")
    val finalPaymentAmount: Long,

    @field:NotBlank(message = "결제방식은 필수입니다")
    val paymentMethod: String
)