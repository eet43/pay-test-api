package com.payment.apiserver.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
class Order(
    @Id
    val id: String,

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "product_name", nullable = false)
    val productName: String,

    @Column(name = "product_price", nullable = false)
    val productPrice: Long,

    @Column(name = "total_amount", nullable = false)
    val totalAmount: Long,

    @Column(name = "point_amount")
    val pointAmount: Long? = null,

    @Column(name = "card_amount")
    val cardAmount: Long? = null,

    @Column(name = "terms_agreed", nullable = false)
    val termsAgreed: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: OrderStatus,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    @OneToOne(mappedBy = "order", fetch = FetchType.LAZY)
    val payment: Payment? = null
)

enum class OrderStatus {
    PENDING, COMPLETED, CANCELLED
}