package com.payment.apiserver.dto

import com.payment.apiserver.service.PaymentStatus
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

data class PaymentPrepareResponse(
    val success: Boolean,
    val message: String,
    val data: PaymentPrepareData?
)

@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "pgType"
)
@JsonSubTypes(
    JsonSubTypes.Type(value = InicisPaymentPrepareData::class, name = "inicis"),
    JsonSubTypes.Type(value = TossPaymentPrepareData::class, name = "toss"),
    JsonSubTypes.Type(value = HybridPaymentPrepareData::class, name = "hybrid")
)
abstract class PaymentPrepareData {
    abstract val paymentId: String
    abstract val oid: String
}

// 이니시스 결제 prepare 데이터 - FO에서 이니시스 API 호출에 필요한 정보
data class InicisPaymentPrepareData(
    override val paymentId: String,
    override val oid: String,
    // 이니시스 인증 API 호출에 필요한 정보들
    val mid: String,
    val price: String,
    val goodname: String,
    val buyername: String,
    val buyeremail: String,
    val buyertel: String,
    val returnUrl: String,
    val closeUrl: String,
    val timestamp: Long,
    val signature: String,
    val verification: String,
    val mKey: String,
    val version: String = "1.0"
) : PaymentPrepareData()

// 토스페이먼츠 결제 prepare 데이터 - FO에서 토스 API 호출에 필요한 정보
data class TossPaymentPrepareData(
    override val paymentId: String,
    override val oid: String,
    // 토스페이먼츠 ready API 호출에 필요한 정보들
    val mId: String,
    val version: String = "1.0",
    val orderName: String,
    val customerName: String,
    val customerEmail: String,
    val customerMobilePhone: String,
    val successUrl: String,
    val failUrl: String,
    val apiKey: String
) : PaymentPrepareData()

// 하이브리드 결제 prepare 데이터 - 포인트 + PG 결제 정보
data class HybridPaymentPrepareData(
    override val paymentId: String,
    override val oid: String,
    val pgPaymentData: PaymentPrepareData? = null // PG 부분에 대한 결제 데이터
) : PaymentPrepareData()