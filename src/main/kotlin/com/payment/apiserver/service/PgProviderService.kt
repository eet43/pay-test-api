package com.payment.apiserver.service

import com.payment.apiserver.config.PaymentProperties
import org.springframework.stereotype.Service
import kotlin.random.Random

@Service
class PgProviderService(
    private val paymentProperties: PaymentProperties
) {

    fun selectProvider(): PaymentProperties.PgProvider {
        val providers = paymentProperties.pg.providers
        val totalWeight = providers.sumOf { it.weight }
        
        if (totalWeight == 0) {
            return providers.first()
        }
        
        val random = Random.nextInt(1, totalWeight + 1)
        var currentWeight = 0
        
        for (provider in providers) {
            currentWeight += provider.weight
            if (random <= currentWeight) {
                return provider
            }
        }
        
        return providers.first()
    }

    fun getProviderByName(name: String): PaymentProperties.PgProvider? {
        return paymentProperties.pg.providers.find { it.name == name }
    }

    fun getAllProviders(): List<PaymentProperties.PgProvider> {
        return paymentProperties.pg.providers
    }
}