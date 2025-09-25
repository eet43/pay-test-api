package com.payment.apiserver.service

import com.payment.apiserver.entity.Point
import com.payment.apiserver.exception.PaymentException
import com.payment.apiserver.repository.PointRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
@Transactional
class PointService(
    private val pointRepository: PointRepository
) {
    
    fun getPointBalance(userId: Long): Long {
        val point = pointRepository.findByUserId(userId)
        return point?.balance ?: 0L
    }
    
    fun validatePointUsage(userId: Long, pointsToUse: Long, totalAmount: Long, finalAmount: Long): Boolean {
        if (pointsToUse <= 0) return true
        
        if (pointsToUse > totalAmount) {
            throw PaymentException("포인트 사용량이 총 결제금액을 초과할 수 없습니다")
        }
        
        if (totalAmount - pointsToUse != finalAmount) {
            throw PaymentException("총 결제금액에서 포인트 사용량을 뺀 값이 최종 결제금액과 일치하지 않습니다")
        }
        
        val currentBalance = getPointBalance(userId)
        if (currentBalance < pointsToUse) {
            throw PaymentException("포인트 잔액이 부족합니다. (잔액: $currentBalance, 필요: $pointsToUse)")
        }
        
        return true
    }
    
    fun usePoints(userId: Long, amount: Long): Point {
        val point = pointRepository.findByUserId(userId)
            ?: throw PaymentException("포인트 정보를 찾을 수 없습니다")
            
        if (point.balance < amount) {
            throw PaymentException("포인트 잔액이 부족합니다. (잔액: ${point.balance}, 필요: $amount)")
        }
        
        val updatedPoint = point.copy(
            balance = point.balance - amount,
            updatedAt = LocalDateTime.now()
        )
        
        return pointRepository.save(updatedPoint)
    }
    
    fun refundPoints(userId: Long, amount: Long): Point {
        val point = pointRepository.findByUserId(userId)
            ?: throw PaymentException("포인트 정보를 찾을 수 없습니다")
            
        val updatedPoint = point.copy(
            balance = point.balance + amount,
            updatedAt = LocalDateTime.now()
        )
        
        return pointRepository.save(updatedPoint)
    }
    
    fun createPoint(userId: Long, initialBalance: Long = 0L): Point {
        val point = Point(
            userId = userId,
            balance = initialBalance,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
        
        return pointRepository.save(point)
    }
}