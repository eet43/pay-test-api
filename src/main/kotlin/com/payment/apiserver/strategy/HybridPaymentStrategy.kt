//package com.payment.apiserver.strategy
//
//import com.payment.apiserver.context.PaymentContext
//import com.payment.apiserver.dto.*
//import com.payment.apiserver.exception.PaymentException
//import com.payment.apiserver.service.*
//import org.springframework.stereotype.Component
//import org.springframework.transaction.annotation.Transactional
//import java.time.LocalDateTime
//import java.time.format.DateTimeFormatter
//import java.util.*
//import java.util.concurrent.ConcurrentHashMap
//
//@Component
//@Transactional
//class HybridPaymentStrategy(
//    private val pointService: PointService,
////    private val pointPaymentStrategy: PointPaymentStrategy,
//    private val inicisPaymentStrategy: InicisPaymentStrategy,
////    private val tossPaymentStrategy: TossPaymentStrategy
//) : PaymentStrategy {
//
//    private val hybridPaymentStorage = ConcurrentHashMap<String, HybridPaymentTransaction>()
//    private val orderToHybridMapping = ConcurrentHashMap<String, String>()
//
//    override fun getPaymentType(): PaymentType = PaymentType.HYBRID
//
//    fun authenticateHybridPayment(request: HybridPaymentAuthRequest): HybridPaymentAuthResponse {
//        val userId = extractUserIdFromEmail(request.buyerEmail)
//
//        try {
//            // 1. 포인트 잔액 확인
//            val pointBalance = pointService.getPointBalance(userId)
//            if (pointBalance < request.pointAmount) {
//                throw PaymentException("포인트 잔액이 부족합니다. (잔액: $pointBalance, 필요: ${request.pointAmount})")
//            }
//
//            // 2. 하이브리드 결제 ID 생성
//            val hybridPaymentId = generateHybridPaymentId()
//
//            // 3. 포인트 결제 먼저 진행
//            var pointPaymentId: String? = null
//            if (request.pointAmount > 0) {
//                val pointAuthRequest = PaymentAuthRequest(
//                    orderId = "${request.orderId}_POINT",
//                    amount = request.pointAmount,
//                    productName = "${request.productName} (포인트 결제)",
//                    buyerName = request.buyerName,
//                    buyerEmail = request.buyerEmail,
//                    buyerTel = request.buyerTel,
//                    returnUrl = request.returnUrl,
//                    closeUrl = request.closeUrl
//                )
//
//                val pointAuthResponse = pointPaymentStrategy.authenticatePayment(pointAuthRequest)
//                if (!pointAuthResponse.success) {
//                    throw PaymentException("포인트 결제 인증 실패: ${pointAuthResponse.message}")
//                }
//                pointPaymentId = pointAuthResponse.data?.paymentId
//            }
//
//            // 4. PG 결제 준비
//            var pgPaymentId: String? = null
//            var pgPaymentUrl: String? = null
//            var pgProvider: String? = null
//
//            if (request.pgAmount > 0) {
//                val pgAuthRequest = PaymentAuthRequest(
//                    orderId = "${request.orderId}_PG",
//                    amount = request.pgAmount,
//                    productName = "${request.productName} (PG 결제)",
//                    buyerName = request.buyerName,
//                    buyerEmail = request.buyerEmail,
//                    buyerTel = request.buyerTel,
//                    returnUrl = request.returnUrl,
//                    closeUrl = request.closeUrl
//                )
//
//                // 가중치 기반으로 PG 선택 (임시로 이니시스 사용)
//                val pgStrategy = inicisPaymentStrategy // 실제로는 가중치 기반 선택 로직 필요
//                val pgAuthResponse = pgStrategy.authenticatePayment(pgAuthRequest)
//
//                if (!pgAuthResponse.success) {
//                    throw PaymentException("PG 결제 인증 실패: ${pgAuthResponse.message}")
//                }
//
//                pgPaymentId = pgAuthResponse.data?.paymentId
//                pgPaymentUrl = pgAuthResponse.data?.paymentUrl
//                pgProvider = pgAuthResponse.data?.pgProvider
//            }
//
//            // 5. 하이브리드 트랜잭션 생성
//            val hybridTransaction = HybridPaymentTransaction(
//                hybridPaymentId = hybridPaymentId,
//                orderId = request.orderId,
//                totalAmount = request.totalAmount,
//                pointAmount = request.pointAmount,
//                pgAmount = request.pgAmount,
//                pointPaymentId = pointPaymentId,
//                pgPaymentId = pgPaymentId,
//                pgProvider = pgProvider,
//                status = if (request.pointAmount > 0) HybridPaymentStatus.PENDING else HybridPaymentStatus.POINT_COMPLETED,
//                createdAt = LocalDateTime.now()
//            )
//
//            hybridPaymentStorage[hybridPaymentId] = hybridTransaction
//            orderToHybridMapping[request.orderId] = hybridPaymentId
//
//            return HybridPaymentAuthResponse(
//                success = true,
//                message = "하이브리드 결제 인증이 완료되었습니다",
//                data = HybridPaymentAuthData(
//                    hybridPaymentId = hybridPaymentId,
//                    orderId = request.orderId,
//                    totalAmount = request.totalAmount,
//                    pointAmount = request.pointAmount,
//                    pgAmount = request.pgAmount,
//                    pointPaymentId = pointPaymentId,
//                    pgPaymentId = pgPaymentId,
//                    pgPaymentUrl = pgPaymentUrl,
//                    status = hybridTransaction.status,
//                    timestamp = getCurrentTimestamp()
//                )
//            )
//
//        } catch (e: PaymentException) {
//            throw e
//        } catch (e: Exception) {
//            throw PaymentException("하이브리드 결제 인증 중 시스템 오류가 발생했습니다: ${e.message}")
//        }
//    }
//
////    fun approveHybridPayment(hybridPaymentId: String): PaymentApprovalResponse {
////        val hybridTransaction = hybridPaymentStorage[hybridPaymentId]
////            ?: throw PaymentException("하이브리드 결제 정보를 찾을 수 없습니다")
////
////        try {
////            var updatedTransaction = hybridTransaction
////
////            // 1. 포인트 결제 승인
////            if (hybridTransaction.pointAmount > 0 && hybridTransaction.pointPaymentId != null) {
////                val pointApprovalResponse = pointPaymentStrategy.approvePayment(hybridTransaction.pointPaymentId)
////                if (!pointApprovalResponse.success) {
////                    throw PaymentException("포인트 결제 승인 실패: ${pointApprovalResponse.message}")
////                }
////
////                updatedTransaction = updatedTransaction.copy(
////                    status = HybridPaymentStatus.POINT_COMPLETED,
////                    pointCompletedAt = LocalDateTime.now()
////                )
////            }
////
////            // 2. PG 결제 승인
////            if (hybridTransaction.pgAmount > 0 && hybridTransaction.pgPaymentId != null) {
////                val pgStrategy = when (hybridTransaction.pgProvider) {
////                    "inicis" -> inicisPaymentStrategy
////                    "toss" -> tossPaymentStrategy
////                    else -> inicisPaymentStrategy
////                }
////
////                val pgApprovalResponse = pgStrategy.approvePayment(hybridTransaction.pgPaymentId)
////                if (!pgApprovalResponse.success) {
////                    // PG 결제 실패 시 포인트 결제 롤백
////                    if (hybridTransaction.pointPaymentId != null) {
////                        try {
////                            pointPaymentStrategy.cancelPayment(
////                                hybridTransaction.pointPaymentId,
////                                "PG 결제 실패로 인한 포인트 결제 롤백",
////                                CancelType.NETWORK
////                            )
////                        } catch (e: Exception) {
////                            println("포인트 결제 롤백 실패: ${e.message}")
////                        }
////                    }
////                    throw PaymentException("PG 결제 승인 실패: ${pgApprovalResponse.message}")
////                }
////
////                updatedTransaction = updatedTransaction.copy(
////                    status = HybridPaymentStatus.PG_COMPLETED,
////                    pgCompletedAt = LocalDateTime.now()
////                )
////            } else if (hybridTransaction.pgAmount == 0L) {
////                // PG 결제 금액이 0인 경우 (전액 포인트)
////                updatedTransaction = updatedTransaction.copy(
////                    status = HybridPaymentStatus.PG_COMPLETED
////                )
////            }
////
////            hybridPaymentStorage[hybridPaymentId] = updatedTransaction
////
////            return PaymentApprovalResponse(
////                success = true,
////                message = "하이브리드 결제 승인이 완료되었습니다",
////                data = PaymentApprovalData(
////                    tid = hybridPaymentId,
////                    orderId = hybridTransaction.orderId,
////                    amount = hybridTransaction.totalAmount,
////                    pgProvider = "HYBRID",
////                    paymentMethod = "HYBRID_POINT_PG",
////                    approvedAt = getCurrentTimestamp(),
////                    status = PaymentStatus.APPROVED
////                )
////            )
////
////        } catch (e: PaymentException) {
////            throw e
////        } catch (e: Exception) {
////            throw PaymentException("하이브리드 결제 승인 중 시스템 오류가 발생했습니다: ${e.message}")
////        }
////    }
//
//    fun cancelHybridPayment(hybridPaymentId: String, reason: String): PaymentCancelResponse {
//        val hybridTransaction = hybridPaymentStorage[hybridPaymentId]
//            ?: throw PaymentException("하이브리드 결제 정보를 찾을 수 없습니다")
//
//        try {
//            var totalCancelledAmount = 0L
//
//            // 1. PG 결제 취소
//            if (hybridTransaction.pgPaymentId != null && hybridTransaction.pgCompletedAt != null) {
//                val pgStrategy = when (hybridTransaction.pgProvider) {
//                    "inicis" -> inicisPaymentStrategy
//                    "toss" -> tossPaymentStrategy
//                    else -> inicisPaymentStrategy
//                }
//
//                val pgCancelResponse = pgStrategy.cancelPayment(
//                    hybridTransaction.pgPaymentId,
//                    reason,
//                    CancelType.MANUAL
//                )
//
//                if (pgCancelResponse.success) {
//                    totalCancelledAmount += hybridTransaction.pgAmount
//                }
//            }
//
//            // 2. 포인트 결제 취소 (환불)
//            if (hybridTransaction.pointPaymentId != null && hybridTransaction.pointCompletedAt != null) {
//                val pointCancelResponse = pointPaymentStrategy.cancelPayment(
//                    hybridTransaction.pointPaymentId,
//                    reason,
//                    CancelType.MANUAL
//                )
//
//                if (pointCancelResponse.success) {
//                    totalCancelledAmount += hybridTransaction.pointAmount
//                }
//            }
//
//            val updatedTransaction = hybridTransaction.copy(
//                status = HybridPaymentStatus.CANCELLED,
//                cancelledAt = LocalDateTime.now()
//            )
//            hybridPaymentStorage[hybridPaymentId] = updatedTransaction
//
//            return PaymentCancelResponse(
//                success = true,
//                message = "하이브리드 결제 취소가 완료되었습니다",
//                data = PaymentCancelData(
//                    tid = hybridPaymentId,
//                    orderId = hybridTransaction.orderId,
//                    cancelledAmount = totalCancelledAmount,
//                    cancelledAt = getCurrentTimestamp(),
//                    reason = reason,
//                    cancelType = CancelType.MANUAL
//                )
//            )
//
//        } catch (e: PaymentException) {
//            throw e
//        } catch (e: Exception) {
//            throw PaymentException("하이브리드 결제 취소 중 시스템 오류가 발생했습니다: ${e.message}")
//        }
//    }
//
//    // PaymentStrategy 인터페이스 구현 (기본 동작)
//    override fun authenticatePayment(request: PaymentAuthRequest): PaymentAuthResponse {
//        throw PaymentException("하이브리드 결제는 authenticateHybridPayment 메서드를 사용해주세요")
//    }
//
//    override fun approvePayment(paymentId: String): PaymentApprovalResponse {
//        TODO("Not yet implemented")
//    }
//
////    override fun approvePayment(paymentId: String): PaymentApprovalResponse {
////        return approveHybridPayment(paymentId)
////    }
//
//    override fun cancelPayment(paymentId: String, reason: String, cancelType: CancelType): PaymentCancelResponse {
//        return cancelHybridPayment(paymentId, reason)
//    }
//
//    override fun preparePayment(request: PaymentPrepareRequest): PaymentPrepareResponse {
//        TODO("Not yet implemented")
//    }
//
////    override fun preparePayment(request: PaymentPrepareRequest): PaymentPrepareResponse {
////        return prepareHybridPayment(request)
////    }
//
////    fun prepareHybridPayment(request: PaymentPrepareRequest): PaymentPrepareResponse {
////        val userId = extractUserIdFromEmail(request.buyerEmail)
////
////        try {
////            // 1. 포인트 사용량 및 가격 정보 검증
////            if (request.usePoints) {
////                pointService.validatePointUsage(
////                    userId = userId,
////                    pointsToUse = request.pointsToUse,
////                    totalAmount = request.amount,
////                    finalAmount = request.finalPaymentAmount
////                )
////            }
////
////            // 2. 승인요청 정보 저장 (임시 저장소 사용)
////            val hybridPaymentId = generateHybridPaymentId()
////            val hybridTransaction = HybridPaymentTransaction(
////                hybridPaymentId = hybridPaymentId,
////                orderId = request.orderId,
////                totalAmount = request.amount,
////                pointAmount = request.pointsToUse,
////                pgAmount = request.finalPaymentAmount,
////                pointPaymentId = null,
////                pgPaymentId = null,
////                pgProvider = null,
////                status = HybridPaymentStatus.PREPARED,
////                createdAt = LocalDateTime.now()
////            )
////
////            hybridPaymentStorage[hybridPaymentId] = hybridTransaction
////            orderToHybridMapping[request.orderId] = hybridPaymentId
////
////            // PG 결제 부분이 있다면 해당 PG의 prepare 데이터도 생성
////            val pgPaymentData = if (request.finalPaymentAmount > 0) {
////                // 실제로는 가중치 기반으로 PG 선택해야 함
////                val pgStrategy = inicisPaymentStrategy as PgPaymentStrategy
////                pgStrategy.createPrepareData(
////                    request.copy(finalPaymentAmount = request.finalPaymentAmount),
////                    "${hybridPaymentId}_PG"
////                )
////            } else null
////
////            return PaymentPrepareResponse(
////                success = true,
////                message = "하이브리드 결제 준비가 완료되었습니다",
////                data = HybridPaymentPrepareData(
////                    paymentId = hybridPaymentId,
////                    orderId = request.orderId,
////                    amount = request.amount,
////                    pointsUsed = request.pointsToUse,
////                    finalPaymentAmount = request.finalPaymentAmount,
////                    pgProvider = "HYBRID",
////                    timestamp = getCurrentTimestamp(),
////                    status = PaymentStatus.PREPARED,
////                    pgPaymentData = pgPaymentData
////                )
////            )
////
////        } catch (e: PaymentException) {
////            throw e
////        } catch (e: Exception) {
////            throw PaymentException("하이브리드 결제 준비 중 시스템 오류가 발생했습니다: ${e.message}")
////        }
////    }
//
//    override fun handlePaymentReturn(request: PaymentReturnRequest): PaymentReturnResponse {
//        throw PaymentException("하이브리드 결제는 별도의 return 처리가 필요합니다")
//    }
//
//    private fun extractUserIdFromEmail(email: String): Long {
//        return email.substringBefore("@").toLongOrNull()
//            ?: throw PaymentException("하이브리드 결제를 위한 유효한 사용자 정보가 필요합니다")
//    }
//
//    private fun generateHybridPaymentId(): String {
//        return "HYBRID_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"
//    }
//
//    private fun getCurrentTimestamp(): String {
//        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
//    }
//}