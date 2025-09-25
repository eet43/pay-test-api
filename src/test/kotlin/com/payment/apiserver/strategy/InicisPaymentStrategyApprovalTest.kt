package com.payment.apiserver.strategy

import com.payment.apiserver.client.InicisApprovalResponse
import com.payment.apiserver.client.InicisNetworkCancelResponse
import com.payment.apiserver.client.InicisPaymentClient
import com.payment.apiserver.config.PaymentProperties
import com.payment.apiserver.dto.*
import com.payment.apiserver.entity.*
import com.payment.apiserver.exception.PaymentException
import com.payment.apiserver.repository.OrderRepository
import com.payment.apiserver.repository.PaymentRepository
import com.payment.apiserver.repository.PaymentRequestRepository
import com.payment.apiserver.dto.PaymentStatus
import com.payment.apiserver.service.PointService
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reactor.core.publisher.Mono
import org.junit.jupiter.api.Assertions.*
import java.time.LocalDateTime
import java.util.*

@DisplayName("InicisPaymentStrategy 승인 처리 테스트")
class InicisPaymentStrategyApprovalTest {

    private lateinit var strategy: InicisPaymentStrategy
    private lateinit var inicisPaymentClient: InicisPaymentClient
    private lateinit var paymentRequestRepository: PaymentRequestRepository
    private lateinit var paymentRepository: PaymentRepository
    private lateinit var orderRepository: OrderRepository
    private lateinit var pointService: PointService
    private lateinit var paymentProperties: PaymentProperties

    private val testProvider = PaymentProperties.PgProvider(
        name = "inicis",
        weight = 100,
        apiUrl = "https://test-inicis.com",
        merchantId = "test_merchant",
        apiKey = "test_key",
        signKey = "test_sign_key",
        hashKey = "test_hash_key"
    )

    private val testOrder = Order(
        id = "ORDER123",
        userId = 1L,
        productName = "테스트 상품",
        productPrice = 1000L,
        totalAmount = 1000L,
        pointAmount = null,
        cardAmount = 1000L,
        termsAgreed = true,
        status = OrderStatus.PENDING,
        createdAt = LocalDateTime.now()
    )

    @BeforeEach
    fun setup() {
        inicisPaymentClient = mockk()
        paymentRequestRepository = mockk()
        paymentRepository = mockk()
        orderRepository = mockk()
        pointService = mockk()

        paymentProperties = PaymentProperties(
            pg = PaymentProperties.PgProperties(listOf(testProvider)),
            minimumAmount = 100,
            cors = PaymentProperties.CorsProperties(listOf("http://localhost:3000"))
        )

        strategy = InicisPaymentStrategy(
            inicisPaymentClient,
            paymentRequestRepository,
            paymentRepository,
            orderRepository,
            pointService,
            paymentProperties
        )

        every { paymentRequestRepository.save(any()) } returns mockk()
        every { paymentRepository.save(any()) } returns mockk { every { id } returns 1L }
        every { orderRepository.save(any()) } returns mockk()
    }

    @Test
    @DisplayName("정상 승인 처리 - 모든 단계가 성공하면 PaymentApprovalResponse를 반환한다")
    fun `정상 승인 처리 성공`() {
        // Given
        val request = PaymentApprovalRequest(
            authToken = "AUTH_TOKEN_123",
            authUrl = "https://auth.inicis.com",
            mid = testProvider.merchantId,
            orderNumber = testOrder.id
        )

        val approvalResponse = InicisApprovalResponse(
            resultCode = "0000",
            resultMsg = "승인 성공",
            tid = "TID123456",
            mid = testProvider.merchantId,
            MOID = testOrder.id,
            TotPrice = 1000L,
            goodName = "테스트 상품",
            payMethod = "CARD",
            applDate = "20240101",
            applTime = "120000",
            EventCode = null,
            buyerName = "홍길동",
            buyerTel = "01012345678",
            buyerEmail = "test@test.com",
            custEmail = "test@test.com",
            timestamp = 20240101120000L,
            netCancelUrl = null
        )

        every { orderRepository.findById(testOrder.id) } returns Optional.of(testOrder)
        every {
            inicisPaymentClient.approvePayment(any(), any(), any(), any(), any(), any())
        } returns Mono.just(approvalResponse)

        // When
        val result = strategy.approvePayment(request)

        // Then
        assertTrue(result.success)
        assertEquals("결제 승인이 완료되었습니다", result.message)
        assertNotNull(result.data)
        assertEquals("TID123456", result.data?.tid)
        assertEquals(testOrder.id, result.data?.orderId)
        assertEquals(1000L, result.data?.amount)
        assertEquals("inicis", result.data?.pgProvider)
        assertEquals("CARD", result.data?.paymentMethod)
        assertEquals(PaymentStatus.APPROVED, result.data?.status)

        // DB 저장 호출 확인
        verify { paymentRequestRepository.save(any()) }
        verify { paymentRepository.save(any()) }
        verify { orderRepository.save(any()) }
    }

