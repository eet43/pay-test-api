package com.payment.apiserver.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.bind.ConstructorBinding

@ConfigurationProperties("payment")
data class PaymentProperties @ConstructorBinding constructor(
    val pg: PgProperties,
    val minimumAmount: Long,
    val cors: CorsProperties
) {
    data class PgProperties(
        val providers: List<PgProvider>
    )

    data class PgProvider(
        val name: String,
        val weight: Int,
        val apiUrl: String,
        val merchantId: String,
        val apiKey: String,
        val signKey: String,
        val hashKey: String
    )

    data class CorsProperties(
        val allowedOrigins: List<String>
    )
}