package com.payment.apiserver.strategy

import com.payment.apiserver.client.InicisApprovalResponse
import com.payment.apiserver.client.InicisPaymentClient
import com.payment.apiserver.config.PaymentProperties
import com.payment.apiserver.dto.*
import com.payment.apiserver.entity.*
import com.payment.apiserver.exception.PaymentException
import com.payment.apiserver.repository.PaymentRequestRepository
import com.payment.apiserver.repository.PaymentRepository
import com.payment.apiserver.repository.OrderRepository
import com.payment.apiserver.dto.PaymentStatus
import com.payment.apiserver.service.PointService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Component
class InicisPaymentStrategy(
    private val inicisPaymentClient: InicisPaymentClient,
    private val paymentRequestRepository: PaymentRequestRepository,
    private val paymentRepository: PaymentRepository,
    private val orderRepository: OrderRepository,
    private val pointService: PointService,
    override val paymentProperties: PaymentProperties
) : PgPaymentStrategy() {

    private val logger = LoggerFactory.getLogger(InicisPaymentStrategy::class.java)
    override val providerName: String = "inicis"

    override fun callPgAuthenticate(
        provider: PaymentProperties.PgProvider,
        request: PaymentAuthRequest
    ): PgAuthResponse? {
        val response = inicisPaymentClient.authenticatePayment(
            apiUrl = provider.apiUrl,
            merchantId = provider.merchantId,
            apiKey = provider.apiKey,
            signKey = provider.signKey,
            hashKey = provider.hashKey,
            orderId = request.orderId,
            amount = request.amount,
            productName = request.productName,
            buyerName = request.buyerName,
            buyerEmail = request.buyerEmail,
            buyerTel = request.buyerTel,
            returnUrl = request.returnUrl,
            closeUrl = request.closeUrl
        ).block()

        return response?.let {
            PgAuthResponse(
                resultCode = it.resultCode,
                resultMsg = it.resultMsg,
                tid = it.tid,
                authUrl = it.authUrl
            )
        }
    }

    override fun callPgApprove(
        provider: PaymentProperties.PgProvider,
        tid: String,
        orderId: String
    ): PgApprovalResponse? {
        TODO("Not yet implemented")
    }

    override fun callPgCancel(
        provider: PaymentProperties.PgProvider,
        tid: String,
        reason: String,
        isNetworkCancel: Boolean
    ): PgCancelResponse? {
        val response = inicisPaymentClient.cancelPayment(
            apiUrl = provider.apiUrl,
            merchantId = provider.merchantId,
            apiKey = provider.apiKey,
            signKey = provider.signKey,
            hashKey = provider.hashKey,
            tid = tid,
            reason = reason,
            isNetworkCancel = isNetworkCancel
        ).block()

        return response?.let {
            PgCancelResponse(
                resultCode = it.resultCode,
                resultMsg = it.resultMsg,
                cancelledTid = tid,
                cancelDate = it.cancelTime,
                cancelTime = it.cancelTime
            )
        }
    }

    override fun createPrepareData(
        request: PaymentPrepareRequest,
        paymentId: String
    ): PaymentPrepareData {
        TODO("Not yet implemented")
    }

    @Transactional
    override fun cancelPayment(request: PaymentCancelRequest): PaymentCancelResponse {
        val provider = paymentProperties.pg.providers.find { it.name == providerName }
            ?: throw PaymentException("이니시스 설정을 찾을 수 없습니다")

        try {
            // 1. TID로 결제 정보 조회
            val payment = paymentRepository.findByPgTid(request.tid)
                ?: throw PaymentException("해당 TID로 결제 내역을 찾을 수 없습니다: ${request.tid}")

            if (payment.status != com.payment.apiserver.entity.PaymentStatus.SUCCESS) {
                throw PaymentException("취소 가능한 상태가 아닙니다. 현재 상태: ${payment.status}")
            }

            val isNetworkCancel = request.cancelType == CancelType.NETWORK

            // 2. PG 취소 요청
            val response = callPgCancel(provider, request.tid, request.reason, isNetworkCancel)
                ?: throw PaymentException("PG 취소 응답이 없습니다")

            // 이니시스 취소 API는 성공 시 "0000" 또는 "00" 리턴
            if (response.resultCode != "0000" && response.resultCode != "00") {
                throw PaymentException("PG 취소 실패: ${response.resultMsg ?: "알 수 없는 오류"}")
            }

            // 3. Payment 테이블 상태 업데이트
            val updatedPayment = Payment(
                id = payment.id,
                userId = payment.userId,
                order = payment.order,
                paymentPgProvider = PaymentPgProvider.INICIS,
                paymentMethod = payment.paymentMethod,
                amount = payment.amount,
                pointAmount = payment.pointAmount,
                cardAmount = payment.cardAmount,
                pgTid = payment.pgTid,
                status = com.payment.apiserver.entity.PaymentStatus.CANCELLED,
                createdAt = payment.createdAt
            )
            paymentRepository.save(updatedPayment)

            // 4. Order 테이블 상태 업데이트 및 포인트 환불
            val orderOptional = orderRepository.findById(payment.order.id)
            if (orderOptional.isPresent) {
                val order = orderOptional.get()

                // 포인트 환불 처리
                if (order.pointAmount != null && order.pointAmount > 0) {
                    pointService.refundPoints(payment.userId.toLong(), order.pointAmount)
                }

                val updatedOrder = Order(
                    id = order.id,
                    userId = order.userId,
                    productName = order.productName,
                    productPrice = order.productPrice,
                    totalAmount = order.totalAmount,
                    pointAmount = order.pointAmount,
                    cardAmount = order.cardAmount,
                    termsAgreed = order.termsAgreed,
                    status = OrderStatus.CANCELLED,
                    createdAt = order.createdAt,
                    updatedAt = LocalDateTime.now()
                )
                orderRepository.save(updatedOrder)
            }

            // 5. PaymentRequest 로그 저장
            val cancelRequest = PaymentRequest(
                userId = payment.userId,
                orderId = payment.order.id,
                requestType = if (isNetworkCancel) PaymentRequestType.NETWORK_CANCEL else PaymentRequestType.CANCEL,
                pgProvider = providerName,
                amount = payment.amount,
                requestData = "tid=${request.tid}, reason=${request.reason}, cancelType=${request.cancelType}",
                responseData = response.toString(),
                status = RequestStatus.SUCCESS
            )
            paymentRequestRepository.save(cancelRequest)

            // 포인트 환불 메시지 추가
            val pointRefundMessage = orderOptional.takeIf { it.isPresent }
                ?.get()?.pointAmount?.takeIf { it > 0 }
                ?.let { " (포인트 ${it}원 환불 완료)" } ?: ""

            return PaymentCancelResponse(
                success = true,
                message = "${if (isNetworkCancel) "망" else "승인"}취소가 완료되었습니다$pointRefundMessage",
                data = PaymentCancelData(
                    tid = response.cancelledTid,
                    orderId = payment.order.id,
                    cancelledAmount = payment.amount,
                    cancelledAt = getCurrentTimestamp(),
                    reason = request.reason,
                    cancelType = request.cancelType
                )
            )

        } catch (e: PaymentException) {
            // 실패 로그 저장
            val payment = paymentRepository.findByPgTid(request.tid)
            if (payment != null) {
                val isNetworkCancel = request.cancelType == CancelType.NETWORK
                val failedRequest = PaymentRequest(
                    userId = payment.userId,
                    orderId = payment.order.id,
                    requestType = if (isNetworkCancel) PaymentRequestType.NETWORK_CANCEL else PaymentRequestType.CANCEL,
                    pgProvider = providerName,
                    amount = payment.amount,
                    requestData = "tid=${request.tid}, reason=${request.reason}, cancelType=${request.cancelType}",
                    responseData = "Error: ${e.message}",
                    status = RequestStatus.FAILED
                )
                paymentRequestRepository.save(failedRequest)
            }
            throw e
        } catch (e: Exception) {
            throw PaymentException("결제 취소 중 시스템 오류가 발생했습니다: ${e.message}")
        }
    }
    
    private fun generateInicisApprovalSignature(
        authToken: String,
        timestamp: Long,
    ): String {
        val signData = "authToken=$authToken&timestamp=$timestamp"
        return generateSignature(signData)
    }
    
    private fun generateInicisVerification(
        authToken: String,
        signKey: String,
        timestamp: Long,
        ): String {
        val signData = "authToken=$authToken&signKey=$signKey&timestamp=$timestamp"
        return generateSignature(signData)
    }

    @Transactional
    override fun approvePayment(request: PaymentApprovalRequest): PaymentApprovalResponse {
        val provider = paymentProperties.pg.providers.find { it.name == providerName }
            ?: throw PaymentException("이니시스 설정을 찾을 수 없습니다")

        var pgApprovalResponse: InicisApprovalResponse? = null

        try {
            val timestamp = getCurrentTimestamp()
            val signature = generateInicisApprovalSignature(
                authToken = request.authToken,
                timestamp = timestamp.toLong(),
            )
            val verification = generateInicisVerification(
                authToken = request.authToken,
                signKey = provider.signKey,
                timestamp = timestamp.toLong(),)

            val response = inicisPaymentClient.approvePayment(
                authUrl = request.authUrl,
                authToken = request.authToken,
                timestamp = timestamp.toLong(),
                signature = signature,
                verification = verification,
                mid = request.mid,
            ).block()

            if (response == null) {
                throw PaymentException("이니시스 결제 승인 응답을 받지 못했습니다")
            }

            if (response.resultCode != "0000") {
                throw PaymentException("이니시스 결제 승인 실패: ${response.resultMsg}")
            }

            // PG 승인이 성공했으므로 응답 저장
            pgApprovalResponse = response

            // 주문 정보에서 사용자 ID 가져오기
            val order = orderRepository.findById(request.orderNumber)
                .orElseThrow { PaymentException("주문 정보를 찾을 수 없습니다: ${request.orderNumber}") }

            val paymentRequest = PaymentRequest(
                userId = order.userId,
                orderId = request.orderNumber,
                requestType = PaymentRequestType.APPROVAL,
                pgProvider = providerName,
                amount = response.TotPrice ?: 0L,
                requestData = "authToken=${request.authToken}, mid=${request.mid}, orderId=${request.orderNumber}",
                responseData = response.toString(),
                status = RequestStatus.SUCCESS
            )

            paymentRequestRepository.save(paymentRequest)

            // 결제 성공 시 Payment 테이블에 저장 (이 부분에서 예외가 발생할 수 있음)
            val paymentMethod = when(response.payMethod) {
                "CARD" -> PaymentMethod.CARD
                "POINT" -> PaymentMethod.POINT
                else -> PaymentMethod.CARD // 기본값
            }

            val payment = Payment(
                userId = order.userId,
                order = order,
                paymentPgProvider = PaymentPgProvider.INICIS,
                paymentMethod = paymentMethod,
                amount = response.TotPrice ?: 0L,
                pointAmount = if (paymentMethod == PaymentMethod.POINT) response.TotPrice else null,
                cardAmount = if (paymentMethod == PaymentMethod.CARD) response.TotPrice else null,
                pgTid = response.tid,
                status = com.payment.apiserver.entity.PaymentStatus.SUCCESS
            )

            val savedPayment = paymentRepository.save(payment)

            // Order 테이블 상태 업데이트 (이 부분에서도 예외가 발생할 수 있음)
            val orderOptional = orderRepository.findById(request.orderNumber)
            if (orderOptional.isPresent) {
                val order = orderOptional.get()
                val updatedOrder = Order(
                    id = order.id,
                    userId = order.userId,
                    productName = order.productName,
                    productPrice = order.productPrice,
                    totalAmount = order.totalAmount,
                    pointAmount = order.pointAmount,
                    cardAmount = order.cardAmount,
                    termsAgreed = order.termsAgreed,
                    status = OrderStatus.COMPLETED,
                    createdAt = order.createdAt,
                    updatedAt = LocalDateTime.now()
                )
                orderRepository.save(updatedOrder)
            }

            return PaymentApprovalResponse(
                success = true,
                message = "결제 승인이 완료되었습니다",
                data = PaymentApprovalData(
                    tid = response.tid,
                    paymentId = savedPayment.id,
                    orderId = response.MOID,
                    amount = response.TotPrice,
                    pgProvider = providerName,
                    paymentMethod = response.payMethod,
                    approvedAt = response.applTime,
                    status = PaymentStatus.APPROVED
                )
            )

        } catch (e: PaymentException) {
            // PG 승인이 성공했지만 후속 로직에서 실패한 경우 망취소 수행
            val netCancelUrl = pgApprovalResponse?.netCancelUrl
            if (pgApprovalResponse != null && netCancelUrl != null) {
                try {
                    performNetworkCancel(
                        netCancelUrl = netCancelUrl,
                        authToken = request.authToken,
                        provider = provider,
                        price = pgApprovalResponse.TotPrice
                    )
                } catch (cancelError: Exception) {
                    // 망취소 실패는 로그만 남기고 원래 예외를 던짐
                    logger.error("망취소 실패: ${cancelError.message}", cancelError)
                }
            }

            // 실패 시에도 주문 정보에서 사용자 ID 가져오기 시도
            val userId = try {
                orderRepository.findById(request.orderNumber).map { it.userId }.orElse(1L)
            } catch (ex: Exception) {
                1L // 주문 조회 실패 시 기본값
            }

            val paymentRequest = PaymentRequest(
                userId = userId,
                orderId = request.orderNumber,
                requestType = PaymentRequestType.APPROVAL,
                pgProvider = providerName,
                amount = 0L,
                requestData = "authToken=${request.authToken}, mid=${request.mid}, orderId=${request.orderNumber}",
                responseData = "Error: ${e.message}",
                status = RequestStatus.FAILED
            )
            paymentRequestRepository.save(paymentRequest)
            throw e
        } catch (e: Exception) {
            // PG 승인이 성공했지만 후속 로직에서 실패한 경우 망취소 수행
            val netCancelUrl = pgApprovalResponse?.netCancelUrl
            if (pgApprovalResponse != null && netCancelUrl != null) {
                try {
                    performNetworkCancel(
                        netCancelUrl = netCancelUrl,
                        authToken = request.authToken,
                        provider = provider,
                        price = pgApprovalResponse.TotPrice
                    )
                } catch (cancelError: Exception) {
                    // 망취소 실패는 로그만 남기고 원래 예외를 던짐
                    logger.error("망취소 실패: ${cancelError.message}", cancelError)
                }
            }

            // 실패 시에도 주문 정보에서 사용자 ID 가져오기 시도
            val userId = try {
                orderRepository.findById(request.orderNumber).map { it.userId }.orElse(1L)
            } catch (ex: Exception) {
                1L // 주문 조회 실패 시 기본값
            }

            val paymentRequest = PaymentRequest(
                userId = userId,
                orderId = request.orderNumber,
                requestType = PaymentRequestType.APPROVAL,
                pgProvider = providerName,
                amount = 0L,
                requestData = "authToken=${request.authToken}, mid=${request.mid}, orderId=${request.orderNumber}",
                responseData = "System Error: ${e.message}",
                status = RequestStatus.FAILED
            )
            paymentRequestRepository.save(paymentRequest)
            throw PaymentException("결제 승인 중 시스템 오류가 발생했습니다: ${e.message}")
        }
    }

    @Transactional
    override fun preparePayment(request: PaymentPrepareRequest): PaymentPrepareResponse {
        if (request.amount < paymentProperties.minimumAmount) {
            throw PaymentException("결제 금액이 최소 금액(${paymentProperties.minimumAmount}원)보다 작습니다")
        }

        val provider = paymentProperties.pg.providers.find { it.name == providerName }
            ?: throw PaymentException("이니시스 설정을 찾을 수 없습니다")

        try {
            // 1. 포인트 결제 처리 (포인트 사용 시)
            var actualPgAmount = request.finalPaymentAmount
            if (request.usePoints && request.pointsToUse > 0) {
                // 포인트 잔액 확인 및 차감
                pointService.validatePointUsage(
                    userId = request.userId,
                    pointsToUse = request.pointsToUse,
                    totalAmount = request.amount,
                    finalAmount = request.finalPaymentAmount
                )

                // 최소 PG 결제 금액 확인
                if (request.finalPaymentAmount < paymentProperties.minimumAmount) {
                    throw PaymentException("포인트 사용 후 남은 결제금액이 최소 금액(${paymentProperties.minimumAmount}원)보다 작습니다")
                }

                // 포인트 차감
                pointService.usePoints(request.userId, request.pointsToUse)
                actualPgAmount = request.finalPaymentAmount
            }

            // 2. 주문번호 생성
            val orderId = generateOrderId()

            val order = Order(
                id = orderId,
                userId = request.userId,
                productName = request.productName,
                productPrice = request.amount,
                totalAmount = request.amount,
                pointAmount = if (request.usePoints) request.pointsToUse else null,
                cardAmount = actualPgAmount,
                termsAgreed = true, // prepare 시점에서 이미 동의했다고 가정
                status = OrderStatus.PENDING
            )

            orderRepository.save(order)

            // 3. PG 인증 준비 (남은 금액으로)
            val signKey = provider.signKey
            val signatureData = "oid=${orderId}&price=${actualPgAmount}&timestamp=${request.timestamp}"
            val verificationData = "oid=${orderId}&price=${actualPgAmount}&signKey=${provider.signKey}&timestamp=${request.timestamp}"

            val signature = generateSignature(signatureData)
            val verification = generateSignature(verificationData)
            val mKey = generateSignature(signKey)

            val requestData = mapOf(
                "orderId" to orderId,
                "amount" to actualPgAmount, // 포인트 차감 후 실제 PG 결제 금액
                "productName" to request.productName,
                "buyerName" to request.buyerName,
                "buyerEmail" to request.buyerEmail,
                "buyerTel" to request.buyerTel,
                "timestamp" to request.timestamp,
                "signature" to signature,
                "verification" to verification,
                "mKey" to mKey,
                "pgProvider" to providerName,
                "merchantId" to provider.merchantId
            )

            // 4. PaymentRequest 로그 저장
            val paymentRequest = PaymentRequest(
                userId = request.userId,
                orderId = orderId,
                requestType = PaymentRequestType.AUTH,
                pgProvider = providerName,
                amount = actualPgAmount, // 실제 PG 결제 금액으로 로그 저장
                requestData = requestData.toString(),
                status = RequestStatus.PENDING
            )

            val savedPaymentRequest = paymentRequestRepository.save(paymentRequest)

            val returnUrl = "http://localhost:3000/api/payment/return"
            val closeUrl = "http://localhost:3000/payment/cancel"

            return PaymentPrepareResponse(
                success = true,
                message = if (request.usePoints && request.pointsToUse > 0)
                    "포인트 ${request.pointsToUse}원 차감 완료. PG 결제 준비가 완료되었습니다"
                else "결제 준비가 완료되었습니다",
                data = InicisPaymentPrepareData(
                    version = "1.0",
                    mid = provider.merchantId,
                    oid = orderId,
                    price = actualPgAmount.toString(), // 실제 PG 결제 금액
                    signature = signature,
                    verification = verification,
                    mKey = mKey,
                    returnUrl = returnUrl,
                    closeUrl = closeUrl,
                    timestamp = request.timestamp.toLong(),
                    goodname = request.productName,
                    buyername = request.buyerName,
                    buyeremail = request.buyerEmail,
                    buyertel = request.buyerTel,
                    paymentId = savedPaymentRequest.id.toString(),
                )
            )

        } catch (e: Exception) {
            // 포인트 차감이 이미 이루어진 경우 롤백 처리
            if (request.usePoints && request.pointsToUse > 0) {
                try {
                    pointService.refundPoints(request.userId, request.pointsToUse)
                } catch (rollbackError: Exception) {
                    // 롤백 실패 시 로그 기록 (실제로는 로거 사용)
                    println("포인트 롤백 실패: userId=${request.userId}, amount=${request.pointsToUse}, error=${rollbackError.message}")
                }
            }
            throw PaymentException("결제 준비 중 오류가 발생했습니다: ${e.message}")
        }
    }

    private fun generateOrderId(): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
        val randomSuffix = UUID.randomUUID().toString().substring(0, 8).uppercase()
        return "ORD${timestamp}${randomSuffix}"
    }

    private fun performNetworkCancel(
        netCancelUrl: String,
        authToken: String,
        provider: PaymentProperties.PgProvider,
        price: Long?
    ) {
        try {
            logger.info("망취소 요청 시작: netCancelUrl=$netCancelUrl, authToken=$authToken")

            val response = inicisPaymentClient.networkCancel(
                netCancelUrl = netCancelUrl,
                mid = provider.merchantId,
                authToken = authToken,
                signKey = provider.signKey,
                price = price
            ).block()

            if (response != null) {
                if (response.resultCode == "0000") {
                    logger.info("망취소 성공: ${response.resultMsg}")
                } else {
                    logger.error("망취소 실패: resultCode=${response.resultCode}, resultMsg=${response.resultMsg}")
                }
            } else {
                logger.error("망취소 응답이 null입니다")
            }
        } catch (e: Exception) {
            logger.error("망취소 중 예외 발생: ${e.message}", e)
            throw e
        }
    }
}