    @Test
    @DisplayName("PG 승인 실패 - PG에서 실패 응답을 받으면 예외가 발생한다")
    fun `PG 승인 실패시 예외 발생`() {
        // Given
        val request = PaymentApprovalRequest(
            authUrl = "https://auth.inicis.com",
            authToken = "AUTH_TOKEN_123",
            mid = testProvider.merchantId,
            orderNumber = testOrder.id
        )

        val failureResponse = InicisApprovalResponse(
            resultCode = "9999",
            resultMsg = "승인 실패",
            tid = null,
            mid = null,
            MOID = null,
            TotPrice = null,
            goodName = null,
            payMethod = "",
            applDate = null,
            applTime = null,
            EventCode = null,
            buyerName = null,
            buyerTel = null,
            buyerEmail = null,
            custEmail = null,
            timestamp = 20240101120000L,
            netCancelUrl = null
        )

        every { orderRepository.findById(testOrder.id) } returns Optional.of(testOrder)
        every {
            inicisPaymentClient.approvePayment(any(), any(), any(), any(), any(), any())
        } returns Mono.just(failureResponse)

        // When & Then
        val exception = assertThrows<PaymentException> {
            strategy.approvePayment(request)
        }

        assertTrue(exception.message!!.contains("이니시스 결제 승인 실패"))
        assertTrue(exception.message!!.contains("승인 실패"))

        // 실패 로그 저장 확인
        verify {
            paymentRequestRepository.save(match {
                it.status == RequestStatus.FAILED
            })
        }
    }

    @Test
    @DisplayName("PG 승인 응답 없음 - PG 클라이언트가 null을 반환하면 예외가 발생한다")
    fun `PG 승인 응답 없음시 예외 발생`() {
        // Given
        val request = PaymentApprovalRequest(
            authUrl = "https://auth.inicis.com",
            authToken = "AUTH_TOKEN_123",
            mid = testProvider.merchantId,
            orderNumber = testOrder.id
        )

        every { orderRepository.findById(testOrder.id) } returns Optional.of(testOrder)
        every {
            inicisPaymentClient.approvePayment(any(), any(), any(), any(), any(), any())
        } returns Mono.empty()

        // When & Then
        val exception = assertThrows<PaymentException> {
            strategy.approvePayment(request)
        }

        assertEquals("이니시스 결제 승인 응답을 받지 못했습니다", exception.message)

        verify {
            paymentRequestRepository.save(match {
                it.status == RequestStatus.FAILED
            })
        }
    }

    @Test
    @DisplayName("주문 정보 없음 - 존재하지 않는 주문번호로 요청하면 예외가 발생한다")
    fun `주문 정보 없음시 예외 발생`() {
        // Given
        val request = PaymentApprovalRequest(
            authUrl = "https://auth.inicis.com",
            authToken = "AUTH_TOKEN_123",
            mid = testProvider.merchantId,
            orderNumber = "NONEXISTENT_ORDER"
        )

        every { orderRepository.findById("NONEXISTENT_ORDER") } returns Optional.empty()

        // When & Then
        val exception = assertThrows<PaymentException> {
            strategy.approvePayment(request)
        }

        assertTrue(exception.message!!.contains("주문 정보를 찾을 수 없습니다"))
    }

