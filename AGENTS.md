# AGENTS.md - soo-auth-service

## 목적

공통 인증 서버를 Kotlin/Spring 기반으로 개발하고, Triplan MVP API를 단계적으로 추가한다.

## 작업 원칙

1. Controller는 얇게 유지한다.
2. 비즈니스 로직은 domain 계층에서 처리한다.
3. 저장소 접근은 storage 계층으로 격리한다.
4. 공통 응답/에러 타입은 기존 support 모듈을 우선 확인한다.
5. 모듈 간 참조 방향을 깨지 않는다.
6. develop 또는 main 브랜치에 직접 push하지 않는다.
7. migration, 배포, 운영 설정 변경은 승인 필요하다.
8. secret, token, .env 파일을 커밋하지 않는다.

## Triplan MVP API

1. POST /recommendations/destinations
2. POST /itineraries/calculate
3. POST /itineraries

## 외부 연동

- TourAPI
- Kakao Local
- Route Provider
- Weather Provider

## 검증

./gradlew ktlintCheck
./gradlew test
./gradlew :core:core-auth-common:compileKotlin
