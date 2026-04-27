# Soo Auth Service

Kotlin/Spring 기반 인증 서버입니다.

이 저장소는 공통 인증 기능과 Triplan MVP 백엔드 API 확장을 담당합니다.

## 목적

- 공통 인증 서버 구현
- 인증/인가 API 제공
- 관리자 프론트엔드와 연동
- Triplan MVP API 단계적 추가

## Triplan MVP API

초기 MVP는 다음 API를 우선 구현합니다.

1. POST /recommendations/destinations
2. POST /itineraries/calculate
3. POST /itineraries

## 추천 엔진 MVP

추천 API는 다음 외부 데이터 소스를 조합합니다.

- TourAPI
  - 관광지
  - 행사
  - 숙박
  - 이미지
- Kakao Local
  - 카페
  - 음식점

백엔드 추천 서비스는 다음 역할을 담당합니다.

- 후보 정규화
- 중복 제거
- 스타일 기반 점수화
- placeType 혼합 비율 제어
- source, externalId, placeType 유지

## 핵심 도메인 규칙

추천, 계산, 저장 전체 흐름에서 다음 장소 식별자를 유지해야 합니다.

- source
- externalId
- placeType

## 개발 원칙

- Controller는 요청/응답 변환과 인증 정보 추출 중심으로 얇게 유지합니다.
- 비즈니스 로직은 domain 계층에서 처리합니다.
- 저장소 접근은 storage 계층으로 격리합니다.
- 모듈 간 참조 방향을 깨지 않습니다.
- DB migration은 사람 승인 후 실행합니다.
- develop/main 브랜치 직접 push를 금지합니다.

## 실행

    ./gradlew bootRun

## 테스트

    ./gradlew test

## Lint

    ./gradlew ktlintCheck

## 환경변수

`.env.example`을 참고하세요.
실제 `.env` 파일은 커밋하지 않습니다.

## OpenClaw 작업 규칙

- 모든 변경은 PR 기반으로 진행합니다.
- DB migration 실행은 승인 필요입니다.
- 운영 설정 변경은 승인 필요입니다.
- secret, token, .env 파일 커밋은 금지입니다.

## 보안

자세한 내용은 `SECURITY.md`를 참고하세요.
