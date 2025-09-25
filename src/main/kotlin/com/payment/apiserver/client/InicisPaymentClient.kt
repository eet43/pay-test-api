package com.payment.apiserver.client

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import reactor.core.publisher.Mono
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import com.fasterxml.jackson.databind.ObjectMapper

@Component
class InicisPaymentClient(
    @Qualifier("pgWebClient") private val webClient: WebClient
) {
    private val logger = LoggerFactory.getLogger(InicisPaymentClient::class.java)
    private val objectMapper = ObjectMapper()

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
    ): Mono<InicisAuthResponse> {
        val requestBody = InicisAuthRequest(
            mid = merchantId,
            orderid = orderId,
            price = amount,
            goodname = productName,
            buyername = buyerName,
            buyeremail = buyerEmail,
            buyertel = buyerTel,
            returnurl = returnUrl,
            closeurl = closeUrl,
            signKey = signKey,
            hashKey = hashKey,
            timestamp = getCurrentTimestamp()
        )

        return webClient.post()
            .uri("$apiUrl/auth")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(InicisAuthResponse::class.java)
            .onErrorReturn(
                InicisAuthResponse(
                    resultCode = "9999",
                    resultMsg = "PG 통신 오류",
                    tid = "",
                    authUrl = "",
                    timestamp = getCurrentTimestamp()
                )
            )
    }

    fun approvePayment(
        authUrl: String,
        authToken: String,
        timestamp: Long,
        signature: String,
        verification: String,
        mid: String,
    ): Mono<InicisApprovalResponse> {
        val formData: MultiValueMap<String, String> = LinkedMultiValueMap()
        formData.add("mid", mid)
        formData.add("authToken", authToken)
        formData.add("timestamp", timestamp.toString())
        formData.add("signature", signature)
        formData.add("verification", verification)
        formData.add("charset", "UTF-8")
        formData.add("format", "JSON")

        return webClient.post()
            .uri(authUrl)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .bodyToMono(InicisApprovalResponse::class.java)
            .onErrorResume { error ->
                logger.error("Inicis approval payment error: ${error.message}", error)
                Mono.just(
                    InicisApprovalResponse(
                        resultCode = "9999",
                        resultMsg = "PG 승인 통신 오류: ${error.message}",
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
                        timestamp = getCurrentTimestamp(),
                        netCancelUrl = null
                    )
                )
            }
    }

    fun cancelPayment(
        apiUrl: String,
        merchantId: String,
        apiKey: String,
        signKey: String,
        hashKey: String,
        tid: String,
        reason: String,
        isNetworkCancel: Boolean = false
    ): Mono<InicisCancelResponse> {
        val timestamp = getCurrentTimestampString()
        val type = "refund"

        val data = InicisRefundData(
            tid = tid,
            msg = reason
        )

        val dataJson = objectMapper.writeValueAsString(data)
        val hashData = generateSHA512Hash(apiKey + merchantId + type + timestamp + dataJson)

        val requestBody = InicisRefundRequest(
            mid = merchantId,
            type = type,
            timestamp = timestamp,
            clientIp = "127.0.0.1",
            hashData = hashData,
            data = data
        )

        return webClient.post()
            .uri("https://iniapi.inicis.com/v2/pg/refund")
            .header("Content-Type", "application/json")
            .bodyValue(requestBody)
            .retrieve()
            .bodyToMono(InicisRefundResponse::class.java)
            .map { refundResponse ->
                // Convert InicisRefundResponse to InicisCancelResponse
                InicisCancelResponse(
                    resultCode = refundResponse.resultCode,
                    resultMsg = refundResponse.resultMsg,
                    tid = tid,
                    cancelPrice = 0L, // This would need to be calculated from the original request
                    cancelTime = refundResponse.cancelTime ?: timestamp,
                    timestamp = refundResponse.timestamp
                )
            }
            .onErrorResume { error ->
                logger.error("Inicis refund error: ${error.message}", error)
                Mono.just(
                    InicisCancelResponse(
                        resultCode = "9999",
                        resultMsg = "PG 취소 통신 오류: ${error.message}",
                        tid = tid,
                        cancelPrice = 0L,
                        cancelTime = timestamp,
                        timestamp = timestamp
                    )
                )
            }
    }

    fun networkCancel(
        netCancelUrl: String,
        mid: String,
        authToken: String,
        signKey: String,
        price: Long? = null
    ): Mono<InicisNetworkCancelResponse> {
        val timestamp = System.currentTimeMillis()
        val signature = generateSHA256Hash("authToken=$authToken&timestamp=$timestamp")
        val verification = generateSHA256Hash("authToken=$authToken&signKey=$signKey&timestamp=$timestamp")

        val formData: MultiValueMap<String, String> = LinkedMultiValueMap()
        formData.add("mid", mid)
        formData.add("authToken", authToken)
        formData.add("timestamp", timestamp.toString())
        formData.add("signature", signature)
        formData.add("verification", verification)
        formData.add("charset", "UTF-8")
        formData.add("format", "JSON")

        price?.let { formData.add("price", it.toString()) }

        return webClient.post()
            .uri(netCancelUrl)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .body(BodyInserters.fromFormData(formData))
            .retrieve()
            .bodyToMono(InicisNetworkCancelResponse::class.java)
            .onErrorResume { error ->
                logger.error("Inicis network cancel error: ${error.message}", error)
                Mono.just(
                    InicisNetworkCancelResponse(
                        resultCode = "9999",
                        resultMsg = "망취소 통신 오류: ${error.message}",
                        timestamp = timestamp
                    )
                )
            }
    }

    private fun getCurrentTimestamp(): Long {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")).toLong()
    }

    private fun getCurrentTimestampString(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
    }

    private fun generateSHA512Hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-512")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateSHA256Hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

