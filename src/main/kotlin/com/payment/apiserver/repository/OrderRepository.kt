package com.payment.apiserver.repository

import com.payment.apiserver.entity.Order
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface OrderRepository : JpaRepository<Order, String> {

    fun findByUserId(userId: Long): List<Order>

    @Query("""
        SELECT o FROM Order o
        LEFT JOIN FETCH o.payment p
        WHERE o.id = :orderId
    """)
    fun findByIdWithPayment(orderId: String): Order?

    @Query("""
        SELECT o FROM Order o
        LEFT JOIN FETCH o.payment p
        WHERE o.userId = :userId
        ORDER BY o.createdAt DESC
    """)
    fun findByUserIdWithPayment(userId: Long): List<Order>
}