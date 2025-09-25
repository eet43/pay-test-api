package com.payment.apiserver.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "payment_requests")
data class PaymentRequest(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    
    @Column(name = "order_id", nullable = false)
    val orderId: String,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false)
    val requestType: PaymentRequestType,
    
    @Column(name = "pg_provider")
    val pgProvider: String? = null,
    
    @Column(nullable = false)
    val amount: Long,
    
    @Column(name = "request_data", columnDefinition = "TEXT")
    val requestData: String? = null,
    
    @Column(name = "response_data", columnDefinition = "TEXT")
    val responseData: String? = null,
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: RequestStatus,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class PaymentRequestType {
    AUTH, APPROVAL, CANCEL, NETWORK_CANCEL
}

enum class RequestStatus {
    PENDING, SUCCESS, FAILED
}