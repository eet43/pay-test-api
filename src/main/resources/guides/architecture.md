# Payment API Server - 아키텍처 및 구조 가이드

## 프로젝트 개요
Spring Boot + Kotlin 기반의 결제 API 서버로, 다중 PG(Payment Gateway) 지원 및 가중치 기반 로드밸런싱을 제공합니다.

## 기술 스택
- **언어**: Kotlin 1.9.21
- **프레임워크**: Spring Boot 3.2.1
- **JVM**: Java 17
- **데이터베이스**: H2 (개발), 운영환경별 설정
- **HTTP Client**: Spring WebFlux
- **ORM**: Spring Data JPA
- **테스트**: JUnit 5, MockK

## 프로젝트 구조

```
src/main/kotlin/com/payment/apiserver/
├── PaymentApiServerApplication.kt           # 메인 애플리케이션
├── client/                                  # 외부 API 클라이언트
│   ├── InicisPaymentClient.kt              # 이니시스 PG 클라이언트
│   └── TossPaymentClient.kt                # 토스 PG 클라이언트
├── config/                                  # 설정 클래스
│   ├── PaymentProperties.kt                # 결제 관련 설정 프로퍼티
│   ├── WebClientConfig.kt                  # HTTP 클라이언트 설정
│   └── WebConfig.kt                        # 웹 관련 설정 (CORS 등)
├── context/                                 # 컨텍스트 관리
│   └── PaymentContext.kt                   # 결제 컨텍스트
├── controller/                              # REST API 컨트롤러
│   ├── AuthController.kt                   # 인증 관련 API
│   ├── HealthController.kt                 # 헬스체크 API
│   ├── OrderController.kt                  # 주문 관련 API
│   └── PaymentController.kt                # 결제 관련 API
├── dto/                                     # 데이터 전송 객체
│   ├── *Request.kt                         # 요청 DTO들
│   └── *Response.kt                        # 응답 DTO들
├── entity/                                  # JPA 엔티티
│   ├── User.kt                            # 사용자 엔티티
│   ├── Order.kt                           # 주문 엔티티
│   ├── Payment.kt                         # 결제 엔티티
│   ├── PaymentRequest.kt                  # 결제 요청 엔티티
│   └── Point.kt                           # 포인트 엔티티
├── exception/                               # 예외 처리
│   └── PaymentException.kt                # 결제 관련 예외
├── repository/                              # 데이터 접근 계층
│   ├── UserRepository.kt                  # 사용자 레포지토리
│   ├── OrderRepository.kt                 # 주문 레포지토리
│   ├── PaymentRepository.kt               # 결제 레포지토리
│   ├── PaymentRequestRepository.kt        # 결제 요청 레포지토리
│   └── PointRepository.kt                 # 포인트 레포지토리
├── service/                                 # 비즈니스 로직
│   ├── AuthService.kt                     # 인증 서비스
│   ├── OrderService.kt                    # 주문 서비스
│   ├── PaymentService.kt                  # 결제 서비스
│   ├── PaymentTransaction.kt              # 결제 트랜잭션 관리
│   ├── HybridPaymentTransaction.kt        # 하이브리드 결제 트랜잭션
│   ├── PgProviderService.kt               # PG 제공자 서비스
│   └── PointService.kt                    # 포인트 서비스
├── strategy/                                # 전략 패턴 구현
│   ├── PaymentStrategy.kt                 # 결제 전략 인터페이스
│   ├── PgPaymentStrategy.kt               # PG 결제 전략 추상클래스
│   ├── InicisPaymentStrategy.kt           # 이니시스 결제 전략
│   ├── TossPaymentStrategy.kt             # 토스 결제 전략
│   └── HybridPaymentStrategy.kt           # 하이브리드 결제 전략
└── util/                                    # 유틸리티 클래스
```

## 핵심 아키텍처 패턴

### 1. Strategy Pattern (전략 패턴)
PG별로 다른 결제 로직을 처리하기 위해 전략 패턴을 사용합니다.
- `PaymentStrategy`: 결제 전략 인터페이스
- `PgPaymentStrategy`: PG 결제 전략 추상클래스
- 각 PG별 구체적인 전략 구현체

### 2. Multi-Environment Configuration
환경별(local, dev, stg, cbo, prod) 설정을 지원합니다.
- 각 환경별로 다른 PG 설정 및 가중치
- 환경 변수를 통한 민감정보 관리

### 3. Layered Architecture
표준적인 Spring Boot 레이어드 아키텍처를 따릅니다:
- **Controller**: HTTP 요청 처리
- **Service**: 비즈니스 로직
- **Repository**: 데이터 접근
- **Entity**: 도메인 모델

## 주요 기능 모듈

### 결제 처리 Flow
1. **인증** (`AuthController`): 사용자 인증 처리
2. **결제 준비** (`PaymentController`): 결제 요청 생성 및 PG 선택
3. **결제 승인** (`PaymentStrategy`): PG별 결제 승인 처리
4. **결제 완료**: 결제 결과 저장 및 응답

### PG 가중치 시스템
- `PgProviderService`: PG 선택 로직
- 설정된 가중치에 따른 랜덤 선택
- PG별 장애 시 자동 failover

### 데이터 모델
- **User**: 사용자 정보
- **Order**: 주문 정보
- **Payment**: 결제 정보
- **PaymentRequest**: 결제 요청 로그
- **Point**: 포인트/적립금 정보

## 환경별 설정

### Local (개발환경)
- H2 인메모리 데이터베이스
- 이니시스/토스 테스트 환경 사용
- 디버그 로그 활성화

### Dev/Stg/CBO/Prod
- 환경변수를 통한 PG 설정
- 실제 PG API 연동
- 로그 레벨 조정

## 보안 고려사항
- PG API 키, 해시키 등 민감정보는 환경변수 관리
- CORS 설정으로 허용된 도메인만 접근 가능
- 결제 금액 검증 (최소 100원)

## 확장 가능성
- 새로운 PG 추가 시 Strategy 패턴으로 쉽게 확장
- 가중치 기반 로드밸런싱으로 PG별 트래픽 조절
- 환경별 독립적인 설정 관리

## 테스트 구조
- 단위 테스트: Service, Strategy 계층
- 통합 테스트: Controller 계층
- MockK를 활용한 외부 API 모킹