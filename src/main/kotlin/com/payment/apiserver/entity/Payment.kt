package com.payment.apiserver.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "payments")
class Payment(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "user_id", nullable = false)
    val userId: Long,
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    val order: Order,

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_pg_provider")
    val paymentPgProvider: PaymentPgProvider,
    
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false)
    val paymentMethod: PaymentMethod,
    
    @Column(nullable = false)
    val amount: Long,
    
    @Column(name = "point_amount")
    val pointAmount: Long? = null,
    
    @Column(name = "card_amount")
    val cardAmount: Long? = null,

    @Column(name = "pg_tid")
    val pgTid: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: PaymentStatus,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)

enum class PaymentPgProvider {
    INICIS, TOSS
}

enum class PaymentMethod {
    POINT, CARD, MIXED
}

enum class PaymentStatus {
    SUCCESS, FAILED, CANCELLED
}