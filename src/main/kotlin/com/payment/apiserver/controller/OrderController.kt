package com.payment.apiserver.controller

import com.payment.apiserver.dto.OrderDetailResponse
import com.payment.apiserver.dto.OrderListResponse
import com.payment.apiserver.service.OrderService
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/orders")
class OrderController(
    private val orderService: OrderService
) {

    @GetMapping
    fun getMyOrders(request: HttpServletRequest): ResponseEntity<List<OrderListResponse>> {
        return try {
            val userId = getUserIdFromCookie(request)
            val orders = orderService.getOrdersByUserId(userId)
            ResponseEntity.ok(orders)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/{orderId}")
    fun getOrderDetail(@PathVariable orderId: String): ResponseEntity<OrderDetailResponse> {
        return try {
            val orderDetail = orderService.getOrderDetail(orderId)
            ResponseEntity.ok(orderDetail)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }

    private fun getUserIdFromCookie(request: HttpServletRequest): Long {
        val cookies = request.cookies ?: throw IllegalArgumentException("쿠키가 없습니다")
        val userIdCookie = cookies.find { it.name == "userId" }
            ?: throw IllegalArgumentException("userId 쿠키를 찾을 수 없습니다")

        return userIdCookie.value.toLongOrNull()
            ?: throw IllegalArgumentException("유효하지 않은 userId입니다")
    }
}