// Inicis API Request/Response DTOs
data class InicisAuthRequest(
    val mid: String,
    val orderid: String,
    val price: Long,
    val goodname: String,
    val buyername: String,
    val buyeremail: String,
    val buyertel: String,
    val returnurl: String,
    val closeurl: String,
    val signKey: String,
    val hashKey: String,
    val timestamp: Long?
)

data class InicisAuthResponse(
    val resultCode: String,
    val resultMsg: String,
    val tid: String,
    val authUrl: String,
    val timestamp: Long?
)

data class InicisApprovalRequest(
    val authToken: String,
    val timestamp: Long?,
    val signature: String,
    val verification: String,
    val mid: String,
    val charset: String,
    val format: String,
)

data class InicisApprovalResponse(
    val resultCode: String,
    val resultMsg: String,
    val tid: String?,
    val mid: String?,
    val MOID: String?,
    val TotPrice: Long?,
    val goodName: String?,
    val payMethod: String,
    val applDate: String?,
    val applTime: String?,
    val EventCode: String?,
    val buyerName: String?,
    val buyerTel: String?,
    val buyerEmail: String?,
    val custEmail: String?,
    val timestamp: Long?,
    val netCancelUrl: String?
)

data class InicisCancelRequest(
    val mid: String,
    val tid: String,
    val reason: String,
    val signKey: String,
    val hashKey: String,
    val timestamp: Long?
)

data class InicisCancelResponse(
    val resultCode: String,
    val resultMsg: String,
    val tid: String,
    val cancelPrice: Long,
    val cancelTime: String,
    val timestamp: String?
)

// Inicis Refund API DTOs (v2/pg/refund)
data class InicisRefundRequest(
    val mid: String,
    val type: String,
    val timestamp: String,
    val clientIp: String,
    val hashData: String,
    val data: InicisRefundData
)

data class InicisRefundData(
    val tid: String,
    val msg: String
)

data class InicisRefundResponse(
    val resultCode: String,
    val resultMsg: String,
    val cancelDate: String?,
    val cancelTime: String?,
    val cshrCancelNum: String?,
    val detailResultCode: String?,
    val receiptInfo: String?,
    val timestamp: String?
)

data class InicisNetworkCancelResponse(
    val resultCode: String,
    val resultMsg: String,
    val timestamp: Long
)