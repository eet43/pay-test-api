package com.payment.apiserver.service

import com.payment.apiserver.client.InicisAuthResponse
import com.payment.apiserver.client.InicisCancelResponse
import com.payment.apiserver.client.InicisPaymentClient
import com.payment.apiserver.config.PaymentProperties
import com.payment.apiserver.dto.*
import com.payment.apiserver.exception.PaymentException
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reactor.core.publisher.Mono
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@DisplayName("PaymentService 취소 기능 테스트")
class PaymentCancellationTest {

    private lateinit var paymentService: PaymentService
    private lateinit var inicisPaymentClient: InicisPaymentClient
    private lateinit var pgProviderService: PgProviderService
    private lateinit var paymentProperties: PaymentProperties

    private val testProvider = PaymentProperties.PgProvider(
        name = "TEST_PG",
        weight = 100,
        apiUrl = "https://test-pg.com",
        merchantId = "test_merchant",
        apiKey = "test_key",
        signKey = "test_sign_key",
        hashKey = "test_hash_key"
    )

    @BeforeEach
    fun setup() {
        inicisPaymentClient = mockk()
        pgProviderService = mockk()
        paymentProperties = PaymentProperties(
            pg = PaymentProperties.PgProperties(listOf(testProvider)),
            minimumAmount = 100,
            cors = PaymentProperties.CorsProperties(listOf("http://localhost:3000"))
        )
        
        paymentService = PaymentService(inicisPaymentClient, pgProviderService, paymentProperties)
        
        every { pgProviderService.selectProvider() } returns testProvider
        every { pgProviderService.getProviderByName("TEST_PG") } returns testProvider
    }

