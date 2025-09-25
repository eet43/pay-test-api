package com.payment.apiserver.controller

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.payment.apiserver.dto.*
import com.payment.apiserver.service.PaymentService
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@WebMvcTest(PaymentController::class)
@DisplayName("PaymentController 통합 테스트")
class PaymentControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var paymentService: PaymentService

    private val objectMapper = jacksonObjectMapper()

    @DisplayName("결제 인증 API 성공 테스트")
    @Test
    fun `POST payment auth - 결제 인증이 성공하면 200과 함께 PaymentAuthResponse를 반환한다`() {
        // Given
        val request = PaymentAuthRequest(
            orderId = "ORDER123",
            amount = 1000L,
            productName = "테스트 상품",
            buyerName = "홍길동",
            buyerEmail = "test@example.com",
            buyerTel = "01012345678"
        )

        val response = PaymentAuthResponse(
            success = true,
            message = "결제 인증이 완료되었습니다",
            data = PaymentAuthData(
                tid = "TID123456",
                orderId = "ORDER123",
                amount = 1000L,
                pgProvider = "TEST_PG",
                authUrl = "https://pay.test.com/auth",
                timestamp = "2024-01-01 12:00:00"
            )
        )

        every { paymentService.authenticatePayment(request) } returns response

        // When & Then
        mockMvc.perform(
            post("/api/payment/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("결제 인증이 완료되었습니다"))
        .andExpect(jsonPath("$.data.tid").value("TID123456"))
        .andExpect(jsonPath("$.data.orderId").value("ORDER123"))
        .andExpect(jsonPath("$.data.amount").value(1000))
        .andExpect(jsonPath("$.data.pgProvider").value("TEST_PG"))
        .andExpect(jsonPath("$.data.authUrl").value("https://pay.test.com/auth"))
    }

    @DisplayName("결제 인증 API 유효성 검증 실패")
    @Test
    fun `POST payment auth - 필수 필드가 누락되면 400 에러를 반환한다`() {
        // Given - orderId가 누락된 요청
        val invalidRequest = mapOf(
            "amount" to 1000,
            "productName" to "테스트 상품",
            "buyerName" to "홍길동",
            "buyerEmail" to "test@example.com",
            "buyerTel" to "01012345678"
        )

        // When & Then
        mockMvc.perform(
            post("/api/payment/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
        .andExpect(status().isBadRequest)
    }

    @DisplayName("결제 인증 API 최소 금액 검증 실패")
    @Test
    fun `POST payment auth - 최소 금액 미만이면 400 에러를 반환한다`() {
        // Given
        val invalidRequest = PaymentAuthRequest(
            orderId = "ORDER123",
            amount = 50L, // 최소 금액 100원 미만
            productName = "테스트 상품",
            buyerName = "홍길동",
            buyerEmail = "test@example.com",
            buyerTel = "01012345678"
        )

        // When & Then
        mockMvc.perform(
            post("/api/payment/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
        .andExpect(status().isBadRequest)
    }

    @DisplayName("결제 승인 API 성공 테스트")
    @Test
    fun `POST payment approve - 결제 승인이 성공하면 200과 함께 PaymentApprovalResponse를 반환한다`() {
        // Given
        val request = PaymentApprovalRequest(
            authToken = "TID123456",
            authUrl = "https://auth.test.com",
            mid = "test_merchant",
            orderNumber = "ORDER123"
        )

        val response = PaymentApprovalResponse(
            success = true,
            message = "결제 승인이 완료되었습니다",
            data = PaymentApprovalData(
                tid = "TID123456",
                orderId = "ORDER123",
                amount = 1000L,
                pgProvider = "TEST_PG",
                paymentMethod = "CARD",
                approvedAt = "20240101120100",
                status = PaymentStatus.APPROVED
            )
        )

        every { paymentService.approvePayment(request) } returns response

        // When & Then
        mockMvc.perform(
            post("/api/payment/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("결제 승인이 완료되었습니다"))
        .andExpect(jsonPath("$.data.tid").value("TID123456"))
        .andExpect(jsonPath("$.data.orderId").value("ORDER123"))
        .andExpect(jsonPath("$.data.amount").value(1000))
        .andExpect(jsonPath("$.data.pgProvider").value("TEST_PG"))
        .andExpect(jsonPath("$.data.paymentMethod").value("CARD"))
        .andExpect(jsonPath("$.data.status").value("APPROVED"))
    }

    @DisplayName("결제 승인 API 유효성 검증 실패")
    @Test
    fun `POST payment approve - 필수 필드가 누락되면 400 에러를 반환한다`() {
        // Given - tid가 누락된 요청
        val invalidRequest = mapOf(
            "orderId" to "ORDER123"
        )

        // When & Then
        mockMvc.perform(
            post("/api/payment/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidRequest))
        )
        .andExpect(status().isBadRequest)
    }

    @DisplayName("결제 취소 API 성공 테스트")
    @Test
    fun `POST payment cancel - 결제 취소가 성공하면 200과 함께 PaymentCancelResponse를 반환한다`() {
        // Given
        val request = PaymentCancelRequest(
            tid = "TID123456",
            reason = "고객 요청에 의한 취소"
        )

        val response = PaymentCancelResponse(
            success = true,
            message = "승인취소가 완료되었습니다",
            data = PaymentCancelData(
                tid = "TID123456",
                orderId = "ORDER123",
                cancelledAmount = 1000L,
                cancelledAt = "20240101130000",
                reason = "고객 요청에 의한 취소",
                cancelType = CancelType.MANUAL
            )
        )

        every { paymentService.cancelPayment(request) } returns response

        // When & Then
        mockMvc.perform(
            post("/api/payment/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("승인취소가 완료되었습니다"))
        .andExpect(jsonPath("$.data.tid").value("TID123456"))
        .andExpect(jsonPath("$.data.orderId").value("ORDER123"))
        .andExpect(jsonPath("$.data.cancelledAmount").value(1000))
        .andExpect(jsonPath("$.data.cancelType").value("MANUAL"))
    }

    @DisplayName("망취소 API 성공 테스트")
    @Test
    fun `POST payment network-cancel - 망취소가 성공하면 200과 함께 PaymentCancelResponse를 반환한다`() {
        // Given
        val request = PaymentCancelRequest(
            tid = "TID123456",
            reason = "승인 응답값 불일치로 인한 자동 망취소",
            cancelType = CancelType.NETWORK
        )

        val response = PaymentCancelResponse(
            success = true,
            message = "망취소가 완료되었습니다",
            data = PaymentCancelData(
                tid = "TID123456",
                orderId = "ORDER123",
                cancelledAmount = 1000L,
                cancelledAt = "20240101130000",
                reason = "승인 응답값 불일치로 인한 자동 망취소",
                cancelType = CancelType.NETWORK
            )
        )

        every { paymentService.cancelPayment(request) } returns response

        // When & Then
        mockMvc.perform(
            post("/api/payment/network-cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isOk)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("망취소가 완료되었습니다"))
        .andExpect(jsonPath("$.data.tid").value("TID123456"))
        .andExpect(jsonPath("$.data.cancelType").value("NETWORK"))
    }

    @DisplayName("서비스 예외 발생 시 400 에러 반환")
    @Test
    fun `결제 서비스에서 예외가 발생하면 400 에러와 에러 메시지를 반환한다`() {
        // Given
        val request = PaymentAuthRequest(
            orderId = "ORDER123",
            amount = 1000L,
            productName = "테스트 상품",
            buyerName = "홍길동",
            buyerEmail = "test@example.com",
            buyerTel = "01012345678"
        )

        every { paymentService.authenticatePayment(request) } throws RuntimeException("테스트 에러")

        // When & Then
        mockMvc.perform(
            post("/api/payment/auth")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request))
        )
        .andExpect(status().isBadRequest)
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("테스트 에러"))
        .andExpect(jsonPath("$.data").isEmpty)
    }
}