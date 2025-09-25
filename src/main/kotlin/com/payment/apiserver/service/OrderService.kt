package com.payment.apiserver.service

import com.payment.apiserver.dto.OrderDetailResponse
import com.payment.apiserver.dto.OrderListResponse
import com.payment.apiserver.entity.Order
import com.payment.apiserver.repository.OrderRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class OrderService(
    private val orderRepository: OrderRepository
) {

    fun getOrderDetail(orderId: String): OrderDetailResponse {
        val order = orderRepository.findByIdWithPayment(orderId)
            ?: throw IllegalArgumentException("주문을 찾을 수 없습니다: $orderId")

        return OrderDetailResponse(
            orderId = order.id,
            userId = order.userId,
            productName = order.productName,
            productPrice = order.productPrice,
            totalAmount = order.totalAmount,
            pointAmount = order.pointAmount,
            cardAmount = order.cardAmount,
            status = order.status,
            termsAgreed = order.termsAgreed,
            pgTid = order.payment?.pgTid,
            createdAt = order.createdAt,
            updatedAt = order.updatedAt
        )
    }

    fun getOrdersByUserId(userId: Long): List<OrderListResponse> {
        val orders = orderRepository.findByUserIdWithPayment(userId)

        return orders.map { order ->
            OrderListResponse(
                orderId = order.id,
                productName = order.productName,
                totalAmount = order.totalAmount,
                status = order.status,
                pgTid = order.payment?.pgTid,
                createdAt = order.createdAt
            )
        }
    }
}