    private fun setupPaymentTransaction(): String {
        val tid = "TID123456"
        val authRequest = PaymentAuthRequest(
            orderId = "ORDER123",
            amount = 1000L,
            productName = "테스트 상품",
            buyerName = "홍길동",
            buyerEmail = "test@example.com",
            buyerTel = "01012345678"
        )

        val authResponse = InicisAuthResponse(
            resultCode = "0000",
            resultMsg = "성공",
            tid = tid,
            authUrl = "https://pay.test.com/auth",
            timestamp = 20240101120000
        )

        every {
            inicisPaymentClient.authenticatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mono.just(authResponse)

        paymentService.authenticatePayment(authRequest)
        return tid
    }

    @DisplayName("수동 승인취소 성공 테스트")
    @Test
    fun `수동 승인취소가 성공하면 PaymentCancelResponse를 반환한다`() {
        // Given
        val tid = setupPaymentTransaction()
        val cancelRequest = PaymentCancelRequest(
            tid = tid,
            reason = "고객 요청에 의한 취소",
            cancelType = CancelType.MANUAL
        )

        val cancelResponse = InicisCancelResponse(
            resultCode = "0000",
            resultMsg = "취소 성공",
            tid = tid,
            cancelPrice = 1000L,
            cancelTime = "20240101130000",
            timestamp = "20240101130000"
        )

        every {
            inicisPaymentClient.cancelPayment(
                apiUrl = any(),
                merchantId = any(),
                apiKey = any(),
                signKey = any(),
                hashKey = any(),
                tid = any(),
                reason = any(),
                isNetworkCancel = false
            )
        } returns Mono.just(cancelResponse)

        // When
        val result = paymentService.cancelPayment(cancelRequest)

        // Then
        assertTrue(result.success)
        assertEquals("승인취소가 완료되었습니다", result.message)
        assertNotNull(result.data)
        assertEquals(tid, result.data?.tid)
        assertEquals("ORDER123", result.data?.orderId)
        assertEquals(1000L, result.data?.cancelledAmount)
        assertEquals("20240101130000", result.data?.cancelledAt)
        assertEquals("고객 요청에 의한 취소", result.data?.reason)
        assertEquals(CancelType.MANUAL, result.data?.cancelType)

        // PG 호출 확인
        verify {
            inicisPaymentClient.cancelPayment(
                apiUrl = "https://test-pg.com",
                merchantId = "test_merchant",
                apiKey = "test_key",
                signKey = "test_sign_key",
                hashKey = "test_hash_key",
                tid = tid,
                reason = "고객 요청에 의한 취소",
                isNetworkCancel = false
            )
        }
    }

    @DisplayName("자동 망취소 성공 테스트")
    @Test
    fun `자동 망취소가 성공하면 PaymentCancelResponse를 반환한다`() {
        // Given
        val tid = setupPaymentTransaction()
        val cancelRequest = PaymentCancelRequest(
            tid = tid,
            reason = "승인 응답값 불일치로 인한 자동 망취소",
            cancelType = CancelType.NETWORK
        )

        val cancelResponse = InicisCancelResponse(
            resultCode = "0000",
            resultMsg = "망취소 성공",
            tid = tid,
            cancelPrice = 1000L,
            cancelTime = "20240101130000",
            timestamp = "20240101130000"
        )

        every {
            inicisPaymentClient.cancelPayment(
                apiUrl = any(),
                merchantId = any(),
                apiKey = any(),
                signKey = any(),
                hashKey = any(),
                tid = any(),
                reason = any(),
                isNetworkCancel = true
            )
        } returns Mono.just(cancelResponse)

        // When
        val result = paymentService.cancelPayment(cancelRequest)

        // Then
        assertTrue(result.success)
        assertEquals("망취소가 완료되었습니다", result.message)
        assertNotNull(result.data)
        assertEquals(tid, result.data?.tid)
        assertEquals("ORDER123", result.data?.orderId)
        assertEquals(1000L, result.data?.cancelledAmount)
        assertEquals("20240101130000", result.data?.cancelledAt)
        assertEquals("승인 응답값 불일치로 인한 자동 망취소", result.data?.reason)
        assertEquals(CancelType.NETWORK, result.data?.cancelType)

        // PG 호출 확인
        verify {
            inicisPaymentClient.cancelPayment(
                apiUrl = "https://test-pg.com",
                merchantId = "test_merchant",
                apiKey = "test_key",
                signKey = "test_sign_key",
                hashKey = "test_hash_key",
                tid = tid,
                reason = "승인 응답값 불일치로 인한 자동 망취소",
                isNetworkCancel = true
            )
        }
    }

    @DisplayName("존재하지 않는 결제 정보로 취소 시 예외 발생")
    @Test
    fun `존재하지 않는 TID로 취소 요청하면 PaymentException이 발생한다`() {
        // Given
        val cancelRequest = PaymentCancelRequest(
            tid = "NONEXISTENT_TID",
            reason = "테스트 취소"
        )

        // When & Then
        val exception = assertThrows<PaymentException> {
            paymentService.cancelPayment(cancelRequest)
        }
        
        assertEquals("결제 정보를 찾을 수 없습니다", exception.message)
    }

    @DisplayName("PG 취소 실패 시 예외 발생")
    @Test
    fun `PG 취소가 실패하면 PaymentException이 발생한다`() {
        // Given
        val tid = setupPaymentTransaction()
        val cancelRequest = PaymentCancelRequest(
            tid = tid,
            reason = "테스트 취소"
        )

        val failResponse = InicisCancelResponse(
            resultCode = "9999",
            resultMsg = "취소 실패",
            tid = tid,
            cancelPrice = 0L,
            cancelTime = "",
            timestamp = "20240101130000"
        )

        every {
            inicisPaymentClient.cancelPayment(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mono.just(failResponse)

        // When & Then
        val exception = assertThrows<PaymentException> {
            paymentService.cancelPayment(cancelRequest)
        }
        
        assertTrue(exception.message!!.contains("PG 취소 실패"))
        assertTrue(exception.message!!.contains("취소 실패"))
    }

    @DisplayName("PG 통신 오류 시 예외 발생")
    @Test
    fun `PG 통신 오류가 발생하면 PaymentException이 발생한다`() {
        // Given
        val tid = setupPaymentTransaction()
        val cancelRequest = PaymentCancelRequest(
            tid = tid,
            reason = "테스트 취소"
        )

        every {
            inicisPaymentClient.cancelPayment(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mono.error(RuntimeException("Network error"))

        // When & Then
        val exception = assertThrows<PaymentException> {
            paymentService.cancelPayment(cancelRequest)
        }
        
        assertTrue(exception.message!!.contains("결제 취소 중 시스템 오류가 발생했습니다"))
    }
}