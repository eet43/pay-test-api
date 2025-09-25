package com.payment.apiserver.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class PaymentAuthRequest(
    @field:NotBlank(message = "주문번호는 필수입니다")
    val orderId: String,
    
    @field:NotNull(message = "결제 금액은 필수입니다")
    @field:Min(value = 100, message = "결제 금액은 100원 이상이어야 합니다")
    val amount: Long,
    
    @field:NotBlank(message = "상품명은 필수입니다")
    val productName: String,
    
    @field:NotBlank(message = "구매자명은 필수입니다")
    val buyerName: String,
    
    @field:NotBlank(message = "구매자 이메일은 필수입니다")
    val buyerEmail: String,
    
    @field:NotBlank(message = "구매자 전화번호는 필수입니다")
    val buyerTel: String,
    
    val returnUrl: String = "http://localhost:3000/payment/result",
    val closeUrl: String = "http://localhost:3000/payment/close"
)