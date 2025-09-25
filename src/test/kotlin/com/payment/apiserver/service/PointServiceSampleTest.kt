package com.payment.apiserver.service

import com.payment.apiserver.entity.Point
import com.payment.apiserver.entity.User
import com.payment.apiserver.exception.PaymentException
import com.payment.apiserver.repository.PointRepository
import com.payment.apiserver.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import java.time.LocalDateTime
import java.util.Optional

@DisplayName("PointService 샘플 테스트 - test_prompt.md 가이드라인 적용")
class PointServiceSampleTest {

    private lateinit var pointService: PointService
    private lateinit var userRepository: UserRepository
    private lateinit var pointRepository: PointRepository

    private lateinit var testUser: User
    private lateinit var testPoint: Point

    @BeforeEach
    fun setUp() {
        // Given: Mock 객체 준비
        userRepository = mockk()
        pointRepository = mockk()
        pointService = PointService(userRepository, pointRepository)

        // 테스트 데이터 준비
        testUser = User(
            id = 1L,
            email = "test@example.com",
            name = "테스트 사용자",
            createdAt = LocalDateTime.now()
        )

        testPoint = Point(
            id = 1L,
            userId = 1L,
            balance = 1000L,
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )
    }

    @Test
    @DisplayName("usePoints_whenSufficientBalance_shouldDeductPoints")
    fun usePoints_whenSufficientBalance_shouldDeductPoints() {
        // Given: 충분한 포인트 잔액이 있는 사용자
        val userId = 1L
        val pointsToUse = 300L
        val expectedBalance = 700L

        every { userRepository.findById(userId) } returns Optional.of(testUser)
        every { pointRepository.findByUserId(userId) } returns testPoint
        every { pointRepository.save(any()) } returns testPoint.copy(balance = expectedBalance)

        // When: 포인트 사용 실행
        val result = pointService.usePoints(userId, pointsToUse)

        // Then: 포인트가 차감되고 올바른 잔액이 반환되어야 함
        assertEquals(expectedBalance, result)
        verify { pointRepository.save(match { it.balance == expectedBalance }) }
        verify { userRepository.findById(userId) }
        verify { pointRepository.findByUserId(userId) }
    }

    @Test
    @DisplayName("usePoints_whenInsufficientBalance_shouldThrowException")
    fun usePoints_whenInsufficientBalance_shouldThrowException() {
        // Given: 부족한 포인트 잔액을 가진 사용자
        val userId = 1L
        val pointsToUse = 1500L // 잔액(1000)보다 많은 사용 요청

        every { userRepository.findById(userId) } returns Optional.of(testUser)
        every { pointRepository.findByUserId(userId) } returns testPoint

        // When & Then: 포인트 부족 시 PaymentException이 발생해야 함
        val exception = assertThrows<PaymentException> {
            pointService.usePoints(userId, pointsToUse)
        }

        assertNotNull(exception.message)
        assertEquals("포인트 잔액이 부족합니다. 현재 잔액: 1000, 요청 금액: 1500", exception.message)

        // 포인트 차감 로직이 실행되지 않았는지 확인
        verify(exactly = 0) { pointRepository.save(any()) }
    }

    @Test
    @DisplayName("usePoints_whenUserNotFound_shouldThrowException")
    fun usePoints_whenUserNotFound_shouldThrowException() {
        // Given: 존재하지 않는 사용자 ID
        val nonExistentUserId = 999L
        val pointsToUse = 100L

        every { userRepository.findById(nonExistentUserId) } returns Optional.empty()

        // When & Then: 사용자가 존재하지 않을 때 PaymentException이 발생해야 함
        val exception = assertThrows<PaymentException> {
            pointService.usePoints(nonExistentUserId, pointsToUse)
        }

        assertEquals("사용자를 찾을 수 없습니다: $nonExistentUserId", exception.message)

        // 포인트 관련 로직이 실행되지 않았는지 확인
        verify(exactly = 0) { pointRepository.findByUserId(any()) }
        verify(exactly = 0) { pointRepository.save(any()) }
    }

    @Test
    @DisplayName("addPoints_whenValidUser_shouldIncreaseBalance")
    fun addPoints_whenValidUser_shouldIncreaseBalance() {
        // Given: 유효한 사용자와 포인트 추가 요청
        val userId = 1L
        val pointsToAdd = 500L
        val expectedBalance = 1500L

        every { userRepository.findById(userId) } returns Optional.of(testUser)
        every { pointRepository.findByUserId(userId) } returns testPoint
        every { pointRepository.save(any()) } returns testPoint.copy(balance = expectedBalance)

        // When: 포인트 추가 실행
        val result = pointService.addPoints(userId, pointsToAdd)

        // Then: 포인트가 추가되고 올바른 잔액이 반환되어야 함
        assertEquals(expectedBalance, result)
        verify { pointRepository.save(match { it.balance == expectedBalance }) }
    }

    @Test
    @DisplayName("getBalance_whenValidUser_shouldReturnCurrentBalance")
    fun getBalance_whenValidUser_shouldReturnCurrentBalance() {
        // Given: 유효한 사용자
        val userId = 1L
        val expectedBalance = 1000L

        every { userRepository.findById(userId) } returns Optional.of(testUser)
        every { pointRepository.findByUserId(userId) } returns testPoint

        // When: 포인트 잔액 조회 실행
        val actualBalance = pointService.getBalance(userId)

        // Then: 올바른 잔액이 반환되어야 함
        assertEquals(expectedBalance, actualBalance)
        verify { userRepository.findById(userId) }
        verify { pointRepository.findByUserId(userId) }
    }

    @Test
    @DisplayName("validatePointUsage_whenValidRequest_shouldNotThrowException")
    fun validatePointUsage_whenValidRequest_shouldNotThrowException() {
        // Given: 유효한 포인트 사용 검증 요청
        val userId = 1L
        val pointsToUse = 800L
        val totalAmount = 1000L
        val finalAmount = 200L

        every { userRepository.findById(userId) } returns Optional.of(testUser)
        every { pointRepository.findByUserId(userId) } returns testPoint

        // When & Then: 유효한 요청일 때 예외가 발생하지 않아야 함
        org.junit.jupiter.api.assertDoesNotThrow {
            pointService.validatePointUsage(userId, pointsToUse, totalAmount, finalAmount)
        }

        verify { userRepository.findById(userId) }
        verify { pointRepository.findByUserId(userId) }
    }
}