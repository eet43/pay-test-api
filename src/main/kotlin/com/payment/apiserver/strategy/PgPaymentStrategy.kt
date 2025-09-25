package com.payment.apiserver.strategy

import com.payment.apiserver.config.PaymentProperties
import com.payment.apiserver.dto.*
import com.payment.apiserver.exception.PaymentException
import com.payment.apiserver.service.PaymentTransaction
import com.payment.apiserver.service.PaymentStatus
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

abstract class PgPaymentStrategy : PaymentStrategy {
    
    protected val paymentStorage = ConcurrentHashMap<String, PaymentTransaction>()
    protected val orderStorage = ConcurrentHashMap<String, PaymentTransaction>()

    abstract val providerName: String
    abstract val paymentProperties: PaymentProperties

    protected abstract fun callPgAuthenticate(
        provider: PaymentProperties.PgProvider,
        request: PaymentAuthRequest
    ): PgAuthResponse?
    
    protected abstract fun callPgApprove(
        provider: PaymentProperties.PgProvider,
        tid: String,
        orderId: String
    ): PgApprovalResponse?

    protected abstract fun callPgCancel(
        provider: PaymentProperties.PgProvider,
        tid: String,
        reason: String,
        isNetworkCancel: Boolean
    ): PgCancelResponse?

    protected abstract fun createPrepareData(
        request: PaymentPrepareRequest,
        paymentId: String
    ): PaymentPrepareData

    override fun getPaymentType(): PaymentType = PaymentType.PG

    override fun authenticatePayment(request: PaymentAuthRequest): PaymentAuthResponse {
        if (request.amount < paymentProperties.minimumAmount) {
            throw PaymentException("결제 금액이 최소 금액(${paymentProperties.minimumAmount}원)보다 작습니다")
        }

        val provider = getProviderConfig()

        try {
            val response = callPgAuthenticate(provider, request)
                ?: throw PaymentException("PG 응답이 없습니다")

            if (response.resultCode != "0000") {
                throw PaymentException("PG 인증 실패: ${response.resultMsg ?: "알 수 없는 오류"}")
            }

            val transaction = PaymentTransaction(
                tid = response.tid,
                orderId = request.orderId,
                amount = request.amount,
                pgProvider = providerName,
                status = PaymentStatus.APPROVED,
                createdAt = LocalDateTime.now()
            )
            paymentStorage[response.tid] = transaction

            return PaymentAuthResponse(
                success = true,
                message = "결제 인증이 완료되었습니다",
                data = PaymentAuthData(
                    tid = response.tid,
                    orderId = request.orderId,
                    amount = request.amount,
                    pgProvider = providerName,
                    authUrl = response.authUrl,
                    timestamp = getCurrentTimestamp()
                )
            )

        } catch (e: PaymentException) {
            throw e
        } catch (e: Exception) {
            throw PaymentException("결제 인증 중 시스템 오류가 발생했습니다: ${e.message}")
        }
    }

    override fun approvePayment(request: PaymentApprovalRequest): PaymentApprovalResponse {
        val transaction = orderStorage[request.orderNumber]
            ?: throw PaymentException("주문 정보를 찾을 수 없습니다")

        val provider = getProviderConfig()

        try {
            val response = callPgApprove(provider, request.authToken, request.orderNumber)
                ?: throw PaymentException("PG 응답이 없습니다")

            if (response.resultCode != "0000") {
                throw PaymentException("PG 승인 실패: ${response.resultMsg ?: "알 수 없는 오류"}")
            }

            if (response.price != transaction.amount) {
                try {
                    callPgCancel(provider, request.authToken, "승인 응답값 불일치로 인한 자동 망취소", true)
                } catch (e: Exception) {
                    println("망취소 실패: ${e.message}")
                }
                throw PaymentException("승인 응답값이 요청값과 상이하여 망취소 처리되었습니다")
            }

            val updatedTransaction = transaction.copy(
                status = PaymentStatus.APPROVED,
                approvedAt = LocalDateTime.now()
            )
            paymentStorage[request.authToken] = updatedTransaction
            orderStorage[request.orderNumber] = updatedTransaction

            return PaymentApprovalResponse(
                success = true,
                message = "결제 승인이 완료되었습니다",
                data = PaymentApprovalData(
                    tid = response.tid,
                    orderId = response.orderId,
                    amount = response.price,
                    pgProvider = providerName,
                    paymentMethod = response.paymentMethod,
                    approvedAt = response.approvedAt,
                    status = com.payment.apiserver.dto.PaymentStatus.APPROVED
                )
            )

        } catch (e: PaymentException) {
            throw e
        } catch (e: Exception) {
            throw PaymentException("결제 승인 중 시스템 오류가 발생했습니다: ${e.message}")
        }
    }

    override fun preparePayment(request: PaymentPrepareRequest): PaymentPrepareResponse {
        if (request.finalPaymentAmount < paymentProperties.minimumAmount) {
            throw PaymentException("결제 금액이 최소 금액(${paymentProperties.minimumAmount}원)보다 작습니다")
        }

        try {
            val paymentId = generatePaymentId()
            
            val transaction = PaymentTransaction(
                tid = paymentId,
                orderId = "1",
                amount = request.finalPaymentAmount,
                pgProvider = providerName,
                status = PaymentStatus.PREPARED,
                createdAt = LocalDateTime.now()
            )
            
            paymentStorage[paymentId] = transaction
            orderStorage["1"] = transaction

            val prepareData = createPrepareData(request, paymentId)
            
            return PaymentPrepareResponse(
                success = true,
                message = "결제 준비가 완료되었습니다",
                data = prepareData
            )

        } catch (e: PaymentException) {
            throw e
        } catch (e: Exception) {
            throw PaymentException("결제 준비 중 시스템 오류가 발생했습니다: ${e.message}")
        }
    }

    override fun handlePaymentReturn(request: PaymentReturnRequest): PaymentReturnResponse {
        try {
            return PaymentReturnResponse(
                success = true,
                message = "결제 결과 처리가 완료되었습니다",
                data = PaymentReturnData(
                    resultCode = request.resultCode ?: "",
                    resultMsg = request.resultMsg ?: "",
                    orderId = request.oid ?: "",
                    amount = request.price?.toLongOrNull() ?: 0L,
                    timestamp = getCurrentTimestamp()
                )
            )
        } catch (e: Exception) {
            throw PaymentException("결제 결과 처리 중 오류가 발생했습니다: ${e.message}")
        }
    }

    private fun generatePaymentId(): String {
        return "${providerName.uppercase()}_${System.currentTimeMillis()}"
    }

    private fun getProviderConfig(): PaymentProperties.PgProvider {
        return paymentProperties.pg.providers.find { it.name == providerName }
            ?: throw PaymentException("PG 설정을 찾을 수 없습니다: $providerName")
    }

    protected fun generateSignature(data: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    protected fun getCurrentTimestamp(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
    }
}

data class PgAuthResponse(
    val resultCode: String,
    val resultMsg: String?,
    val tid: String,
    val authUrl: String
)

data class PgApprovalResponse(
    val resultCode: String,
    val resultMsg: String?,
    val tid: String,
    val orderId: String,
    val price: Long,
    val paymentMethod: String,
    val approvedAt: String
)

data class PgCancelResponse(
    val resultCode: String,
    val resultMsg: String?,
    val cancelledTid: String,
    val timestamp: String? = null,
    val cancelDate: String? = null,
    val cancelTime: String? = null,
    val cshrCancelNum: String? = null,
    val detailResultCode: String? = null,
    val receiptInfo: String? = null
)