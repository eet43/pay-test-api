package com.payment.apiserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PaymentApiServerApplication

fun main(args: Array<String>) {
    runApplication<PaymentApiServerApplication>(*args)
}