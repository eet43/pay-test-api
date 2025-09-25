package com.payment.apiserver.controller

import com.payment.apiserver.dto.LoginRequest
import com.payment.apiserver.dto.LoginResponse
import com.payment.apiserver.service.AuthService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {
    
    @PostMapping("/login")
    fun login(@RequestBody request: LoginRequest): ResponseEntity<LoginResponse> {
        return try {
            val response = authService.login(request)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.badRequest().build()
        }
    }

    @GetMapping("/user/{userId}")
    fun getUserInfo(@PathVariable userId: Long): ResponseEntity<LoginResponse> {
        return try {
            val response = authService.getUserInfo(userId)
            ResponseEntity.ok(response)
        } catch (e: IllegalArgumentException) {
            ResponseEntity.notFound().build()
        }
    }
}