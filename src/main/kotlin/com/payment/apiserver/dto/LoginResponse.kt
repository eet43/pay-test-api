package com.payment.apiserver.dto

data class LoginResponse(
    val userId: Long,
    val email: String,
    val name: String,
    val pointBalance: Long,
    val message: String = "로그인 성공"
)