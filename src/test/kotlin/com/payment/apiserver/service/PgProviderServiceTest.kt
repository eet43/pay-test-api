package com.payment.apiserver.service

import com.payment.apiserver.config.PaymentProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull

@DisplayName("PgProviderService 테스트")
class PgProviderServiceTest {

    private lateinit var pgProviderService: PgProviderService
    
    private val testProviders = listOf(
        PaymentProperties.PgProvider(
            name = "PG_A",
            weight = 50,
            apiUrl = "https://api.pg-a.com",
            merchantId = "merchant_a",
            apiKey = "key_a",
            signKey = "sign_a",
            hashKey = "hash_a"
        ),
        PaymentProperties.PgProvider(
            name = "PG_B",
            weight = 30,
            apiUrl = "https://api.pg-b.com",
            merchantId = "merchant_b",
            apiKey = "key_b",
            signKey = "sign_b",
            hashKey = "hash_b"
        ),
        PaymentProperties.PgProvider(
            name = "PG_C",
            weight = 20,
            apiUrl = "https://api.pg-c.com",
            merchantId = "merchant_c",
            apiKey = "key_c",
            signKey = "sign_c",
            hashKey = "hash_c"
        )
    )

    @BeforeEach
    fun setup() {
        val paymentProperties = PaymentProperties(
            pg = PaymentProperties.PgProperties(testProviders),
            minimumAmount = 100,
            cors = PaymentProperties.CorsProperties(listOf("http://localhost:3000"))
        )
        pgProviderService = PgProviderService(paymentProperties)
    }

    @DisplayName("PG 프로바이더 선택 테스트")
    @Test
    fun `selectProvider는 가중치에 따라 PG를 선택한다`() {
        // When
        val selectedProvider = pgProviderService.selectProvider()

        // Then
        assertNotNull(selectedProvider)
        assert(testProviders.contains(selectedProvider))
    }

    @DisplayName("PG 프로바이더 이름으로 조회 성공")
    @Test
    fun `getProviderByName은 정확한 이름으로 PG를 반환한다`() {
        // When
        val provider = pgProviderService.getProviderByName("PG_A")

        // Then
        assertNotNull(provider)
        assertEquals("PG_A", provider?.name)
        assertEquals(50, provider?.weight)
        assertEquals("https://api.pg-a.com", provider?.apiUrl)
        assertEquals("merchant_a", provider?.merchantId)
        assertEquals("key_a", provider?.apiKey)
        assertEquals("sign_a", provider?.signKey)
        assertEquals("hash_a", provider?.hashKey)
    }

    @DisplayName("존재하지 않는 PG 이름으로 조회 시 null 반환")
    @Test
    fun `getProviderByName은 존재하지 않는 이름으로 조회 시 null을 반환한다`() {
        // When
        val provider = pgProviderService.getProviderByName("NONEXISTENT_PG")

        // Then
        assertNull(provider)
    }

    @DisplayName("모든 PG 프로바이더 조회")
    @Test
    fun `getAllProviders는 모든 PG 목록을 반환한다`() {
        // When
        val allProviders = pgProviderService.getAllProviders()

        // Then
        assertEquals(3, allProviders.size)
        assertEquals(testProviders, allProviders)
    }

    @DisplayName("가중치 0인 경우 첫 번째 PG 선택")
    @Test
    fun `모든 PG의 가중치가 0이면 첫 번째 PG를 선택한다`() {
        // Given
        val zeroWeightProviders = listOf(
            PaymentProperties.PgProvider(
                name = "PG_ZERO_1",
                weight = 0,
                apiUrl = "https://api.pg-zero-1.com",
                merchantId = "merchant_zero_1",
                apiKey = "key_zero_1",
                signKey = "sign_zero_1",
                hashKey = "hash_zero_1"
            ),
            PaymentProperties.PgProvider(
                name = "PG_ZERO_2",
                weight = 0,
                apiUrl = "https://api.pg-zero-2.com",
                merchantId = "merchant_zero_2",
                apiKey = "key_zero_2",
                signKey = "sign_zero_2",
                hashKey = "hash_zero_2"
            )
        )

        val paymentProperties = PaymentProperties(
            pg = PaymentProperties.PgProperties(zeroWeightProviders),
            minimumAmount = 100,
            cors = PaymentProperties.CorsProperties(listOf("http://localhost:3000"))
        )
        val service = PgProviderService(paymentProperties)

        // When
        val selectedProvider = service.selectProvider()

        // Then
        assertEquals("PG_ZERO_1", selectedProvider.name)
    }
}