package com.payment.apiserver.controller

import com.payment.apiserver.dto.*
import com.payment.apiserver.service.PaymentService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/payment")
class PaymentController(
    private val paymentService: PaymentService
) {

    @PostMapping("/auth")
    fun authenticatePayment(@RequestBody request: PaymentAuthRequest): ResponseEntity<PaymentAuthResponse> {
        return try {
            val response = paymentService.authenticatePayment(request)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                PaymentAuthResponse(
                    success = false,
                    message = e.message ?: "결제 인증 중 오류가 발생했습니다",
                    data = null
                )
            )
        }
    }

    @PostMapping("/approve")
    fun approvePayment(@RequestBody request: PaymentApprovalRequest): ResponseEntity<PaymentApprovalResponse> {
        return try {
            val response = paymentService.approvePayment(request)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                PaymentApprovalResponse(
                    success = false,
                    message = e.message ?: "결제 승인 중 오류가 발생했습니다",
                    data = null
                )
            )
        }
    }

    @PostMapping("/cancel")
    fun cancelPayment(@RequestBody request: PaymentCancelRequest): ResponseEntity<PaymentCancelResponse> {
        return try {
            val response = paymentService.cancelPayment(request)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                PaymentCancelResponse(
                    success = false,
                    message = e.message ?: "결제 취소 중 오료가 발생했습니다",
                    data = null
                )
            )
        }
    }

    @PostMapping("/network-cancel")
    fun networkCancelPayment(@RequestBody request: PaymentCancelRequest): ResponseEntity<PaymentCancelResponse> {
        return try {
            val response = paymentService.cancelPayment(request)
            ResponseEntity.ok(response)
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(
                PaymentCancelResponse(
                    success = false,
                    message = e.message ?: "망취소 중 오류가 발생했습니다",
                    data = null
                )
            )
        }
    }
}