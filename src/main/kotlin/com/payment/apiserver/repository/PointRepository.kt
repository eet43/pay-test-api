package com.payment.apiserver.repository

import com.payment.apiserver.entity.Point
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PointRepository : JpaRepository<Point, Long> {
    fun findByUserId(userId: Long): Point?
}