package com.payment.apiserver.repository

import com.payment.apiserver.entity.Payment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PaymentRepository : JpaRepository<Payment, Long> {

    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId")
    fun findByOrderId(orderId: String): Payment?

    fun findByUserId(userId: Long): List<Payment>

    @Query("SELECT p FROM Payment p WHERE p.userId = :userId ORDER BY p.createdAt DESC")
    fun findByUserIdOrderByCreatedAtDesc(userId: String): List<Payment>

    @Query("SELECT p FROM Payment p WHERE p.order.id = :orderId AND p.pgTid IS NOT NULL")
    fun findByOrderIdWithTid(orderId: String): Payment?

    fun findByPgTid(pgTid: String): Payment?
}