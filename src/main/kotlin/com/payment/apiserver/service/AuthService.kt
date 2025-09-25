package com.payment.apiserver.service

import com.payment.apiserver.dto.LoginRequest
import com.payment.apiserver.dto.LoginResponse
import com.payment.apiserver.repository.UserRepository
import com.payment.apiserver.repository.PointRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class AuthService(
    private val userRepository: UserRepository,
    private val pointRepository: PointRepository
) {
    
    fun login(request: LoginRequest): LoginResponse {
        val user = userRepository.findByEmail(request.email)
            ?: throw IllegalArgumentException("사용자를 찾을 수 없습니다: ${request.email}")

        val point = pointRepository.findByUserId(user.id)
        val pointBalance = point?.balance ?: 0L

        return LoginResponse(
            userId = user.id,
            email = user.email,
            name = user.name,
            pointBalance = pointBalance
        )
    }

    fun getUserInfo(userId: Long): LoginResponse {
        val user = userRepository.findById(userId).orElseThrow {
            IllegalArgumentException("사용자를 찾을 수 없습니다: $userId")
        }

        val point = pointRepository.findByUserId(user.id)
        val pointBalance = point?.balance ?: 0L

        return LoginResponse(
            userId = user.id,
            email = user.email,
            name = user.name,
            pointBalance = pointBalance
        )
    }
}