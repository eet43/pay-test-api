package com.payment.apiserver.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

data class HybridPaymentAuthRequest(
    @field:NotBlank(message = "주문번호는 필수입니다")
    val orderId: String,
    
    @field:NotNull(message = "총 결제금액은 필수입니다")
    @field:Min(value = 100, message = "최소 결제금액은 100원입니다")
    val totalAmount: Long,
    
    @field:NotNull(message = "포인트 사용금액은 필수입니다")
    @field:Min(value = 0, message = "포인트 사용금액은 0원 이상이어야 합니다")
    val pointAmount: Long,
    
    @field:NotBlank(message = "상품명은 필수입니다")
    val productName: String,
    
    @field:NotBlank(message = "구매자명은 필수입니다")
    val buyerName: String,
    
    @field:NotBlank(message = "구매자 이메일은 필수입니다")
    val buyerEmail: String,
    
    @field:NotBlank(message = "구매자 전화번호는 필수입니다")
    val buyerTel: String,
    
    @field:NotBlank(message = "결제 완료 후 이동할 URL은 필수입니다")
    val returnUrl: String,
    
    @field:NotBlank(message = "결제 취소 시 이동할 URL은 필수입니다")
    val closeUrl: String
) {
    val pgAmount: Long
        get() = totalAmount - pointAmount
        
    init {
        require(pointAmount <= totalAmount) { "포인트 사용금액은 총 결제금액을 초과할 수 없습니다" }
        require(totalAmount - pointAmount >= 100) { "PG 결제 금액은 최소 100원 이상이어야 합니다" }
    }
}

data class HybridPaymentAuthResponse(
    val success: Boolean,
    val message: String,
    val data: HybridPaymentAuthData?
)

data class HybridPaymentAuthData(
    val hybridPaymentId: String,
    val orderId: String,
    val totalAmount: Long,
    val pointAmount: Long,
    val pgAmount: Long,
    val pointPaymentId: String?,
    val pgPaymentId: String?,
    val pgPaymentUrl: String?,
    val status: HybridPaymentStatus,
    val timestamp: String
)

enum class HybridPaymentStatus {
    PREPARED,          // 결제 준비 완료 (승인요청 정보 저장됨)
    PENDING,           // 결제 진행 중
    POINT_COMPLETED,   // 포인트 결제 완료, PG 결제 대기
    PG_COMPLETED,      // PG 결제 완료, 전체 결제 완료
    FAILED,            // 결제 실패
    CANCELLED          // 결제 취소
}