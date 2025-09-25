package com.payment.apiserver.dto

import jakarta.validation.constraints.NotBlank

data class PaymentApprovalRequest(
    @field:NotBlank(message = "authToken는 필수입니다")
    val authToken: String,
    @field:NotBlank(message = "authUrl 필수입니다")
    val authUrl: String,
    
    @field:NotBlank(message = "MID는 필수입니다") 
    val mid: String,
    
    @field:NotBlank(message = "주문번호는 필수입니다")
    val orderNumber: String,

    val netCancelUrl: String? = null,
    val signature: String? = null,
    val verification: String? = null,
    val charset: String? = null,
    val format: String? = null,
    val timestamp: String? = null
)