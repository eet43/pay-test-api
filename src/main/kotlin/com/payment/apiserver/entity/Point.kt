package com.payment.apiserver.entity

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "points")
data class Point(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long = 0,
    
    @Column(nullable = false)
    val balance: Long = 0,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: LocalDateTime = LocalDateTime.now()
)