    @Test
    @DisplayName("PG 승인 성공 후 Payment 저장 실패 - 망취소가 자동 실행된다")
    fun `PG승인성공후_Payment저장실패시_망취소실행`() {
        // Given
        val request = PaymentApprovalRequest(
            authUrl = "https://auth.inicis.com",
            authToken = "AUTH_TOKEN_123",
            mid = testProvider.merchantId,
            orderNumber = testOrder.id
        )

        val approvalResponse = InicisApprovalResponse(
            resultCode = "0000",
            resultMsg = "승인 성공",
            tid = "TID123456",
            mid = testProvider.merchantId,
            MOID = testOrder.id,
            TotPrice = 1000L,
            goodName = "테스트 상품",
            payMethod = "CARD",
            applDate = "20240101",
            applTime = "120000",
            EventCode = null,
            buyerName = "홍길동",
            buyerTel = "01012345678",
            buyerEmail = "test@test.com",
            custEmail = "test@test.com",
            timestamp = 20240101120000L,
            netCancelUrl = "https://netcancel.inicis.com/cancel"
        )

        val networkCancelResponse = InicisNetworkCancelResponse(
            resultCode = "0000",
            resultMsg = "망취소 성공",
            timestamp = System.currentTimeMillis()
        )

        every { orderRepository.findById(testOrder.id) } returns Optional.of(testOrder)
        every {
            inicisPaymentClient.approvePayment(any(), any(), any(), any(), any(), any())
        } returns Mono.just(approvalResponse)

        // Payment 저장 시 실패 시뮬레이션
        every { paymentRepository.save(any()) } throws RuntimeException("DB 저장 실패")

        // 망취소 모킹
        every {
            inicisPaymentClient.networkCancel(any(), any(), any(), any(), any())
        } returns Mono.just(networkCancelResponse)

        // When & Then
        val exception = assertThrows<PaymentException> {
            strategy.approvePayment(request)
        }

        assertTrue(exception.message!!.contains("결제 승인 중 시스템 오류가 발생했습니다"))

        // 망취소 호출 확인
        verify {
            inicisPaymentClient.networkCancel(
                netCancelUrl = "https://netcancel.inicis.com/cancel",
                mid = testProvider.merchantId,
                authToken = "AUTH_TOKEN_123",
                signKey = testProvider.signKey,
                price = 1000L
            )
        }

        // 실패 로그 저장 확인
        verify {
            paymentRequestRepository.save(match {
                it.status == RequestStatus.FAILED
            })
        }
    }

    @Test
    @DisplayName("PG 승인 성공 후 Order 업데이트 실패 - 망취소가 자동 실행된다")
    fun `PG승인성공후_Order업데이트실패시_망취소실행`() {
        // Given
        val request = PaymentApprovalRequest(
            authUrl = "https://auth.inicis.com",
            authToken = "AUTH_TOKEN_123",
            mid = testProvider.merchantId,
            orderNumber = testOrder.id
        )

        val approvalResponse = InicisApprovalResponse(
            resultCode = "0000",
            resultMsg = "승인 성공",
            tid = "TID123456",
            mid = testProvider.merchantId,
            MOID = testOrder.id,
            TotPrice = 1000L,
            goodName = "테스트 상품",
            payMethod = "CARD",
            applDate = "20240101",
            applTime = "120000",
            EventCode = null,
            buyerName = "홍길동",
            buyerTel = "01012345678",
            buyerEmail = "test@test.com",
            custEmail = "test@test.com",
            timestamp = 20240101120000L,
            netCancelUrl = "https://netcancel.inicis.com/cancel"
        )

        val networkCancelResponse = InicisNetworkCancelResponse(
            resultCode = "0000",
            resultMsg = "망취소 성공",
            timestamp = System.currentTimeMillis()
        )

        every { orderRepository.findById(testOrder.id) } returns Optional.of(testOrder)
        every {
            inicisPaymentClient.approvePayment(any(), any(), any(), any(), any(), any())
        } returns Mono.just(approvalResponse)

        // Payment 저장은 성공
        every { paymentRepository.save(any()) } returns mockk { every { id } returns 1L }

        // Order 업데이트 시 실패 시뮬레이션
        every { orderRepository.save(any()) } throws RuntimeException("Order 업데이트 실패")

        // 망취소 모킹
        every {
            inicisPaymentClient.networkCancel(any(), any(), any(), any(), any())
        } returns Mono.just(networkCancelResponse)

        // When & Then
        val exception = assertThrows<PaymentException> {
            strategy.approvePayment(request)
        }

        assertTrue(exception.message!!.contains("결제 승인 중 시스템 오류가 발생했습니다"))

        // 망취소 호출 확인
        verify {
            inicisPaymentClient.networkCancel(
                netCancelUrl = "https://netcancel.inicis.com/cancel",
                mid = testProvider.merchantId,
                authToken = "AUTH_TOKEN_123",
                signKey = testProvider.signKey,
                price = 1000L
            )
        }
    }

