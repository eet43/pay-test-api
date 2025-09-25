package com.payment.apiserver.repository

import com.payment.apiserver.entity.PaymentRequest
import com.payment.apiserver.entity.PaymentRequestType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PaymentRequestRepository : JpaRepository<PaymentRequest, Long> {
    fun findByOrderId(orderId: String): List<PaymentRequest>
    fun findByOrderIdAndRequestType(orderId: String, requestType: PaymentRequestType): PaymentRequest?
    fun findByUserIdAndOrderId(userId: Long, orderId: String): List<PaymentRequest>
}