package com.payment.apiserver.dto

data class PaymentReturnRequest(
    val resultCode: String?,
    val resultMsg: String?,
    val mid: String?,
    val oid: String?,
    val price: String?,
    val authToken: String?,
    val authUrl: String?,
    val netCancelUrl: String?,
    val timestamp: String?,
    val signature: String?
)