    @Test
    @DisplayName("PG 승인 성공 후 PaymentRequest 로그 저장 실패 - 망취소가 자동 실행된다")
    fun `PG승인성공후_PaymentRequest로그저장실패시_망취소실행`() {
        // Given
        val request = PaymentApprovalRequest(
            authUrl = "https://auth.inicis.com",
            authToken = "AUTH_TOKEN_123",
            mid = testProvider.merchantId,
            orderNumber = testOrder.id
        )

        val approvalResponse = InicisApprovalResponse(
            resultCode = "0000",
            resultMsg = "승인 성공",
            tid = "TID123456",
            mid = testProvider.merchantId,
            MOID = testOrder.id,
            TotPrice = 1000L,
            goodName = "테스트 상품",
            payMethod = "CARD",
            applDate = "20240101",
            applTime = "120000",
            EventCode = null,
            buyerName = "홍길동",
            buyerTel = "01012345678",
            buyerEmail = "test@test.com",
            custEmail = "test@test.com",
            timestamp = 20240101120000L,
            netCancelUrl = "https://netcancel.inicis.com/cancel"
        )

        val networkCancelResponse = InicisNetworkCancelResponse(
            resultCode = "0000",
            resultMsg = "망취소 성공",
            timestamp = System.currentTimeMillis()
        )

        every { orderRepository.findById(testOrder.id) } returns Optional.of(testOrder)
        every {
            inicisPaymentClient.approvePayment(any(), any(), any(), any(), any(), any())
        } returns Mono.just(approvalResponse)

        // PaymentRequest 로그 저장 시 실패 시뮬레이션
        every { paymentRequestRepository.save(any()) } throws RuntimeException("로그 저장 실패")

        // 망취소 모킹
        every {
            inicisPaymentClient.networkCancel(any(), any(), any(), any(), any())
        } returns Mono.just(networkCancelResponse)

        // When & Then
        val exception = assertThrows<PaymentException> {
            strategy.approvePayment(request)
        }

        assertTrue(exception.message!!.contains("결제 승인 중 시스템 오류가 발생했습니다"))

        // 망취소 호출 확인
        verify {
            inicisPaymentClient.networkCancel(
                netCancelUrl = "https://netcancel.inicis.com/cancel",
                mid = testProvider.merchantId,
                authToken = "AUTH_TOKEN_123",
                signKey = testProvider.signKey,
                price = 1000L
            )
        }
    }

