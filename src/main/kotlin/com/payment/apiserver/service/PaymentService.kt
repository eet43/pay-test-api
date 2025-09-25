package com.payment.apiserver.service

import com.payment.apiserver.client.InicisPaymentClient
import com.payment.apiserver.config.PaymentProperties
import com.payment.apiserver.dto.*
import com.payment.apiserver.exception.PaymentException
import com.payment.apiserver.service.PaymentTransaction
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class PaymentService(
    private val inicisPaymentClient: InicisPaymentClient,
    private val pgProviderService: PgProviderService,
    private val paymentProperties: PaymentProperties
) {
    private val paymentTransactions = ConcurrentHashMap<String, PaymentTransaction>()

    fun authenticatePayment(request: PaymentAuthRequest): PaymentAuthResponse {
        // Validate minimum amount
        if (request.amount < paymentProperties.minimumAmount) {
            throw PaymentException("최소 금액 ${paymentProperties.minimumAmount}원 이상 결제 가능합니다")
        }

        val provider = pgProviderService.selectProvider()

        try {
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
                returnUrl = "http://localhost:3000/return",
                closeUrl = "http://localhost:3000/close"
            ).block()

            if (response == null || response.resultCode != "0000") {
                throw PaymentException("PG 인증 실패: ${response?.resultMsg ?: "응답 없음"}")
            }

            // Store payment transaction for approval
            paymentTransactions[response.tid] = PaymentTransaction(
                tid = response.tid,
                orderId = request.orderId,
                amount = request.amount,
                pgProvider = provider.name,
                status = com.payment.apiserver.service.PaymentStatus.PENDING,
                createdAt = java.time.LocalDateTime.now()
            )

            return PaymentAuthResponse(
                success = true,
                message = "결제 인증이 완료되었습니다",
                data = PaymentAuthData(
                    tid = response.tid,
                    orderId = request.orderId,
                    amount = request.amount,
                    pgProvider = provider.name,
                    authUrl = response.authUrl,
                    timestamp = response.timestamp?.toString() ?: ""
                )
            )
        } catch (e: Exception) {
            throw PaymentException("결제 인증 중 시스템 오류가 발생했습니다: ${e.message}")
        }
    }

    fun approvePayment(request: PaymentApprovalRequest): PaymentApprovalResponse {
        val transaction = paymentTransactions[request.authToken]
            ?: throw PaymentException("결제 정보를 찾을 수 없습니다")

        if (transaction.orderId != request.orderNumber) {
            throw PaymentException("주문번호가 일치하지 않습니다")
        }

        val provider = pgProviderService.getProviderByName(transaction.pgProvider)
            ?: throw PaymentException("PG 프로바이더를 찾을 수 없습니다")

        try {
            val response = inicisPaymentClient.approvePayment(
                authUrl = "https://auth.inicis.com",
                authToken = request.authToken,
                timestamp = System.currentTimeMillis(),
                signature = "test_signature",
                verification = "test_verification",
                mid = provider.merchantId
            ).block()

            if (response == null) {
                throw PaymentException("PG 승인 응답을 받지 못했습니다")
            }

            if (response.resultCode != "0000") {
                throw PaymentException("PG 승인 실패: ${response.resultMsg}")
            }

            // Check response amount matches request
            if (response.TotPrice != null && response.TotPrice != transaction.amount) {
                // Perform network cancel
                try {
                    inicisPaymentClient.cancelPayment(
                        apiUrl = provider.apiUrl,
                        merchantId = provider.merchantId,
                        apiKey = provider.apiKey,
                        signKey = provider.signKey,
                        hashKey = provider.hashKey,
                        tid = request.authToken,
                        reason = "승인 응답값 불일치로 인한 자동 망취소",
                        isNetworkCancel = true
                    ).block()
                } catch (cancelException: Exception) {
                    // Log cancel error but still throw original validation error
                }

                throw PaymentException("승인 응답값이 요청값과 상이하여 망취소 처리되었습니다 (요청: ${transaction.amount}, 응답: ${response.TotPrice})")
            }

            return PaymentApprovalResponse(
                success = true,
                message = "결제 승인이 완료되었습니다",
                data = PaymentApprovalData(
                    tid = response.tid,
                    orderId = response.MOID,
                    amount = response.TotPrice,
                    pgProvider = transaction.pgProvider,
                    paymentMethod = response.payMethod,
                    approvedAt = response.applTime,
                    status = com.payment.apiserver.dto.PaymentStatus.APPROVED
                )
            )
        } catch (e: PaymentException) {
            throw e
        } catch (e: Exception) {
            throw PaymentException("결제 승인 중 시스템 오류가 발생했습니다: ${e.message}")
        }
    }

    fun cancelPayment(request: PaymentCancelRequest): PaymentCancelResponse {
        val transaction = paymentTransactions[request.tid]
            ?: throw PaymentException("결제 정보를 찾을 수 없습니다")

        val provider = pgProviderService.getProviderByName(transaction.pgProvider)
            ?: throw PaymentException("PG 프로바이더를 찾을 수 없습니다")

        try {
            val isNetworkCancel = request.cancelType == CancelType.NETWORK

            // Using the refund API for cancellation
            val response = inicisPaymentClient.cancelPayment(
                apiUrl = provider.apiUrl,
                merchantId = provider.merchantId,
                apiKey = provider.apiKey,
                signKey = provider.signKey,
                hashKey = provider.hashKey,
                tid = request.tid,
                reason = request.reason,
                isNetworkCancel = isNetworkCancel
            ).block()

            if (response == null || response.resultCode != "0000") {
                throw PaymentException("PG 취소 실패: ${response?.resultMsg ?: "응답 없음"}")
            }

            val message = if (isNetworkCancel) "망취소가 완료되었습니다" else "승인취소가 완료되었습니다"

            return PaymentCancelResponse(
                success = true,
                message = message,
                data = PaymentCancelData(
                    tid = request.tid,
                    orderId = transaction.orderId,
                    cancelledAmount = transaction.amount,
                    cancelledAt = response.timestamp ?: "",
                    reason = request.reason,
                    cancelType = request.cancelType
                )
            )
        } catch (e: PaymentException) {
            throw e
        } catch (e: Exception) {
            throw PaymentException("결제 취소 중 시스템 오류가 발생했습니다: ${e.message}")
        }
    }
}

