package com.payment.apiserver.service

import com.payment.apiserver.client.InicisAuthResponse
import com.payment.apiserver.client.InicisApprovalResponse
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
import org.springframework.test.util.ReflectionTestUtils
import reactor.core.publisher.Mono
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

@DisplayName("PaymentService 테스트")
class PaymentServiceTest {

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

    @DisplayName("결제 인증 성공 테스트")
    @Test
    fun `결제 인증이 성공하면 PaymentAuthResponse를 반환한다`() {
        // Given
        val request = PaymentAuthRequest(
            orderId = "ORDER123",
            amount = 1000L,
            productName = "테스트 상품",
            buyerName = "홍길동",
            buyerEmail = "test@example.com",
            buyerTel = "01012345678"
        )

        val mockResponse = InicisAuthResponse(
            resultCode = "0000",
            resultMsg = "성공",
            tid = "TID123456",
            authUrl = "https://pay.test.com/auth",
            timestamp = 20240101120000
        )

        every {
            inicisPaymentClient.authenticatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mono.just(mockResponse)

        // When
        val result = paymentService.authenticatePayment(request)

        // Then
        assertTrue(result.success)
        assertEquals("결제 인증이 완료되었습니다", result.message)
        assertNotNull(result.data)
        assertEquals("TID123456", result.data?.tid)
        assertEquals("ORDER123", result.data?.orderId)
        assertEquals(1000L, result.data?.amount)
        assertEquals("TEST_PG", result.data?.pgProvider)
        assertEquals("https://pay.test.com/auth", result.data?.authUrl)
    }

    @DisplayName("최소 결제 금액 미만 시 예외 발생")
    @Test
    fun `결제 금액이 최소 금액보다 작으면 PaymentException이 발생한다`() {
        // Given
        val request = PaymentAuthRequest(
            orderId = "ORDER123",
            amount = 50L, // 최소 금액 100원보다 작음
            productName = "테스트 상품",
            buyerName = "홍길동",
            buyerEmail = "test@example.com",
            buyerTel = "01012345678"
        )

        // When & Then
        val exception = assertThrows<PaymentException> {
            paymentService.authenticatePayment(request)
        }
        
        assertTrue(exception.message!!.contains("최소 금액"))
    }

    @DisplayName("PG 인증 실패 시 예외 발생")
    @Test
    fun `PG 인증이 실패하면 PaymentException이 발생한다`() {
        // Given
        val request = PaymentAuthRequest(
            orderId = "ORDER123",
            amount = 1000L,
            productName = "테스트 상품",
            buyerName = "홍길동",
            buyerEmail = "test@example.com",
            buyerTel = "01012345678"
        )

        val mockResponse = InicisAuthResponse(
            resultCode = "9999",
            resultMsg = "인증 실패",
            tid = "",
            authUrl = "",
            timestamp = 20240101120000
        )

        every {
            inicisPaymentClient.authenticatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mono.just(mockResponse)

        // When & Then
        val exception = assertThrows<PaymentException> {
            paymentService.authenticatePayment(request)
        }
        
        assertTrue(exception.message!!.contains("PG 인증 실패"))
        assertTrue(exception.message!!.contains("인증 실패"))
    }

    @DisplayName("결제 승인 성공 테스트")
    @Test
    fun `결제 승인이 성공하면 PaymentApprovalResponse를 반환한다`() {
        // Given
        val tid = "TID123456"
        val orderId = "ORDER123"
        val amount = 1000L
        
        // 먼저 인증을 진행하여 결제 정보를 저장
        val authRequest = PaymentAuthRequest(
            orderId = orderId,
            amount = amount,
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
            timestamp = 20240101120000L
        )

        every {
            inicisPaymentClient.authenticatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mono.just(authResponse)

        // 인증 실행
        paymentService.authenticatePayment(authRequest)

        // 승인 요청
        val approvalRequest = PaymentApprovalRequest(
            authToken = tid,
            authUrl = "https://auth.test.com",
            mid = "test_merchant",
            orderNumber = orderId
        )
        
        val approvalResponse = InicisApprovalResponse(
            resultCode = "0000",
            resultMsg = "승인 성공",
            tid = tid,
            mid = "test_merchant",
            MOID = orderId,
            TotPrice = amount,
            goodName = "테스트 상품",
            payMethod = "CARD",
            applDate = "20240101",
            applTime = "120100",
            EventCode = null,
            buyerName = "홍길동",
            buyerTel = "01012345678",
            buyerEmail = "test@example.com",
            custEmail = "test@example.com",
            timestamp = 20240101120100L,
            netCancelUrl = null
        )

        every {
            inicisPaymentClient.approvePayment(any(), any(), any(), any(), any(), any())
        } returns Mono.just(approvalResponse)

        // When
        val result = paymentService.approvePayment(approvalRequest)

        // Then
        assertTrue(result.success)
        assertEquals("결제 승인이 완료되었습니다", result.message)
        assertNotNull(result.data)
        assertEquals(tid, result.data?.tid)
        assertEquals(orderId, result.data?.orderId)
        assertEquals(amount, result.data?.amount)
        assertEquals("TEST_PG", result.data?.pgProvider)
        assertEquals("CARD", result.data?.paymentMethod)
        assertEquals("20240101120100", result.data?.approvedAt)
        assertEquals(PaymentStatus.APPROVED, result.data?.status)
    }

    @DisplayName("승인 응답값 불일치 시 망취소 실행")
    @Test
    fun `승인 응답 금액이 요청 금액과 다르면 망취소를 실행하고 예외를 발생시킨다`() {
        // Given
        val tid = "TID123456"
        val orderId = "ORDER123"
        val requestAmount = 1000L
        val responseAmount = 2000L // 응답 금액이 다름
        
        // 인증 진행
        val authRequest = PaymentAuthRequest(
            orderId = orderId,
            amount = requestAmount,
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
            timestamp = 20240101120000L
        )

        every {
            inicisPaymentClient.authenticatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),any(), any())
        } returns Mono.just(authResponse)

        paymentService.authenticatePayment(authRequest)

        // 승인 요청
        val approvalRequest = PaymentApprovalRequest(
            authToken = tid,
            authUrl = "https://auth.test.com",
            mid = "test_merchant",
            orderNumber = orderId
        )
        
        val approvalResponse = InicisApprovalResponse(
            resultCode = "0000",
            resultMsg = "승인 성공",
            tid = tid,
            mid = "test_merchant",
            MOID = orderId,
            TotPrice = responseAmount, // 다른 금액
            goodName = "테스트 상품",
            payMethod = "CARD",
            applDate = "20240101",
            applTime = "120100",
            EventCode = null,
            buyerName = "홍길동",
            buyerTel = "01012345678",
            buyerEmail = "test@example.com",
            custEmail = "test@example.com",
            timestamp = 20240101120100L,
            netCancelUrl = null
        )

        val cancelResponse = InicisCancelResponse(
            resultCode = "0000",
            resultMsg = "망취소 성공",
            tid = tid,
            cancelPrice = responseAmount,
            cancelTime = "20240101120200",
            timestamp = "20240101120200"
        )

        every {
            inicisPaymentClient.approvePayment(any(), any(), any(), any(), any(), any())
        } returns Mono.just(approvalResponse)
        
        every {
            inicisPaymentClient.cancelPayment(any(), any(), any(), any(), any(), any(), any(), any())
        } returns Mono.just(cancelResponse)

        // When & Then
        val exception = assertThrows<PaymentException> {
            paymentService.approvePayment(approvalRequest)
        }
        
        assertTrue(exception.message!!.contains("승인 응답값이 요청값과 상이하여 망취소 처리"))
        
        // 망취소 호출 확인
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

    @DisplayName("존재하지 않는 결제 정보로 승인 시 예외 발생")
    @Test
    fun `존재하지 않는 TID로 승인 요청하면 PaymentException이 발생한다`() {
        // Given
        val approvalRequest = PaymentApprovalRequest(
            authToken = "NONEXISTENT_TID",
            authUrl = "https://auth.test.com",
            mid = "test_merchant",
            orderNumber = "ORDER123"
        )

        // When & Then
        val exception = assertThrows<PaymentException> {
            paymentService.approvePayment(approvalRequest)
        }
        
        assertEquals("결제 정보를 찾을 수 없습니다", exception.message)
    }

    @DisplayName("주문번호 불일치 시 예외 발생")
    @Test
    fun `주문번호가 일치하지 않으면 PaymentException이 발생한다`() {
        // Given
        val tid = "TID123456"
        val originalOrderId = "ORDER123"
        val wrongOrderId = "WRONG_ORDER"
        
        // 인증 진행
        val authRequest = PaymentAuthRequest(
            orderId = originalOrderId,
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
            timestamp = 20240101120000L
        )

        every {
            inicisPaymentClient.authenticatePayment(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(),any(), any())
        } returns Mono.just(authResponse)

        paymentService.authenticatePayment(authRequest)

        // 잘못된 주문번호로 승인 요청
        val approvalRequest = PaymentApprovalRequest(
            authToken = tid,
            authUrl = "https://auth.test.com",
            mid = "test_merchant",
            orderNumber = wrongOrderId
        )

        // When & Then
        val exception = assertThrows<PaymentException> {
            paymentService.approvePayment(approvalRequest)
        }
        
        assertEquals("주문번호가 일치하지 않습니다", exception.message)
    }
}