    @Test
    @DisplayName("망취소 실패 - PG 승인 후 로직 실패 시 망취소도 실패하면 원래 예외를 던진다")
    fun `망취소실패시_원래예외유지`() {
        // Given
        val request = PaymentApprovalRequest(
            authUrl = "https://auth.inicis.com",
            authToken = "AUTH_TOKEN_123",
            mid = testProvider.merchantId,
            orderNumber = testOrder.id
        )

        val approvalResponse = InicisApprovalResponse(
            resultCode = "0000",
            resultMsg = "승인 성공",
            tid = "TID123456",
            mid = testProvider.merchantId,
            MOID = testOrder.id,
            TotPrice = 1000L,
            goodName = "테스트 상품",
            payMethod = "CARD",
            applDate = "20240101",
            applTime = "120000",
            EventCode = null,
            buyerName = "홍길동",
            buyerTel = "01012345678",
            buyerEmail = "test@test.com",
            custEmail = "test@test.com",
            timestamp = 20240101120000L,
            netCancelUrl = "https://netcancel.inicis.com/cancel"
        )

        every { orderRepository.findById(testOrder.id) } returns Optional.of(testOrder)
        every {
            inicisPaymentClient.approvePayment(any(), any(), any(), any(), any(), any())
        } returns Mono.just(approvalResponse)

        // PaymentRequest 저장 실패
        every { paymentRequestRepository.save(any()) } throws RuntimeException("DB 저장 실패")

        // 망취소도 실패
        every {
            inicisPaymentClient.networkCancel(any(), any(), any(), any(), any())
        } throws RuntimeException("망취소 통신 실패")

        // When & Then
        val exception = assertThrows<PaymentException> {
            strategy.approvePayment(request)
        }

        // 원래 예외 메시지가 유지되는지 확인
        assertTrue(exception.message!!.contains("결제 승인 중 시스템 오류가 발생했습니다"))
        assertTrue(exception.message!!.contains("DB 저장 실패"))

        // 망취소 시도는 했는지 확인
        verify {
            inicisPaymentClient.networkCancel(any(), any(), any(), any(), any())
        }
    }

    @Test
    @DisplayName("netCancelUrl이 없는 경우 - PG 응답에 netCancelUrl이 없으면 망취소를 시도하지 않는다")
    fun `netCancelUrl없으면_망취소시도안함`() {
        // Given
        val request = PaymentApprovalRequest(
            authUrl = "https://auth.inicis.com",
            authToken = "AUTH_TOKEN_123",
            mid = testProvider.merchantId,
            orderNumber = testOrder.id
        )

        val approvalResponse = InicisApprovalResponse(
            resultCode = "0000",
            resultMsg = "승인 성공",
            tid = "TID123456",
            mid = testProvider.merchantId,
            MOID = testOrder.id,
            TotPrice = 1000L,
            goodName = "테스트 상품",
            payMethod = "CARD",
            applDate = "20240101",
            applTime = "120000",
            EventCode = null,
            buyerName = "홍길동",
            buyerTel = "01012345678",
            buyerEmail = "test@test.com",
            custEmail = "test@test.com",
            timestamp = 20240101120000L,
            netCancelUrl = null // netCancelUrl이 없음
        )

        every { orderRepository.findById(testOrder.id) } returns Optional.of(testOrder)
        every {
            inicisPaymentClient.approvePayment(any(), any(), any(), any(), any(), any())
        } returns Mono.just(approvalResponse)

        // Payment 저장 실패
        every { paymentRepository.save(any()) } throws RuntimeException("DB 저장 실패")

        // When & Then
        val exception = assertThrows<PaymentException> {
            strategy.approvePayment(request)
        }

        assertTrue(exception.message!!.contains("결제 승인 중 시스템 오류가 발생했습니다"))

        // 망취소를 시도하지 않았는지 확인
        verify(exactly = 0) {
            inicisPaymentClient.networkCancel(any(), any(), any(), any(), any())
        }
    }

    @Test
    @DisplayName("PG Provider 설정 없음 - 이니시스 설정이 없으면 예외가 발생한다")
    fun `PG_Provider설정없으면_예외발생`() {
        // Given
        val emptyProperties = PaymentProperties(
            pg = PaymentProperties.PgProperties(emptyList()), // 빈 프로바이더 목록
            minimumAmount = 100,
            cors = PaymentProperties.CorsProperties(listOf("http://localhost:3000"))
        )

        val strategyWithoutProvider = InicisPaymentStrategy(
            inicisPaymentClient,
            paymentRequestRepository,
            paymentRepository,
            orderRepository,
            pointService,
            emptyProperties
        )

        val request = PaymentApprovalRequest(
            authUrl = "https://auth.inicis.com",
            authToken = "AUTH_TOKEN_123",
            mid = "test_merchant",
            orderNumber = testOrder.id
        )

        // When & Then
        val exception = assertThrows<PaymentException> {
            strategyWithoutProvider.approvePayment(request)
        }

        assertEquals("이니시스 설정을 찾을 수 없습니다", exception.message)
    }
}