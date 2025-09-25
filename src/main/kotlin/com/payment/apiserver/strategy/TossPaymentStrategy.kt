//package com.payment.apiserver.strategy
//
//import com.payment.apiserver.client.TossPaymentClient
//import com.payment.apiserver.config.PaymentProperties
//import com.payment.apiserver.dto.*
//import com.payment.apiserver.exception.PaymentException
//import com.payment.apiserver.service.PaymentStatus
//import com.payment.apiserver.service.PaymentTransaction
//import org.springframework.stereotype.Component
//import java.time.LocalDateTime
//
//@Component
//class TossPaymentStrategy(
//    private val tossPaymentClient: TossPaymentClient,
//    override val paymentProperties: PaymentProperties
//) : PgPaymentStrategy() {
//
//    override val providerName: String = "toss"
//
//    override fun callPgAuthenticate(
//        provider: PaymentProperties.PgProvider,
//        request: PaymentAuthRequest
//    ): PgAuthResponse? {
//        val response = tossPaymentClient.authenticatePayment(
//            apiUrl = provider.apiUrl,
//            merchantId = provider.merchantId,
//            apiKey = provider.apiKey,
//            signKey = provider.signKey,
//            hashKey = provider.hashKey,
//            orderId = request.orderId,
//            amount = request.amount,
//            productName = request.productName,
//            buyerName = request.buyerName,
//            buyerEmail = request.buyerEmail,
//            buyerTel = request.buyerTel,
//            returnUrl = request.returnUrl,
//            closeUrl = request.closeUrl
//        ).block()
//
//        return response?.let {
//            if (it.code == "SUCCESS" || it.code.isBlank()) {
//                PgAuthResponse(
//                    resultCode = "0000",
//                    resultMsg = it.message,
//                    tid = it.paymentKey,
//                    authUrl = it.checkoutUrl
//                )
//            } else {
//                PgAuthResponse(
//                    resultCode = it.code,
//                    resultMsg = it.message,
//                    tid = it.paymentKey,
//                    authUrl = it.checkoutUrl
//                )
//            }
//        }
//    }
//
//    override fun callPgApprove(
//        provider: PaymentProperties.PgProvider,
//        tid: String,
//        orderId: String
//    ): PgApprovalResponse? {
//        val response = tossPaymentClient.approvePayment(
//            apiUrl = provider.apiUrl,
//            merchantId = provider.merchantId,
//            apiKey = provider.apiKey,
//            signKey = provider.signKey,
//            hashKey = provider.hashKey,
//            tid = tid,
//            orderId = orderId
//        ).block()
//
//        return response?.let {
//            val isSuccess = it.status == "DONE" || it.status == "SUCCESS"
//            PgApprovalResponse(
//                resultCode = if (isSuccess) "0000" else "9999",
//                resultMsg = if (isSuccess) "승인완료" else "승인실패",
//                tid = it.paymentKey,
//                orderId = it.orderId,
//                price = it.totalAmount,
//                paymentMethod = it.method,
//                approvedAt = it.approvedAt
//            )
//        }
//    }
//
//    override fun callPgCancel(
//        provider: PaymentProperties.PgProvider,
//        tid: String,
//        reason: String,
//        isNetworkCancel: Boolean
//    ): PgCancelResponse? {
//        val response = tossPaymentClient.cancelPayment(
//            apiUrl = provider.apiUrl,
//            merchantId = provider.merchantId,
//            apiKey = provider.apiKey,
//            signKey = provider.signKey,
//            hashKey = provider.hashKey,
//            tid = tid,
//            reason = reason,
//            isNetworkCancel = isNetworkCancel
//        ).block()
//
//        return response?.let {
//            val isSuccess = it.status.contains("CANCEL")
//            val cancelAmount = it.cancels.firstOrNull()?.cancelAmount ?: 0L
//            PgCancelResponse(
//                resultCode = if (isSuccess) "0000" else "9999",
//                resultMsg = if (isSuccess) "취소완료" else "취소실패",
//                tid = it.paymentKey,
//                cancelPrice = cancelAmount,
//                cancelTime = it.canceledAt
//            )
//        }
//    }
//
//    override fun createPrepareData(
//        request: PaymentPrepareRequest,
//        paymentId: String
//    ): PaymentPrepareData {
//        val provider = paymentProperties.pg.providers.find { it.name == providerName }
//            ?: throw PaymentException("토스페이먼츠 설정을 찾을 수 없습니다")
//
//        return TossPaymentPrepareData(
//            paymentId = paymentId,
//            orderId = request.orderId,
//            amount = request.amount,
//            pointsUsed = request.pointsToUse,
//            finalPaymentAmount = request.finalPaymentAmount,
//            pgProvider = providerName,
//            timestamp = getCurrentTimestamp(),
//            status = PaymentStatus.PREPARED,
//            mId = provider.merchantId,
//            orderName = request.productName,
//            customerName = request.buyerName,
//            customerEmail = request.buyerEmail,
//            customerMobilePhone = request.buyerTel,
//            successUrl = getSuccessUrl(),
//            failUrl = getFailUrl(),
//            apiKey = provider.apiKey
//        )
//    }
//
//    private fun getSuccessUrl(): String {
//        return "https://your-domain.com/payments/success"
//    }
//
//    private fun getFailUrl(): String {
//        return "https://your-domain.com/payments/fail"
//    }
//
//    override fun handlePaymentReturn(request: PaymentReturnRequest): PaymentReturnResponse {
//        val orderId = request.oid ?: throw PaymentException("주문번호가 없습니다")
//
//        val transaction = orderStorage[orderId]
//            ?: throw PaymentException("주문 정보를 찾을 수 없습니다")
//
//        val provider = paymentProperties.pg.providers.find { it.name == providerName }
//            ?: throw PaymentException("토스페이먼츠 설정을 찾을 수 없습니다")
//
//        try {
//            if (request.resultCode != "0000" && request.resultCode != "SUCCESS") {
//                val updatedTransaction = transaction.copy(
//                    status = PaymentStatus.FAILED,
//                    cancelledAt = LocalDateTime.now()
//                )
//                orderStorage[orderId] = updatedTransaction
//                throw PaymentException("결제 인증 실패: ${request.resultMsg ?: "알 수 없는 오류"}")
//            }
//
//            val authToken = request.authToken ?: throw PaymentException("인증 토큰이 없습니다")
//
//            val approvalResponse = tossPaymentClient.approvePayment(
//                apiUrl = provider.apiUrl,
//                merchantId = provider.merchantId,
//                apiKey = provider.apiKey,
//                signKey = provider.signKey,
//                hashKey = provider.hashKey,
//                tid = authToken,
//                orderId = orderId
//            ).block()
//
//            if (approvalResponse == null || (approvalResponse.status != "DONE" && approvalResponse.status != "SUCCESS")) {
//                throw PaymentException("결제 승인 실패: 토스페이먼츠 승인 오류")
//            }
//
//            if (approvalResponse.totalAmount != transaction.amount) {
//                try {
//                    tossPaymentClient.cancelPayment(
//                        apiUrl = provider.apiUrl,
//                        merchantId = provider.merchantId,
//                        apiKey = provider.apiKey,
//                        signKey = provider.signKey,
//                        hashKey = provider.hashKey,
//                        tid = approvalResponse.paymentKey,
//                        reason = "금액 불일치로 인한 자동 망취소",
//                        isNetworkCancel = true
//                    ).block()
//                } catch (e: Exception) {
//                    println("자동 망취소 실패: ${e.message}")
//                }
//                throw PaymentException("결제 금액이 일치하지 않아 자동 망취소 처리되었습니다")
//            }
//
//            val completedTransaction = transaction.copy(
//                tid = approvalResponse.paymentKey,
//                status = PaymentStatus.APPROVED,
//                approvedAt = LocalDateTime.now()
//            )
//
//            orderStorage[orderId] = completedTransaction
//            paymentStorage[approvalResponse.paymentKey] = completedTransaction
//
//            return PaymentReturnResponse(
//                success = true,
//                message = "결제가 성공적으로 완료되었습니다",
//                data = PaymentReturnData(
//                    orderId = orderId,
//                    paymentId = approvalResponse.paymentKey,
//                    amount = approvalResponse.totalAmount,
//                    status = PaymentStatus.APPROVED
//                )
//            )
//
//        } catch (e: PaymentException) {
//            throw e
//        } catch (e: Exception) {
//            throw PaymentException("결제 처리 중 시스템 오류가 발생했습니다: ${e.message}")
//        }
//    }
//}