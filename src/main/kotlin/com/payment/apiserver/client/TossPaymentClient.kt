package com.payment.apiserver.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class TossPaymentClient(
    @Qualifier("pgWebClient") private val webClient: WebClient
) {

    fun authenticatePayment(
        apiUrl: String,
        merchantId: String,
        apiKey: String,
        signKey: String,
        hashKey: String,
        orderId: String,
        amount: Long,
        productName: String,
        buyerName: String,
        buyerEmail: String,
        buyerTel: String,
        returnUrl: String,
        closeUrl: String
    ): Mono<TossAuthResponse> {
        val requestBody = TossAuthRequest(
            mId = merchantId,
            version = "1.0",
            orderId = orderId,
            orderName = productName,
            amount = amount,
            customerName = buyerName,
            customerEmail = buyerEmail,
            customerMobilePhone = buyerTel,
            successUrl = returnUrl,
            failUrl = closeUrl,
            timestamp = getCurrentTimestamp()
        )

        return webClient.post()
            .uri("$apiUrl/v1/brandpay/payments/ready")
            .header("Authorization", "Basic $apiKey")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(TossAuthResponse::class.java)
            .onErrorReturn(
                TossAuthResponse(
                    code = "INTERNAL_SERVER_ERROR",
                    message = "PG 통신 오류",
                    paymentKey = "",
                    checkoutUrl = "",
                    timestamp = getCurrentTimestamp()
                )
            )
    }

    fun approvePayment(
        apiUrl: String,
        merchantId: String,
        apiKey: String,
        signKey: String,
        hashKey: String,
        tid: String,
        orderId: String
    ): Mono<TossApprovalResponse> {
        val requestBody = TossApprovalRequest(
            paymentKey = tid,
            orderId = orderId,
            amount = null // amount는 검증용이므로 null로 설정
        )

        return webClient.post()
            .uri("$apiUrl/v1/payments/$tid")
            .header("Authorization", "Basic $apiKey")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(TossApprovalResponse::class.java)
            .onErrorReturn(
                TossApprovalResponse(
                    paymentKey = tid,
                    orderId = orderId,
                    status = "FAILED",
                    totalAmount = 0,
                    method = "",
                    requestedAt = getCurrentTimestamp(),
                    approvedAt = getCurrentTimestamp()
                )
            )
    }

    fun cancelPayment(
        apiUrl: String,
        merchantId: String,
        apiKey: String,
        signKey: String,
        hashKey: String,
        tid: String,
        reason: String,
        isNetworkCancel: Boolean
    ): Mono<TossCancelResponse> {
        val requestBody = TossCancelRequest(
            cancelReason = reason
        )

        val endpoint = if (isNetworkCancel) "/cancel-auth" else "/cancel"
        
        return webClient.post()
            .uri("$apiUrl/v1/payments/$tid$endpoint")
            .header("Authorization", "Basic $apiKey")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(TossCancelResponse::class.java)
            .onErrorReturn(
                TossCancelResponse(
                    paymentKey = tid,
                    status = "CANCEL_FAILED",
                    cancels = emptyList(),
                    canceledAt = getCurrentTimestamp()
                )
            )
    }

    private fun getCurrentTimestamp(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
    }
}

// Toss API Request/Response DTOs
data class TossAuthRequest(
    val mId: String,
    val version: String,
    val orderId: String,
    val orderName: String,
    val amount: Long,
    val customerName: String,
    val customerEmail: String,
    val customerMobilePhone: String,
    val successUrl: String,
    val failUrl: String,
    val timestamp: String?
)

data class TossAuthResponse(
    val code: String,
    val message: String,
    val paymentKey: String,
    val checkoutUrl: String,
    val timestamp: String?
)

data class TossApprovalRequest(
    val paymentKey: String,
    val orderId: String,
    val amount: Long?
)

data class TossApprovalResponse(
    val paymentKey: String,
    val orderId: String,
    val status: String,
    val totalAmount: Long,
    val method: String,
    val requestedAt: String,
    val approvedAt: String
)

data class TossCancelRequest(
    val cancelReason: String
)

data class TossCancelResponse(
    val paymentKey: String,
    val status: String,
    val cancels: List<TossCancel>,
    val canceledAt: String
)

data class TossCancel(
    val cancelAmount: Long,
    val cancelReason: String,
    val canceledAt: String
)